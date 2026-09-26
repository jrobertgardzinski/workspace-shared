# UserId as the key — content authored by a stable identity, in either assembly

- Date: 2026-09-26
- Status: analysis, nothing decided (decisions in §8 are the owner's)
- Constraint (owner, verbatim): *"Robimy mechanizm jeszcze zanim zdecydujemy o architekturze"* — domain and application identical in monolith and microservices; only adapters differ. Precedent: `Source`/`Destination` instead of topics (`portal/microservice-offboarding/offboarding-application/.../Source.java:15`, `Destination.java:12`, mapped to topics only in `offboarding-infrastructure/.../SagaTopics.java:61-62`) and the one-process runner (`portal/specs/README.md:9,15-24`, `portal/account-closure-specs/pom.xml:54-120`).

Paths below are relative to `/home/robert/Documents/git/`. A claim without an anchor is marked *(guess)*.

## 0. Verification of the gathered facts

| # | Claim | Verdict | Evidence |
|---|---|---|---|
| 1 | `users.id UUID PRIMARY KEY` exists | true | `shared/microservice-security/security-infrastructure/src/main/resources/db/migration/V1__init.sql:2` |
| 2 | security "already has the key" | **true on paper, false in use** | `User.java:22,43` mints the UUID; `UserRepository.java:23-90` has no `findById` — every method takes `Email`; no migration references `users.id` (grep `user_id\|REFERENCES users` over `db/migration/*.sql` = 0 hits); `sessions.email` (V1:25), `federated_identities.user_email` (V10:8), `enrolled_factors.user_email` (V11:8), `email_changes.current_email` (V4:5). Rename keeps the id: `UserJdbcRepository.java:35` `UPDATE users SET email=… WHERE email=…` |
| 3 | closure keyed by e-mail in security | true | `security-domain/.../vo/AccountClosure.java:26` `AccountClosure(Email target, …)`; `AccountDeletionOrchestrator.java:124` puts `"email"`, `:135` uses the e-mail as the **outbox partition key** |
| 4 | JWT `sub` = e-mail | true | `JwtAccessTokenMint.java:94` `claims.put("sub", email.value())`; asserted by `JwtAccessTokenHttpTest.java:79`; verifier reads it at `shared/offline-jwt/.../OfflineJwtVerifier.java:134` → `VerifiedToken.java:12` `(String subject, Set<String> roles, boolean mfaCompliant)` |
| 5 | who reads `sub` / the caller's e-mail | wider than the portal | offline: memes `JwtSecurityAuthenticationGate.java:47`, comments `JwtSecurityAuthenticationGate.java`, collections `JwtSecurityGate.java:34`, **paddock** `formula/microservice-paddock/.../infra/Identity.java:52`; introspection (the **default**, `HttpSecurityAuthenticationGate.java:27` `matchIfMissing = true`) reads `/me` → `"email"` (`:61,65`; `MeController.java:48-53`); the filters expose it as `AUTHENTICATED_USER` (memes `RequireSignInFilter.java:64`, comments `:49`); security's own `AuthorizationFilter.java:51` / `Caller.java:23`; UI `memes-ui/src/App.tsx:212` and `DeleteAccountDialog.tsx:83` → `DELETE /account/{email}` (`DeleteAccountController.java:81`) |
| 6 | `account-closure` library shape | true | `ClosureMessages.java:57-72` (`Field.EMAIL:60`), `ClosureCommand.java:26,30-31` (`isAddressed` = e-mail non-blank), `ClosureConfirmation.java:29,53`, `ClosureConfirmations.java:7` `confirm(sagaId, leaver, reserved)`; contract test `ClosureParticipantContractTest.java:15-77` |
| 7 | participants only implement; tests in the runner | true | `portal/account-closure-specs/src/test/java/.../{memes,comments,collections}/*` + `PortalInOneProcess.java:58-89`; `HeapMemes.java:28` `extends FakeMemeErasure implements MemeRepository` |
| 8 | erasure ports keyed by e-mail | true | `MemeErasure.java:25,32` `activeOf/pendingOf(String author)`, `CommentErasure.java:26,33`, `ItemErasure.java:25,32,54` (`user`); `MemeRepository.java:88` `reassignAuthor`; `PurgeUserContent.java:78` and `PurgeUserComments.java:57` write `DeletedAccount.AUTHOR` (`memes-domain/.../DeletedAccount.java:6`, `comments-domain/.../DeletedAccount.java:6`) into the same column |
| 9 | identity lives in more columns than `author` | **missed** | memes `V1__memes.sql:6` `author`, `:25` `meme_votes.voter`, `settings.updated_by` (rekeyed at `JdbcUserContentRekey.java:56`); comments `V1__comments.sql:6` `author`, `:15` `comment_votes.voter`; collections `V1__collection_items.sql:6` `user_email` (part of `uq_collection_item`, `:11`). Purge already touches voters: `PurgeUserContent.java:75` `purgeVoter(author)` |
| 10 | rekey file count ~41 | 38 code/pact files + 6 `.md` = 44 | `rg --no-ignore -l "Rekey\|EmailChanged\|EMAIL_CHANGED" portal shared` minus `target/`; classified in §5 |
| 11 | `PurgeReservedNothing` exists because rename and "nothing held" are indistinguishable | true, and the code says so | `memes-domain/.../Observation.java:45-57` (F-014), recorded at `MemesClosureParticipant.java:96`, `CommentsClosureParticipant.java:96`, `CollectionsClosureParticipant.java:84`. The estate also *locks* against it: `ConfirmEmailChange.java:98` refuses a rename while a deletion is pending; `V22` one running saga per address |
| 12 | `Email` VO shared; no `UserId`/`Nickname` type | true | `shared/email/email-domain/.../Email.java`; `rg "UserId\|Nickname\|nick"` over main code of portal+shared = 0 |
| 13 | **nickname exists** | **false** | no column (`users` = id, email, normalized_email, password_hash, V1:1-6), no type, no UI field. What the reader sees today is the e-mail **masked per response**: `MemeController.java:257-263` `maskAuthor` (`a***@domain`), `CommentController.java:193-203`; `"deleted account"` passes through unmasked (`:255`). "Nickname changes" is a future problem; today's problem is that the display name IS the key |
| 14 | memes has `idx_memes_author`, no id column | true; same in the other two | memes `V10:57`; comments `V1:11` `idx_comments_author`; collections `V1:14` `idx_collection_items_user` |
| 15 | token claims are pact-guarded | **false** | `shared/offline-jwt/pacts/offline-jwt-microservice-security.json` covers only the JWKS fetch; the claim shape is guarded by `JwtAccessTokenHttpTest.java:79` alone |

Verdict on the instinct: **right.** Every workaround listed in rows 3, 8, 9, 11 exists because the key is mutable. One correction of scope: the estate has no nickname, so the first deliverable is not "UserId instead of nickname" but "UserId instead of e-mail, with the display name computed elsewhere than the content row".

## 1. The key

**Type.** `record UserId(UUID value)` with `parse(String)`/`toString()`; pure Java, no framework. It is needed by security-domain, three content domains, `account-closure` and `offline-jwt`, so it cannot live in any of them: a new one-record shared module beside `email` (`shared/pom.xml:49`) — name is the owner's (§8 D2).

**Minted** in `User.java:43` already. Security must start *using* it: `UserRepository.findBy(UserId)` and `User.id()` surfaced in `/me` and in the token. Re-keying security's own tables (sessions, factors, federated identities) is **not** required for this mechanism and should stay out of scope.

**Token — two strategies.**

| | A. change `sub` to the UUID | B. keep `sub` = e-mail, add claim `uid` (recommended) |
|---|---|---|
| ADR 0004 (`shared/docs/adr/0004…md`, "within a version, additive only") | breaking — `sub` is read as an address | additive |
| breaks today | paddock stores the subject as `memberships.member` (`formula/microservice-paddock/.../V1__paddock.sql:15`) and **mails it** (`Notifications.java:70,79`); `DELETE /account/{email}` from the UI; `/me` `"email"` consumers; every `AUTHENTICATED_USER` parameter typed as an address | nothing; `VerifiedToken` gains `Optional<UserId> userId()`; older tokens (no claim) verify as before |
| dual period | needed anyway (tokens in flight up to `exp`) | none for verification; content services fall back to e-mail until §6 cutover |
| introspection path | `/me` must return `"id"` too | same: `/me` adds `"id"` (`MeController.java:48`) — this is the **default** path (`HttpSecurityAuthenticationGate.java:27`), so the claim alone is not enough |

**Saga.** `Field.USER_ID = "userId"` beside `EMAIL` (`ClosureMessages.java:60`), additive within version 1. `ClosureCommand` gains `userId`; `isAddressed()` becomes "userId present, or (dual period only) e-mail present" — after cutover, userId only, and `email` leaves the command at version 2. `ClosureConfirmation.fields()` (`:53`) echoes whichever key the command carried. Messages in flight: a participant that receives `email` only resolves rows by the `author` column, `userId` only by `author_id` (both columns exist throughout §6); the orchestrator's saga row keeps `email` (`offboarding V1:13`) and the running-saga uniqueness (`V2:38`, security `V22`) moves to the id. Partition key of the fact (`AccountDeletionOrchestrator.java:135`) becomes the id, so a rename and a deletion of the same person are ordered.

**`AccountClosure`.** `AccountClosure(UserId target, …)`; the HTTP edge (`DeleteAccountController.java:81`) may keep accepting the address for a while and resolve it to the id — an adapter concern, exactly the owner's rule.

## 2. Author as a type

| Shape | What it means | Verdict |
|---|---|---|
| `Author = UserId` on `MemeMetadata` (`:32`), `Comment` (`:22`), `SavedItem` (`:21`); names resolved at read time via a port | the row carries only the key; rename = nothing to do in content; "who am I" (`own`, `MemeController.java:244`) is an id comparison | **recommended** |
| sealed `Author = Email \| Nickname` | encodes today's accident (the name is the key) as a type; still mutable; two rekeys instead of one | reject |
| denormalised snapshot (git-style `author_id + name_at_post_time`) | historically honest, no read-time lookup; but a renamed/anonymised person stays visible under their old address forever — that is the F-014 defect made permanent, and a GDPR problem | reject for names; acceptable only for the **audit** column `settings.updated_by` (`JdbcUserContentRekey.java:56`), which should stop being rekeyed and become a snapshot |

So e-mail and nickname are **attributes of the person**, owned by security, never stored on content. Two consequences: (i) `maskAuthor` leaves the controllers and becomes security's job (it is the display name, `a***@domain` today, a nickname tomorrow — content services never learn which); (ii) `DeletedAccount.AUTHOR` stops being a value in the author column and becomes a *rendering* of "no author" (§4).

## 3. `AuthorDirectory` — the port that keeps it architecture-neutral

```java
public interface AuthorDirectory {                       // application layer, every content service
    Map<UserId, AuthorName> namesOf(Collection<UserId> ids);   // batch, one call per listing
}
public record AuthorName(String display) {}              // never the e-mail
```
Unknown id → **absent from the map**; the caller renders `AuthorName.DELETED` ("deleted account"). Consistency: the port promises nothing stronger than "what identity last told me" — the monolith adapter happens to be immediate, the domain must not rely on it. Errors: the adapter never throws into a listing; it returns what it has and records an `Observation` (the estate's pattern, `memes-domain/.../Observation.java`).

**Four adapters of the same port.**

| Adapter | Where it lives | Feeds on | Availability on the read path | PII in content DB | Consistency | Backfill / cold start |
|---|---|---|---|---|---|---|
| `HeapAuthorDirectory` | `account-closure-specs` (runner) | `put(id, name)` from steps | n/a | n/a | immediate | n/a — proves the mechanism like `HeapMemes` (`HeapMemes.java:28`) |
| **P. local projection table** `author_directory(id, display_name, updated_at, deleted_at)` | each `*-infrastructure` | `USER_PROFILE_CHANGED` facts (below) | none (own DB) | display name only (owner's choice, D4); e-mail never leaves security | eventual; LWW on `updated_at` | replay from security (§6); until warm, unknown ids render as deleted — mitigated by a tombstone/`warm` marker (§7) |
| **S. sync batch call** `GET security/authors?ids=` | each `*-infrastructure` | HTTP per listing (+ cache) | every listing depends on security being up — new coupling for **reads**; today only writes do (`RequireSignInFilter.java:74-76` → 503) | none | immediate (modulo cache) | none |
| **V. read-only VIEW in security's DB** (owner's "nieszablonowo") | each `*-infrastructure`, second `DataSource` | `SELECT … FROM author_directory WHERE id = ANY(?)` | every listing depends on security's **database** | none | immediate | none |
| **M. monolith join** | the single assembly | `JOIN users` in one query (Hibernate or JDBC) | n/a | n/a | immediate | n/a |

**V, weighed seriously.**

- (a) *Contract.* A VIEW `author_directory(id, display_name)` created by security's Flyway plus a `SELECT`-only role; the base table stays private. Security's migration freedom is then the same as with any published API: it can reshape `users` at will as long as the view is re-created in the same migration (Postgres needs `DROP/CREATE VIEW` when columns change — `memes V12` already does exactly this dance for `active_memes`). The one real loss: security can no longer take its DB offline or move it (host, credentials, major version) without co-ordinating three consumers' `DataSource`s — today only its HTTP URL is shared (`portal/docker-compose.yml:61`, `k8s/base/configmap-common.yaml:13`).
- (b) *Topology today.* Five separate Postgres containers: security in compose project `security` on host port 5433 (`shared/docker-compose.identity.yml:370-374`), memes 5434, comments 5435, collections 5436, offboarding 5437 (`portal/docker-compose.yml:176-183, 240-247, 315-322, 368-375`); the portal compose reuses the project name `security` (`:24`) but addresses security through the host because it is *swappable* (`:59-61`). Options: a second JDBC `DataSource` per service pointing at `host.docker.internal:5433` (Spring in memes/comments; plain JDBC in collections `Main.java:296`), or `postgres_fdw` in each content DB (needs the extension, a foreign server and a user mapping per DB — three more secrets to rotate), or a read replica (the honest production form; overkill for this estate). In k8s the security DB is in the same base (`portal/k8s/base/security-postgres.yaml`), so `security-postgres:5432` is reachable; a NetworkPolicy would then have to *allow* what the split was meant to forbid.
- (c) *Availability.* Every gallery/thread/favourites page needs names, so security's DB down = content service degraded. Honest fallback: render `AuthorName.UNAVAILABLE` (not "deleted") behind a short timeout, count it, keep serving content. That is workable, but it is a coupling the estate does not have today and the monolith decision would inherit it invisibly.
- (d) *PII.* Genuinely simpler: no copy of any name in content DBs, art. 17 needs no new participant duty, "deleted account" is automatic once the `users` row is gone. This is V's strongest point. It does **not** outweigh (a)–(c) *if* P stores only the display name (D4): then P's PII footprint is a masked handle or a nickname per id, the erasure duty is one row per service (§4), and P has no shared-database coupling.
- (e) *Consistency.* Immediate, no events, no replay, no cold start. True, and the reason V is the right adapter **for the monolith assembly** (it is M with a different `DataSource`).
- (f) *Mechanism unchanged.* Confirmed: V is one more implementation of `AuthorDirectory`; domain, application, the runner and M are untouched. Choosing V later costs one class per service.

**3c. Securing the view on Kubernetes — layer by layer, against what exists in `portal/k8s/`.** Baseline today: every service, security included, connects as the **superuser `postgres`** (`k8s/base/security.yaml:54-55`, `memes.yaml:48-49`; compose `shared/docker-compose.identity.yml:80`), and *one* Secret `portal-db` with *one* `POSTGRES_PASSWORD` is mounted into every app and every Postgres (`security.yaml:59-60`, `memes.yaml:53-54`, `user-collections.yaml:69-70`, generated at `k8s/overlays/dev/kustomization.yaml:28-31` with the literal `secret`). No `NetworkPolicy`, no TLS, no pooler, no replica, no Postgres exporter exists in `k8s/` (grep over `k8s/**` = 0; `k8s/README.md:154-165` lists observability as "deliberately missing"). So option V is not "grant a SELECT"; it is the first time the estate has to do database security at all.

| Layer | What must exist | Exists today | Notes / cost |
|---|---|---|---|
| (1) database | in **security's Flyway** (an artefact, e.g. `V32__author_directory_view.sql`): `CREATE VIEW author_directory AS SELECT id, <display expr> FROM users`; `CREATE ROLE memes_reader LOGIN NOINHERIT`; `…comments_reader`, `…collections_reader`; `REVOKE ALL ON SCHEMA public FROM PUBLIC`; `GRANT USAGE ON SCHEMA public TO <r>`; `GRANT SELECT ON author_directory TO <r>` (nothing on `users`); `ALTER ROLE <r> SET default_transaction_read_only = on, statement_timeout = '2s', idle_in_transaction_session_timeout = '5s'`; `CONNECTION LIMIT 5` per role | nothing; security's Flyway runs as superuser so it *can* create roles | roles are cluster-wide: the migration must be idempotent (`DO $$ IF NOT EXISTS …`) and the password cannot live in a migration — create the role `PASSWORD NULL` and set it from the Secret by an init step (`ALTER ROLE … PASSWORD` from a Job or the security pod's entrypoint), or use `pg_hba` `scram` with a rotated password via the same Job. `security_barrier` on the view is irrelevant (no RLS), but `WITH (security_invoker=false)` default is what makes the view readable without table rights — keep it |
| (2) credentials | one Secret per consumer (`memes-db-reader`, `comments-db-reader`, `collections-db-reader`), referenced only by that Deployment's `secretKeyRef`; rotation = new password in the Secret + `ALTER ROLE` Job + rollout; security's own `portal-db` password **never** reaches a content pod — it is the superuser of `users`, `sessions`, `password_hash` (V1:5) | one shared `portal-db` for everything | today a content pod's compromise already yields security's superuser password; V would make that *by design* unless the split is done first. `k8s/README.md:81`/`overlays/dev/kustomization.yaml:9-12` already say SealedSecrets/ExternalSecrets for prod — the reader Secrets follow that path |
| (3) network | `NetworkPolicy` on `app=security-postgres`: ingress 5432 only from pods `app in (security, memes, comments, user-collections)`; egress policies on the three content pods allowing `security-postgres:5432` (plus their own Postgres, Kafka, security HTTP, MinIO/image-encoder for memes); default-deny in namespace `portal` (`kustomization.yaml:9`) | none — the namespace is flat | k3s enforces `NetworkPolicy` with its **embedded kube-router controller** even though flannel is the CNI (k3s default; disabled only by `--disable-network-policy`) — *from k3s documentation, not verified in this repo*; k3d (`k8s/README.md:1`) runs k3s so the same holds. Verify once with a `kubectl exec … nc -zv security-postgres 5432` from a pod that is not allowed; if it connects, the drill is: switch CNI (Calico/Cilium) or accept that (3) is not enforced and rely on (1)+(2) — say so in `k8s/README.md`. Egress policies also block DNS unless `kube-system` UDP/TCP 53 is allowed explicitly |
| (4) transport | either `sslmode=verify-full` with a cert issued by cert-manager (HOSTING-K3S.md:7 already plans cert-manager for the ingress) mounted into `security-postgres` (`ssl=on`, `ssl_cert_file`, `ssl_key_file`) and its CA into the three consumers; or **plaintext inside the cluster, stated in the manifest** | plaintext everywhere, including each service to its own Postgres | recommend plaintext + (3) for the dev overlay and `verify-full` in the hosting overlay; the honest sentence in the README is worth more than a half-configured `sslmode=require` (which does not verify the server) |
| (5) pooler / replica | a `pgbouncer` Deployment in front of `security-postgres` with `pool_mode=transaction`, only the three reader roles in its `userlist`, `max_client_conn` capped; or a streaming replica (`hot_standby`) that consumers read and the primary never sees | none; single-replica Postgres on a 1Gi local-path PVC (`security-postgres.yaml:5-13`) | on HOSTING-K3S.md's small-node budget (~7-8 GB RAM total, `:5`) a replica doubles the identity DB's footprint for three tiny reads; pgbouncer costs ~10 MiB and gives the connection cap. Neither removes the coupling — a replica's lag reintroduces "eventual" without the projection's local autonomy |
| (6) observability | `postgres_exporter` sidecar on `security-postgres` (or `pg_stat_statements` + a scrape) exporting `pg_stat_activity` **by role** (`usename=memes_reader` count, max `state_change` age), connections per role, `pg_stat_database_blks_read`; alert rules beside `ErasureBacklogStuck` (`shared/observability/alert-rules.yml:46`): `ReaderRoleQueryTooLong`, `ReaderConnectionStorm` (connections ≥ `CONNECTION LIMIT`) | `prometheus.yml:30-33` scrapes security's HTTP metrics only; no Postgres exporter anywhere; no observability overlay in k8s at all (`k8s/README.md:154`) | without this, a content service's misbehaviour surfaces only as security's sign-in latency — the coupling is invisible. Security's own JVM metrics do not see the reader roles at all, since the readers bypass the service |
| (7) failure drill | consumer side: the second `DataSource` is **not** part of readiness (own DB is); on `28P01` (bad password) / connection refused / `NetworkPolicy` drop → listing renders `AuthorName.UNAVAILABLE`, records `Observation.AuthorDirectoryUnavailable`, exports a gauge, log at WARN once per minute (not per request); alert `AuthorDirectoryUnavailable > 2m`; on `statement_timeout` the same path. Security side: nothing changes — its readiness never depended on the readers | memes/comments readiness today is the app's own health; collections `Main.java` plain JDBC | must be proven by a test that pulls the reader password (a `Testcontainers` Postgres with the role revoked) — the estate's rule "a proof that runs nowhere lies" applies |

Manifests that would have to exist (none does): `security-postgres-netpol.yaml`, `memes-egress-netpol.yaml`, `comments-egress-netpol.yaml`, `collections-egress-netpol.yaml`, `default-deny.yaml`; three reader Secrets in every overlay; a `reader-passwords` Job (or init container on security) that runs `ALTER ROLE … PASSWORD`; optional `pgbouncer.yaml`; `postgres-exporter` sidecar in `security-postgres.yaml` + rules in the (missing) observability overlay; a `security` Flyway migration for view + roles. That is the true price of "nieszablonowo": about ten manifests, a credentials split the estate needs anyway, and a NetworkPolicy story it has never had — versus one table and one listener branch per service for the projection.

**Projection feed for P — per-field vs full-state.**

| | per-field (`EMAIL_CHANGED` as today `EmailChangedAnnouncer.java:31,45,49`, plus `NICKNAME_CHANGED` later) | **full-state `USER_PROFILE_CHANGED {id, displayName, updatedAt, version}`** (recommended) |
|---|---|---|
| one lost event | row torn until the *same* field changes again | row stale until *any* change; a replay heals it |
| reordering | last arrival wins per field — wrong if old arrives late | compare `updatedAt`, drop older (LWW); idempotent, ADR 0006 |
| new attribute (nickname) | new event type, new pact interaction, new consumer branch × 3 | new field, additive within version 1 (ADR 0004) |
| backfill / cold start | needs a separate "snapshot" message anyway | the same fact, replayed |
| deletion | `ACCOUNT_DELETED` | the closure itself (§4) — no extra fact |

**How the runner proves it.** `PortalInOneProcess` gets a `HeapAuthorDirectory`; the feature keeps its literals (`portal/specs/account-closure.feature:12` `alice@example.com` — `AccountClosureSteps.java:163`) but the step maps the address to a `UserId` once and passes the id to the router; a new scenario states "after erasure, alice's kept comments render as *deleted account* and her name is gone from every directory". The `account-closure` test-jar pattern (`ClosureParticipantContractTest.java:15`) is repeated for the directory: an `AuthorDirectoryContractTest` every adapter (heap, projection, view) must pass — same idea as the port contract tests closed 2026-09-26.

## 4. Deletion and anonymisation with a projection

Today: MARK hides by status (ADR 0007, `shared/docs/adr/0007-…md:25-40`), ERASE either deletes or `reassignAuthor(id, "deleted account")` (`PurgeUserContent.java:78`, `PurgeUserComments.java:57`), votes are purged first (`:75`).

With `UserId` + projection **P**:

| Concern | What changes | Anchor |
|---|---|---|
| "deleted account" rendering | directory row gone → every content of that id renders as deleted, content rows untouched | port contract |
| **linkage** | if kept content retains the real `author_id`, all posts of one deleted person stay *groupable* — today's sentinel string destroys that (`DeletedAccount.AUTHOR`). So `ANONYMIZE_AUTHOR` / `KEEP_POPULAR_ANONYMIZED` (`shared/purge-rule/.../PurgeRule.java:19-49`) must still write `author_id = NULL` (or a nil sentinel — D6) on kept rows; `reassignAuthor` survives with a new parameter type | `MemeRepository.java:88`, `CommentRepository.java:30` |
| **new duty per participant** | the projection row is PII (display name) → ERASE must also remove/tombstone `author_directory[id]`, **inside** the same `Atomically` step as the purge (`MemesClosureParticipant.java:73`). Collections too, even though favourites carry no author name: the row exists there because the person could be listed elsewhere *(guess: collections may never need names — then no projection there at all, D9)* | `Atomically.java` |
| confirmations | `reserved` stays the count of content rows (`ClosureConfirmation.java:29`); the directory row is not "content" and is not reserved — it is erased at ERASE, restored never (RESTORE re-shows content; the name comes back with the next `USER_PROFILE_CHANGED`, which security emits on unlock — one more line in the outcome listener, `OffboardingOutcomeListener.java:150`) | |
| sweep / alarm | `StuckErasureWatch` (memes, comments) unchanged: a lost ERASE already means content hidden-not-erased; add "directory rows whose id has a `deleted_at` but content still attributed" only if D6 keeps real ids | `memes-infrastructure/.../StuckErasureWatch.java` |
| art. 17 guard | the participants' rule "an owner's request admits no conditions" (`ClosureCommand.allowsConditions`, `MemesClosureParticipant.java:104-108`) is unaffected; the guard that *no e-mail remains anywhere* gains one table to check per service — and, after §6 stage 6, the `author` e-mail columns are gone, so the content DBs hold **less** PII than today (one handle per id instead of an address per row) | |
| `PurgeReservedNothing` | keeps its metric but loses F-014: with a stable key the only remaining meaning is "really held nothing", so the WARN at `MemesClosureParticipant.java:97` can drop to INFO | `Observation.java:45-57` |
| in-flight races | rename during a saga no longer needs the lock at `ConfirmEmailChange.java:98` for correctness (it may stay as UX) | |

## 5. What the rekey machinery becomes (38 files, exact)

| Fate | Files | Count |
|---|---|---|
| **disappear** (rows are no longer re-keyed on rename) | memes `RekeyUserContent`, `UserContentRekey`, `JdbcUserContentRekey`, `RenamedMemberKeepsTheirMemesTest`; comments `RekeyUserComments`, `UserCommentsRekey`, `JdbcUserCommentsRekey`, `RenamedMemberKeepsTheirCommentsTest`; collections `RekeyUserItems`, `UserItemsRekey`, `JdbcUserItemsRekey`, `RenamedMemberKeepsTheirCollectionsTest` | 12 |
| **change job: listener** (consume `USER_PROFILE_CHANGED` → upsert one projection row; the `EMAIL_CHANGED` branch at `memes SecurityEventsListener.java:90-102` goes) | memes `SecurityEventsListener` + `SecurityEventsListenerTest` + `MemesConfig`; comments `SecurityEventsListener` + `SecurityEventsListenerTest` + `CommentsConfig`; collections `SecurityEventsConsumer` + `SecurityEventsConsumerLoopTest` + `ConsumerShutdownTest` + `Main` | 10 |
| **change job: consumer pacts** (`EmailChangedContractTest` → `ProfileChangedContractTest`; the three JSON pacts regenerate) | ×3 tests, ×3 `pacts/*-microservice-security.json` | 6 |
| **change job: security producer** (`EmailChangedAnnouncer` → announces the full profile; called from registration, rename and the replay) | `EmailChangedAnnouncer`, `ConfirmEmailChangeController`, `ConfirmEmailChange`, `ConfirmEmailChangeResult`, `ConfirmEmailChangeTest`, `EmailChangeIsAnnouncedTest`, `SecurityEventPacts` (`:110` "an email changed fact"), `MemesFactsPactProviderTest`, `CommentsFactsPactProviderTest`, `CollectionsFactsPactProviderTest` | 10 |
| docs to amend | 6 `.md` (grep) | 6 |

Net: the machinery does not vanish; a third of it does, the rest shrinks from "UPDATE three tables by address" to "upsert one row by id" — and stops racing the saga.

## 6. Backfill and cutover

The e-mail→UUID map exists only in `users` (V1:2-3).

| Option | Failure modes | Handles drift (registrations/renames during migration)? | PII exposure |
|---|---|---|---|
| one-off CSV export loaded by a Flyway migration | file is stale the second it is written; a PII file in a repo or an ops box; three copies | no | worst |
| temporary admin endpoint `GET /admin/users/ids?emails=…` | sync coupling for the duration; batching; the caller must page through *its own* distinct addresses | yes, if re-run | transient |
| **event replay**: security emits `USER_PROFILE_CHANGED {id, displayName, email}` for every user (the `email` field present **only during the dual period**), participants match `author = email` → set `author_id` | a lost fact = a row left unmatched — visible in the guard query below and repaired by re-running the replay; consumers must be idempotent (they are: ADR 0006) | yes, natively — it is the same stream the projection lives on | e-mail travels once more on a topic it already travels on today (`EmailChangedAnnouncer.java:49`) |

Recommended: replay. Content whose account is already gone: rows with `author = 'deleted account'` → `author_id = NULL` immediately; rows whose address matches no user after the replay has finished (accounts deleted before ADR 0007, test debris) → counted, listed to the owner, then `NULL` — they are unattributable today too.

**Dual-key period.**

| Stage | `author` (e-mail) | `author_id` | reads by | writes |
|---|---|---|---|---|
| add column + index (`author_id UUID NULL`) | key | empty | e-mail | e-mail |
| token/`/me` carry `uid`; controllers set both | key | filling | e-mail | **both** |
| replay complete; guard `SELECT count(*) FROM memes WHERE author_id IS NULL AND author <> 'deleted account'` = 0 (same for `meme_votes.voter`, `comments`, `comment_votes`, `collection_items`) | shadow | key | **id** (ownership, `activeOf`, listings via directory) | both |
| `EMAIL_CHANGED` retired; `author` dropped, `author_id NOT NULL` | gone | key | id | id |

Each row is one deployable per service, reversible until the last: rolling back a stage = redeploying the previous image (both columns are still filled). The last stage is the point of no return and is the only one that removes PII — do it last, per service, after the guard has been zero for a full retention window.

**Where the proof lives per stage:** claim/`/me` — `JwtAccessTokenHttpTest`, offline-jwt tests; facts — `SecurityEventPacts` + the three consumer pacts (ADR 0003); port + adapters — `AuthorDirectoryContractTest` (library test-jar) and `account-closure-specs` with the heap adapter; cutover — `ClosureParticipantContractTest` (commands with id only), `account-closure-specs`, then `portal/e2e/features/account-deletion.feature` on the live stack.

## 7. Risks and what they cost

| Risk | Cost / mitigation |
|---|---|
| token change blast radius | with strategy B: zero for verifiers; one field in `/me`; paddock, UI and `DELETE /account/{email}` keep working. With A: paddock mails a UUID (`Notifications.java:79`), UI breaks, every `AUTHENTICATED_USER` site (memes `MemeController.java:78,237,295`, `AdminController.java:56,79`, `VoteController.java:60,71`, `TagController`; comments `CommentController.java:72,95,158,178`) changes meaning at once |
| projection = hidden dependency of every listing | cold start shows every author as "deleted account" until warm. Mitigations: distinguish *unknown* (no row) from *deleted* (tombstone row `deleted_at`), render unknown as a neutral placeholder and count it; readiness = "replay marker seen" *(guess: worth it only if a service can be re-created from an empty DB in prod — with k8s PVCs it can)* |
| three copies of the projection | library `author-directory` (port, `AuthorName`, contract test, heap adapter, projection upsert SQL is per-service anyway) — same split as `account-closure` (`shared/pom.xml:45`) |
| pact churn | 3 consumer pacts + 3 provider tests change once (per-field would change them again for nickname); the offboarding↔participant pacts change for `Field.USER_ID` — additive, so old consumers keep passing |
| e-mail still needed by security on outcome | unchanged: security mails the leaver itself (`AccountDeletionOrchestrator.java:107`); the saga can stop carrying the address at version 2 |
| the collections unique key | `uq_collection_item (user_email, collection, item_type, item_id)` (`V1:11`) must be rebuilt on `user_id` — one migration, lock on a small table |
| the monolith wins | nothing wasted: `UserId`, `AuthorDirectory`, the runner scenario, the `author_id` columns and the erasure rule are the same; only the projection adapter (P) is replaced by the join (M) — and if V was chosen, M **is** V |
| doing it before the architecture decision | that is the point: every artefact above is either domain/application (identical) or an adapter (swappable). The one thing that would pre-empt the decision is choosing V as the *target*, because a shared database is the architecture decision made by default |

## 8. Decisions that remain the owner's

1. **`sub` unchanged + new `uid` claim, or `sub` = UUID.** — Recommend: keep `sub`, add `uid`; add `"id"` to `/me`.
2. **Where `UserId` lives.** — Recommend: a new one-record shared module beside `email`, not inside `account-closure` or `offline-jwt`.
3. **Projection vs read-only view vs sync call.** — Recommend: projection (P) as the target, view (V) as the monolith adapter only; not S.
4. **What the projection stores.** — Recommend: display name only, computed by security (mask today, nickname tomorrow); never the e-mail.
5. **Per-field vs full-state facts.** — Recommend: full-state `USER_PROFILE_CHANGED` with `updatedAt`, LWW.
6. **Deleted-but-kept content: `author_id NULL`, a nil sentinel, or the real id + tombstone.** — Recommend: `NULL` (breaks linkage like today's sentinel) plus a tombstone row in the directory to tell *deleted* from *unknown*.
7. **Backfill method.** — Recommend: replay via `USER_PROFILE_CHANGED` carrying `email` only during the dual period.
8. **Do voters and `settings.updated_by` move too.** — Recommend: voters yes (they are purged and rekeyed today); `updated_by` becomes an audit snapshot and stops being rekeyed.
9. **Does collections get a directory at all.** — Recommend: no, until a favourites view shows a person's name; it still gets `user_id` and the closure duty.
10. **Library or three copies for the directory port.** — Recommend: library, like `account-closure`.
11. **Does the saga keep carrying `email` after cutover.** — Recommend: no; `ClosureCommand` version 2 carries `userId` only.
12. **Security's own tables re-keyed to id.** — Recommend: out of scope for this mechanism.

## 9. Staged plan (paste into `todo.md`)

1. **`UserId` type.** New shared module with `UserId`; security: `UserRepository.findBy(UserId)`, `User.id()` used; `/me` returns `"id"`; `JwtAccessTokenMint` adds `uid`; `offline-jwt` `VerifiedToken.userId()` Optional. Repos: shared/`<user-id>`, microservice-security, offline-jwt. Proof: `JwtAccessTokenHttpTest`, offline-jwt unit tests, `MeController` HTTP test. Rollback: additive claim/field, drop it.
2. **Full-state profile fact.** `USER_PROFILE_CHANGED {id, displayName, email(dual), updatedAt, version:1}` from registration and rename (replaces the body of `EmailChangedAnnouncer`; `EMAIL_CHANGED` keeps being emitted until stage 7); an admin-only replay command that emits one per user. Repo: microservice-security. Proof: `SecurityEventPacts` provider methods, `EmailChangeIsAnnouncedTest`. Rollback: stop emitting; consumers ignore unknown types (`memes SecurityEventsListener.java:90`).
3. **`AuthorDirectory` library + heap adapter + runner scenario.** Port, `AuthorName`, `AuthorDirectoryContractTest` test-jar, `HeapAuthorDirectory` in `account-closure-specs`; participants and use cases take `UserId` **in parallel signatures** (old `String` ones delegate) so the runner can be switched first. Repos: shared/`author-directory`, portal/account-closure-specs, the three `*-application` modules. Proof: the runner green with the leaver addressed by id. Rollback: unused code.
4. **Projection adapter + dual columns.** Per service: `author_directory` table, `Jdbc…AuthorDirectory`, listener branch for `USER_PROFILE_CHANGED`, `author_id`/`voter_id`/`user_id` nullable columns + indexes; controllers write both from `uid`/`/me id`. Repos: memes, comments, collections (+ their pacts with security). Proof: consumer pacts, `SecurityEventsListenerTest`, DB tests. Rollback: drop columns and table.
5. **Backfill.** Run the replay; guard query per table until zero; unresolved rows reported then nulled. Repo: ops + a one-off admin command in security. Proof: guard = 0 in every DB. Rollback: nothing destructive happened.
6. **Reads and the saga switch to the id.** Ownership, `activeOf/pendingOf`, listings through the directory (mask leaves `MemeController`/`CommentController`); `Field.USER_ID` on commands and confirmations, participants accept either key, ERASE also erases/tombstones the directory row inside `Atomically`; `AccountClosure(UserId …)`, `DELETE /account/{email}` resolves at the edge; `reassignAuthor` writes `NULL`. Repos: account-closure, microservice-security, offboarding, memes, comments, collections, memes-ui (renders `display`), account-closure-specs. Proof: `ClosureParticipantContractTest`, the runner, offboarding↔participant pacts, `e2e/features/account-deletion.feature`. Rollback: previous image per service (both columns still filled).
7. **Retire the address.** Stop `EMAIL_CHANGED`; delete the 12 rekey files; drop `author`/`voter`/`user_email` columns and rebuild `uq_collection_item`; `NOT NULL` on the id columns; `ClosureCommand` v2 without `email`; a source-level guard (like `MemeReadFilterTest`) that no SQL names the dropped columns. Repos: all six. Proof: full CI, nightly e2e, grep guard. Rollback: none — last on purpose.
8. **Optional, monolith path.** `ViewAuthorDirectory` over `JOIN users` for the single assembly; same contract test. Repo: wherever the monolith is assembled. Proof: `AuthorDirectoryContractTest`.
