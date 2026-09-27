# ADR 0008: `UserId` is the identity; the e-mail address is an attribute

- Status: Accepted
- Date: 2026-09-27
- Scope: identity across the estate — microservice-security, the three content services (memes,
  comments, user-collections), the offboarding saga and the `account-closure` vocabulary

## Context

Everything a member owned used to be keyed by the address their token carried: `memes.author`,
`comments.author`, `collection_items.user_email`, the vote tables, and the closure commands the
offboarding saga sent. An address is mutable, and no content service had a foreign key to anything —
so a rename had to be replayed into every service by hand. That is what `EMAIL_CHANGED` and the
rekey machinery were: security announced the new address, each service re-keyed its own rows, and
the estate hoped the two topics did not overtake each other.

The failure modes were not hypothetical (P18, and the 2026-09-26 analysis
`docs/analysis/2026-09-26-user-id-as-the-key.md`):

- a member who renamed became a stranger to their own uploads — `own:false`, `DELETE` 403 — until
  the rekey arrived, and permanently if it was lost;
- their closure marked nothing while confirming an erasure, so the saga reported success over
  content that was still public;
- whoever registered the freed address next inherited what was left under it;
- a deletion could overtake a rename, and nothing in the design ordered the two topics.

Every one of those is the same defect: a key that changes. `users.id` — a UUID, assigned at
registration, immutable — is the one thing about a member that does not.

## Decision

1. **Identity is `UserId`** (`shared/user-id`, the UUID from security's `users.id`). Every row that
   belongs to a member is keyed by it: `memes.author_id`, `comments.author_id`,
   `collection_items.user_id`, and the vote tables by the voter's id.
2. **The e-mail address is an attribute**, never a key. It stays on content rows as the display
   value the placeholder "deleted account" is written into; it is not what a row is looked up by. A
   build-time guard per service (`RetiredAddressKeyTest`) fails the build on any SQL that matches
   content by address again — an ADR alone enforces nothing (0001, 0006).
3. **The token carries both, separately**: `sub` = the UUID, `email` = its own claim. No `uid` claim
   was added — a second home for the id is a second thing to disagree with `sub`.
4. **Display names are read, not replicated.** `AuthorDirectory` (`shared/author-directory`) asks
   security's `GET /users?ids=` at read time — masked addresses, anonymous, throttled, cached
   briefly. There is no nickname: the name IS the masked address. A directory outage degrades a
   listing's names, it does not hide the content.
5. **Anonymisation clears the id.** `reassignAuthor` writes the placeholder into the address column
   AND `author_id = NULL`, so content kept after a closure cannot be grouped back together by the
   id of the account that is gone. The id columns are therefore nullable in memes and comments;
   `collection_items.user_id` is `NOT NULL`, because a saved item of nobody has nothing to show.
6. **The closure speaks in ids.** `ClosureCommand` v2 carries `userId` and no address; a fact
   without one is refused rather than guessed at, and confirmations answer by id. The saga's own row
   still carries the leaver's address, because the farewell mail and the security verdict need
   something to write to.

### Alternatives rejected

- **A `uid` claim beside `sub`** — two places to hold one identity, and a migration that ends with
  both in the codebase anyway.
- **A projection / CDC feed of profiles into each service** (analysis §9 stages 2 and 4, the
  `USER_PROFILE_CHANGED` fact and a per-service `author_directory` table) — it replaces one
  replication problem with a bigger one: a second copy of identity in five databases, kept fresh by
  a topic that can still be lost or overtaken. Reading names at request time has a cost the cache
  pays; staleness has a cost only the member notices.
- **A read-only DB view over security's `users`** — it couples five services to one schema and only
  works while everything shares a database, which the estate deliberately does not.
- **Keeping the address as a secondary key "just for the saga"** — that is the defect, written down
  as a feature.

## Consequences

- A rename touches no content row in any service: there is nothing to re-key. `EMAIL_CHANGED`, the
  twelve rekey files and the consumers that listened for them are deleted, and security no longer
  announces address changes to anybody.
- Names now cost a call. A listing fans out one `GET /users?ids=` per page, cached for 60 s; when
  security is down the page still renders and the log says the names are unavailable.
- Security's own stores stay keyed by address on purpose — MFA factors, recovery codes, federated
  links and sessions all live inside the service that owns the address, and their law is written in
  `AddressKeyedStoresTest` (ADR 0006's shape). This ADR is about identity as the ESTATE sees it.
- Tokens minted before the cutover carry an address as `sub`; `offline-jwt` keeps the fallback until
  they expire, and a token without an id is nobody to the id-keyed filters.
- The address is still personal data on content rows. Whether it should be dropped from
  `memes.author` / `comments.author` altogether is open, and is the one box the cutover plan left
  unticked (`docs/plans/user-id-cutover.md`, 3e).
