# The estate's behavior, in its own words

Every `.feature` across the three workspaces — collected 2026-07-14 by
`build_features.py`. Titles and scenario names only: the steps live with
their repos, this page is the spec surface you can diff in one glance.

## shared

### config

**Hardcoded configuration source** — `shared/config/src/test/resources/com/jrobertgardzinski/config/source/hardcoded/hardcoded-config.feature`

> A HardcodedKey carries its value directly in the key definition. No external dependencies are needed to resolve it — the value is always available at compile time.

- Resolve a hardcoded scalar value
- Resolve a hardcoded list value

**Repository configuration source** — `shared/config/src/test/resources/com/jrobertgardzinski/config/source/repository/repository-config.feature`

> A RepositoryKey resolves its value via RepositoryConfigPort, which abstracts database access. The key name is used as the identifier for the lookup. Values may be absent — the port returns Optional for scalar keys.

- Resolve a scalar value from the repository
- Resolve a list value from the repository
- Resolve a scalar value that is absent from the repository

**Properties configuration source** — `shared/config/src/test/resources/com/jrobertgardzinski/config/source/properties/properties-config.feature`

> A PropertiesKey resolves its value via PropertiesConfigPort, which abstracts application.properties, environment variables, or any other property source. The key name maps directly to the property name.

- Resolve a scalar value from properties
- Resolve a list value from properties

### microservice-email

**Sending mail on behalf of trusted services** — `shared/microservice-email/src/test/resources/features/send-mail.feature`

> The mail service is internal: only callers presenting the shared API key may send a MAIL, and a malformed MAIL command is refused before anything is dispatched.

- a trusted service sends a plain mail
- a caller without the API key is refused
- a malformed command is refused
- a verification link is rendered into the template
- a password-reset link is rendered into the template

### microservice-security

**Authentication** — `shared/microservice-security/specs/authenticate.feature`

> Repeated failed AUTHENTICATION attempts from the same source temporarily block that source, to protect accounts from password guessing.

- 1. Correct CREDENTIALS AUTHENTICATE the USER
- 
- 2. Wrong CREDENTIALS are rejected
- <case>
- 3. Too many failed attempts block the source
- reaching the failure limit blocks the source
- staying under the limit keeps the source open
- 4. A blocked source is rejected even with the correct CREDENTIALS
- 
- 5. Failed attempts stop counting after 15 minutes
- within 15 minutes the earlier failures still count
- after 15 minutes the earlier failures are forgiven
- 6. A block is temporary and expires after a while
- 
- 7. Correct CREDENTIALS are not enough while the EMAIL is unverified
- 

**Authorizing access with an access token** — `shared/microservice-security/specs/authorize.feature`

> A protected resource requires a valid, unexpired ACCESS TOKEN. The token is obtained by AUTHENTICATING and is presented as a Bearer token.

- 1. A valid ACCESS TOKEN grants access
- 
- 2. A missing or unknown ACCESS TOKEN is refused
- <case>
- 3. An expired ACCESS TOKEN is refused
- 

**Changing the email address** — `shared/microservice-security/specs/change-email.feature`

> A signed-in USER changes their EMAIL by proving ownership of the new one: a verification link goes to the new EMAIL, and confirming the token applies the change. Afterwards they AUTHENTICATE under the new EMAIL and no longer under the old one. An unknown token is rejected.

- Confirming the token from the link CHANGES the EMAIL
- 
- An unknown token is rejected
- 
- A taken EMAIL cannot be probed through the change — the reply is quiet, the owner is told by mail
- 
- FEDERATED LINKS follow the account — the subject is the person, not the address
-  *(@http-only)*

**Changing the password** — `shared/microservice-security/specs/change-password.feature`

> A signed-in USER changes their password by proving the current one. Afterwards they AUTHENTICATE with the new password; the old one no longer works. A wrong current password is rejected.

- The correct current password lets the USER CHANGE it
- 
- A wrong current password is rejected
- 

**Closing the account** — `shared/microservice-security/specs/delete-account.feature`

> A signed-in USER closes their account. Closure is a SAGA across services: the account locks at once (sessions revoked, sign-in refused) and identity announces the deletion to the PORTAL, whose own orchestrator (microservice-offboarding) has every content service purge the USER's content — each axis under the USER's chosen rule (delete / anonymise / keep-popular); votes are retracted. Identity waits for the portal's single outcome: only "content purged" deletes the USER for good; a failed purge — or silence past the safety net — rolls the closure back.

- Requesting closure locks the account immediately
- 
- The USER chooses what happens to their content
-  *(@http-only)*
- The closure completes only when the PORTAL confirmed its content purged
-  *(@http-only)*
- A failed portal purge rolls the closure back
-  *(@http-only)*
- Even total silence rolls the closure back (the safety net)
-  *(@http-only)*

**Federated sign-in** — `shared/microservice-security/specs/federated-sign-in.feature`

> A USER SIGNS IN with an identity an external PROVIDER vouches for. Registration collapses into the first sign-in, and one ACCOUNT may hold many identities — a password and a provider subject are equal keys to the same ACCOUNT.

- 1. A vouched identity SIGNS the USER IN, creating the ACCOUNT at first contact *(@http-only)*
- first contact
- the second contact opens the same ACCOUNT, not a twin
- 2. A verified local ACCOUNT auto-links: the same inbox proved twice is the same person
- forgot the password sign-up, came back through the provider
- 3. An unverified local ACCOUNT is taken over: the PROVIDER's proof beats an unproven password
- a squatter planted an account on someone else's address
- 3b. A provider login is only link #1 — enrolled factors must still be passed
- a federated account that turned on a second factor
- 4. An identity the PROVIDER does not vouch for touches nothing
- the provider did not verify the email

**Listing active sessions** — `shared/microservice-security/specs/list-sessions.feature`

> A signed-in USER sees their active sessions. Each time they AUTHENTICATE a new session is created, and every active one shows up in the list.

- Every active session of the USER is listed
- 

**Logging out** — `shared/microservice-security/specs/logout.feature`

> LOGGING OUT ends the current session: its REFRESH TOKEN can no longer be REFRESHED and its access token no longer authorizes.

- 1. A LOGGED-OUT session can no longer be REFRESHED
- 
- 2. A LOGGED-OUT ACCESS TOKEN no longer grants access
- 
- 3. LOGGING OUT without a session still succeeds
- 

**Passkey sign-in** — `shared/microservice-security/specs/mfa-passkey.feature`

> A USER may enrol a PASSKEY — a possession FACTOR, nothing to type and nothing to copy. Once enrolled, the password alone no longer signs in: the device holding the PASSKEY must prove it is present. This exercises the same factor chain the e-mail and TOTP factors use, proving the factor port is genuinely plug-and-play. (The protocol behind a passkey is an implementation detail and lives in the glue — the argon2 rule.)

- An enrolled PASSKEY signs the USER in without a typed code
- 

**Multi-factor sign-in** — `shared/microservice-security/specs/mfa.feature`

> A USER may enrol extra FACTORS. Once enrolled, a correct password is no longer enough: the sign-in pauses and each FACTOR must be passed, in order, before a session is issued. The e-mail FACTOR sends a one-time CODE that must be quoted back. A RECOVERY CODE — GENERATED ahead of time, shown once — stands in for any FACTOR the USER lost access to; each one works exactly once.

- With a FACTOR enrolled, the password alone does not sign in
- 
- Passing the FACTOR completes the sign-in
- 
- A wrong CODE does not sign in
- 
- A RECOVERY CODE stands in for a FACTOR the USER cannot pass
- 
- each RECOVERY CODE works exactly once

**Refreshing a session** — `shared/microservice-security/specs/refresh-session.feature`

> A USER keeps their session alive by REFRESHING it. A session that has expired, or that no longer exists, cannot be REFRESHED — the USER must AUTHENTICATE again.

- 1. An active session can be REFRESHED
- 
- 2. An expired session cannot be REFRESHED
- 
- 3. A missing session cannot be REFRESHED
- 

**Registration** — `shared/microservice-security/specs/register.feature`

> A new USER REGISTERS with an EMAIL and a password. Registration is refused when the EMAIL or the password is invalid. An already-taken EMAIL is refused quietly: the answer does not reveal that the account exists — the owner of the address learns what happened from a mail only they can read.

- 1. A valid EMAIL and password REGISTER the USER *(@ui)*
- 
- 2. An invalid EMAIL or password is rejected, saying which one
- <case>
- 3. An EMAIL that is already REGISTERED is refused quietly, so nobody can probe who has an account
- the same email
- a provider alias of that email (Gmail treats dots and "+tags" as the same inbox)

**Resetting a forgotten password** — `shared/microservice-security/specs/reset-password.feature`

> A USER who forgot their password requests a reset link, then sets a new password with the RESET TOKEN from the link. Afterwards they AUTHENTICATE with the new password; the old one no longer works. An unknown token is rejected.

- A valid RESET TOKEN sets a new password
- 
- An unknown RESET TOKEN is rejected
- 

**Refresh token theft detection** — `shared/microservice-security/specs/reuse-detection.feature`

> REFRESH tokens are single-use: each REFRESH rotates to a new one. Presenting a REFRESH TOKEN that has already been rotated away signals theft, so the whole session lineage is revoked — including the attacker's freshly obtained token.

- A replayed (already-rotated) REFRESH TOKEN revokes the whole SESSION FAMILY
- 

**Revoking all sessions** — `shared/microservice-security/specs/revoke-all-sessions.feature`

> A USER can log out everywhere at once: revoking all sessions ends every session the USER holds, so afterwards no ACCESS TOKEN authorizes and no REFRESH TOKEN can be REFRESHED.

- REVOKING all sessions ends every session of the USER
- 

**Verifying an email address** — `shared/microservice-security/specs/verify-email.feature`

> A USER proves they own their EMAIL by following a verification link sent to it. The link carries a single-use VERIFICATION TOKEN; the matching token marks the EMAIL as verified, and an unknown token is rejected.

- The VERIFICATION TOKEN from the link verifies the EMAIL
- 
- An unknown VERIFICATION TOKEN is rejected
- 
- Registration automatically starts VERIFICATION
- 

**Roles** — `shared/microservice-security/security-infrastructure/src/test/resources/roles.feature`

> Every signed-in USER holds the USER role; an ADMIN may grant MODERATOR or ADMIN on top, and a protected who-am-I resource reports the caller's roles so other services can gate on them.

- a fresh USER is only a USER
- an ADMIN GRANTS a ROLE and it shows up
- a non-admin cannot GRANT ROLES
- GRANTING ROLES to an unknown USER is refused

## portal

### microservice-comments

**Comment threads under memes** — `portal/microservice-comments/src/test/resources/features/comment-thread.feature`

> Signed-in users discuss under an existing meme; the COMMENT's author comes from the security service, not from the request body. Votes on a COMMENT are one-per-user toggles, and the listing carries each COMMENT's score. When a meme is deleted, its whole thread of COMMENTs disappears with it.

- a signed-in user comments and the thread lists it
- an anonymous comment is refused
- a comment under a meme that does not exist is refused
- comment votes are one-per-user and the listing carries the score
- a long thread is read one page at a time
- an essay is refused; a remark is accepted
- the author deletes their own COMMENT; a stranger cannot
- a moderator deletes someone else's COMMENT
- a deleted meme takes its whole thread with it
- a moderator hides a COMMENT; readers see a tombstone, the author still sees their words
- only a moderator may hide a COMMENT

### microservice-memes

**Favourites — the gallery integrated with user-collections** — `portal/microservice-memes/memes-ui/e2e/features/favourites.feature`

> A signed-in visitor stars memes; the refs live in microservice-user-collections (opaque ids, saved cross-origin straight from the browser) and the gallery hydrates them back into tiles. A ref outlives its meme by design — the favourites wall then shows an unavailable keepsake.

- A starred meme lands on the favourites wall
- Unstarring lets the favourite go
- A favourite outliving its meme shows as an unavailable keepsake

**The meme gallery in a real browser** — `portal/microservice-memes/memes-ui/e2e/features/gallery.feature`

> The gallery is public to browse; uploading, voting and commenting need a signed-in identity from microservice-security. These scenarios drive the React UI with Playwright against real memes + comments + security services (in-memory stores).

- An anonymous visitor browses the gallery
- Signing in through the panel
- An upload appears on the wall
- A vote is a toggle
- A comment lands in the thread

**The purge-policy default is an administrator's dial** — `portal/microservice-memes/memes-infrastructure/src/test/resources/features/admin-purge-policy.feature`

> What happens to a leaver's memes is deployment policy — but policy changes faster than deployments. An ADMIN may override the env default at runtime; the leaver's own wizard choice still wins over everything. Everyone else is refused at the door.

- an admin overrides the deployment default and the purge obeys it
- a plain user may not touch the dial
- clearing the override restores the deployment default

**Moderating memes** — `portal/microservice-memes/memes-infrastructure/src/test/resources/features/moderate-meme.feature`

> A MEME belongs to its uploader, who may delete it; a MODERATOR (a role microservice-security reports for the caller) may delete anyone's MEME. Everyone else is refused.

- a stranger cannot delete someone else's meme
- the author deletes their own meme
- a moderator deletes someone else's meme
- deleting without signing in is refused
- a moderator flags a meme NSFW and the gallery carries the flag
- the safe-for-work judgement is a moderator's alone

**Tagging memes and finding them by tag** — `portal/microservice-memes/memes-infrastructure/src/test/resources/features/tag-meme.feature`

> The uploader curates their MEME's TAGs — a small, legal set of search keys, not free text. Anyone browses the gallery narrowed to one TAG; the TAGs of a purged MEME vanish with it.

- the author tags their meme and the gallery finds it by tag
- only the author curates the tags
- keyword spam is refused

**Uploading a meme** — `portal/microservice-memes/memes-infrastructure/src/test/resources/features/upload-meme.feature`

> Signed-in users upload images; a MEME is stored optimised for the browser. Browsing is public: anyone can fetch a MEME, its thumbnail, or the gallery without signing in.

- a signed-in user uploads, anyone fetches it back
- fetch a meme's thumbnail
- the gallery lists the meme publicly
- an anonymous upload is refused

**Voting on memes** — `portal/microservice-memes/memes-infrastructure/src/test/resources/features/vote-meme.feature`

> Signed-in users vote on a MEME; each user has ONE vote per MEME, worked as a toggle: repeating the same vote retracts it, the opposite direction switches it. An up-voted MEME becomes a higher-scoring RANKED MEME in the public hot list.

- the meme with more distinct up-voters ranks higher
- repeating the same vote retracts it
- an anonymous vote is refused

### microservice-offboarding

**Offboarding — the portal cleans up after a leaving account** — `portal/microservice-offboarding/src/test/resources/features/offboarding.feature`

> The portal's process manager for account deletion, extracted from the identity service so identity stays reusable. Security announces the FACT that an account requested deletion; this service commands every configured content participant to purge, collects their confirmations, and announces the single outcome security waits for: the portal's content is purged, or the purge failed because someone never answered. Commands are idempotent BY DEFAULT (workspace ADR 0006 — enforced by the generic IdempotentCommandsTest, not restated per scenario); the scenarios below pin the message choreography.

- A deletion request commands the content purge
- The leaver's policy choices ride the command untouched
- An early confirmation announces nothing yet
- The last confirmation announces the portal purged
- A confirmation for nobody's saga is a stray, not an error
- Silence past the deadline announces the failure
- With no content participants the portal is instantly clean

### microservice-user-collections

**A user's collections of saved references** — `portal/microservice-user-collections/src/test/resources/features/collections.feature`

> A user saves opaque references — a meme, a comment — into named collections. The service keeps the refs and nothing else; it never interprets what they point at. Every command is idempotent BY DEFAULT (workspace ADR 0006 — enforced by the generic IdempotentCommandsTest, not restated per scenario); the scenarios below pin the REPLY contracts a caller can lean on. When the account is deleted every collection goes with it.

- Saving a reference puts it in the collection
- Saving twice tells the caller it was already there
- A collection lists its refs newest first
- Collections are per user and per name
- Removing a reference takes it out
- Removing something not saved tells the caller it was not there
- Deleting the account purges every collection *(@saga)*

## formula

### formula-simulator

**Car development under a rulebook** — `formula/formula-simulator/src/test/resources/features/car-development.feature`

> R&D explores the design space and costs real budget; homologation freezes the car for the season; an era change reshapes what a team knows — portable know-how is clamped into the new legal window, era-bound branches reset. The rulebook, not the richest wallet, draws the boundaries.

- a research programme charges the budget it spends
- a homologated car is frozen until the rules change
- an era change clamps the car into the new legal window

**The driver market** — `formula/formula-simulator/src/test/resources/features/driver-market.feature`

> Free agents ask a salary their talent justifies; contracts bind; poaching a contracted driver means paying the buyout — to the team that loses them. Money moves, drivers move, nobody teleports.

- a fair offer signs the free agent and empties the market
- poaching a contracted driver pays the buyout to the old team

**A race weekend** — `formula/formula-simulator/src/test/resources/features/race-weekend.feature`

> The manager registers a driver line-up, the backend runs a race weekend (qualifying orders the grid, the start reshuffles it, the race is streamed lap by lap as state), and the championship counts the points. State, never pixels.

- the team races and the championship counts the result
- a race needs a grid

### microservice-paddock

**The paddock — servers, people and what's coming up** — `formula/microservice-paddock/src/test/resources/features/paddock.feature`

> A racer signs in once and finds the servers they play on, who is on each, and the events and live signals around them. Identity is the security service's; game state stays on the servers.

- a private server is the organizer's circle
- an open server is self-service and counts its people
- my paddock carries each campaign's live state
- the organizer plans an event and members answer
- a private server can have more than one admin adding friends
- planning an event notifies the members
- the feed merges planned events with derived signals

**The workshop — the community's garage** — `formula/microservice-paddock/src/test/resources/features/workshop.feature`

> Every mod surface of the game is a data file: a bot script, a track profile, an era rulebook, a scenario. The workshop takes them under the author's signed-in identity, versions them, and hands them to anyone who wants to run them — JA2 and Gothic live because their players could do this.

- an author publishes a track and the community finds it
- only the author ships a new version
- a rulebook that is not JSON is turned away at the door

**The paddock PWA in a real browser** — `formula/microservice-paddock/e2e/features/paddock.feature`

> The social hub around the game: browsing servers is public, everything else — registering a server, joining an open one, planning an event, answering an RSVP — needs the one identity shared with the game and the gallery. Playwright drives the PWA against a real security (test environment) and a real paddock on in-memory H2.

- An anonymous visitor sees an empty registry and no controls
- Signing in through the dialog
- Registering a server puts the organizer on its roster
- Joining an open server from the registry
- An organizer plans an event and a member answers the call

*191 scenarios in total.*
