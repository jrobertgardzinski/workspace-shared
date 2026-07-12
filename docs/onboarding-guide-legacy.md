# Project guide — for a junior, hand in hand

> **⚠️ SUPERSEDED (2026-07-11):** the current onboarding document is
> [onboarding-guide.md](onboarding-guide.md) — fuller (tool tags per section, the two
> products, ADR 0006, the memes interview annex) and kept up to date.
> This file remains as an archive; details below may be outdated
> (among others, formula-simulator is no longer a reactor module).

You are reading this because you want to understand what is here, why, and how to touch
it without fear. We go from the general to the specific. Every tool has a "WHY it
exists" angle — a name without a reason to exist teaches nothing.

---

## 1. What this even is (the bird's-eye map)

This is a **workspace-aggregator**: one directory housing A DOZEN-PLUS independent
projects, each with its OWN git repository. The parent directory (`security/`) holds
only the glue: the shared `pom.xml` (a Maven aggregator), the launch scripts,
`docker-compose.yml` and the cross-cutting documentation. The children are in its
`.gitignore` — a commit here does NOT touch the projects, and a commit in a project
does not touch the workspace.

Two "worlds" inside:

**The portfolio world (microservices around the meme gallery)** — every service
deliberately in a DIFFERENT architectural style and a different framework, to show the
different schools:

| Service | Port | Framework / style | Responsibility |
|---|---|---|---|
| microservice-security | 8080 | Micronaut, **5-layer hexagon** | accounts, sign-in, MFA, OAuth, sessions, JWT |
| microservice-email | 8082 | Quarkus, **BCE** | mail delivery (verification, reset, farewell) |
| microservice-memes | 8083 | Spring Boot, **layers** | the gallery: upload, votes, moderation, NSFW |
| microservice-comments | 8085 | Spring Boot + Postgres | comment threads under memes |
| microservice-paddock | 8086 | **Javalin, vertical slices** + PWA | the social side of the game: servers, memberships, events |
| microservice-idp | 8091 | Python | a MOCK Google: OAuth/OIDC for social sign-in tests |
| microservice-image | — | Python + Pillow | PNG→WebP for the gallery (internal) |
| sms / push | — | Python | the paddock's notification channels (stub) |

**The game world (formula-simulator)** — an F1 manager inspired by Jagged Alliance 2:

| Piece | Port | Technology | Responsibility |
|---|---|---|---|
| game backend | 8084 | **pure JDK** (zero framework!) | game state, teams, seasons, economy, SSE broadcast |
| race-sim | 8090 | **pure Python** (stdlib) | physics: races, qualifying, R&D, driving school, manual driving |
| app/ | — | React Native (Expo) | the manager app for phone/PC |
| viewer/ | — | HTML+JS | viewers: replays, track editor, 3D manual driving |

Plus the shared infrastructure: Postgres (a separate database per service!), Kafka,
Mailpit, MinIO, Prometheus+Grafana.

**The portfolio world's most important rule:** every service has its own database and
does NOT peek into anyone else's. Services talk over HTTP (synchronously) or through
Kafka (events).

---

## 2. The tools — which does what, and why we use it

### Git — many repositories
- **Why:** every project lives its own life (its own history, its own GitHub remote).
- **Junior trap no. 1:** `git status` in the parent directory will NOT show changes in
  `microservice-security/`. Want to commit a service change? `cd` INTO it.
- The commit author is canonicalised: `Robert Gardziński <jrobertgardzinski@gmail.com>`.
- Backlogs: `todo.md` in the workspace (cross-cutting) and a separate `todo.md` in every
  project.

### Java 25 + Maven (via `./mvnw`)
- **Why Maven:** builds the Java, manages dependencies. `mvnw` is the "wrapper" — it
  downloads the right Maven version itself, so you install nothing.
- **The reactor:** the workspace `pom.xml` AGGREGATES the projects —
  `./mvnw clean install` at the root builds everything in dependency order. One project
  + its dependencies: `./mvnw -pl microservice-security/security-infrastructure -am package`.
- The aggregator is NOT a parent — every project also builds solo, at home.

### Docker + Docker Compose
- **Why:** every service is packed into a container (an isolated, repeatable
  environment), and Compose raises the WHOLE puzzle with one command, with networks and
  dependencies.
- **An important nuance of this repo:** the Dockerfiles are "runtime-only" — you build
  the jar ON THE HOST (with Maven), the image only copies it. That's why the scripts
  first call `mvnw`, then `docker compose up --build`.
- Inside the Compose network services see each other by NAME (`http://security:8080`),
  and you from the browser via `localhost:PORT`.

### Postgres (×6!)
- **Why:** durable data. EVERY service has its own database container (security, memes,
  comments, paddock, formula…) — deliberately: "database per service" = services don't
  couple through shared tables.
- Schema migrations: **Flyway** (`V1__...sql` files in `src/main/resources/db`) — the
  database "catches up" to the version itself when the service starts.

### Kafka
- **Why:** the EVENT bus — asynchronous conversation. Our examples:
  - **outbox → mail:** security does not send mail itself; it writes the request to
    a table (transactionally, together with the data) and a separate thread publishes it
    to Kafka; the email service consumes and sends. Mailer down? The events wait. This
    is the **transactional outbox** pattern.
  - **the account-deletion saga:** one event, many consumers — memes cleans its own,
    comments anonymises its own.
- Single-node (KRaft) — enough for dev.

### Mailpit
- **Why:** the dev "mailbox". We send no real mail — Mailpit catches everything and
  shows it at `http://localhost:8025`. This is where you get the verification links
  during registration.

### MinIO
- **Why:** object storage speaking Amazon's S3 language. Memes keeps the image BYTES in
  it (metadata stays in Postgres). In production you swap the endpoint for real S3 — the
  code does not change.

### Prometheus + Grafana (+ cAdvisor, node-exporter)
- **Why:** observability — "what happens inside, without guessing".
  - **Prometheus** (`:9090`) POLLS (scrapes) every service for metrics every 10 s and
    stores them as time series.
  - **Grafana** (`:3000`) draws dashboards from that (ready-made: "Stack — containers").
  - **cAdvisor** sees every container (CPU/RAM/network), **node-exporter** — the host.
  - Every service exposes metrics in ITS OWN style: Micronaut via micrometer
    (`/prometheus`), Quarkus (`/q/metrics`), Spring via actuator
    (`/actuator/prometheus`), and pure JDK/Python — a hand-assembled text format
    (`/metrics`) — because the Prometheus format is just a few lines of text.
- Configuration: `observability/` (the scrape list, Grafana provisioning, the dashboard).

### Python 3 (pure stdlib)
- **Why:** the game's simulation modules (`formula-simulator/sim/race/`). A conscious
  decision: ZERO dependencies (no numpy) — a balancing prototype, easy to read.
  Tests: the built-in `unittest`.

### The stub IdP (microservice-idp)
- **Why:** "Sign in with Google" without Google. It plays out the OAuth2/OIDC protocol
  (authorize → code → token → id_token/userinfo), so you test the whole social sign-in
  flow locally. Production = swapping URLs in env.

### Tests — three floors
- **Unit/feature per project:** JUnit 5 + Cucumber (Java; `*.feature` scenarios =
  executable specifications), `unittest` (Python). Pretty reports in **Allure**
  (`allure-serve.sh`).
- **UI:** security-ui (React) tested with cucumber-js + **Playwright** (a code-driven
  browser).
- **The whole-stack smoke:** `./infra-smoke.sh` — a REAL end-to-end test on live
  containers: registration → mail → verification → sign-in → MFA → memes → the deletion
  saga → a race in the game → metrics. If it passes, THE WORLD WORKS.

---

## 3. How to run it (cheat sheet)

```bash
# the whole stack (everything at once):
./infra-up.sh          # builds the jars + docker compose up
./infra-smoke.sh       # proof it works end to end
./infra-down.sh        # cleanup

# just one world:
./memes-up.sh          # gallery + comments + sign-in + mail + monitoring
./formula-up.sh        # the game + physics + sign-in + monitoring

# what to click after startup:
#   http://localhost:8083                     the meme gallery (register/sign in here)
#   http://localhost:8025                     the Mailpit inbox (the links from mails!)
#   http://localhost:8084                     the F1 manager panel
#   http://localhost:8090/school/drive-ui     MANUAL DRIVING (arrows, C, Z/X, M)
#   http://localhost:3000                     Grafana
#   http://localhost:9090                     Prometheus (Status→Targets tab)

# tests:
./mvnw test                                   # all the Java
( cd formula-simulator/sim/race && ./run-tests.sh )   # all the physics, parallel (~15 min)
```

---

## 4. Concepts you will meet here (a glossary that makes sense)

- **Hexagon (ports and adapters):** the domain inside knows nothing of the world;
  "ports" are interfaces, "adapters" are implementations (HTTP, database, Kafka). In
  security additionally 5 layers: domain / config / system (use cases) / application
  (facade) / infrastructure.
- **BCE (Boundary-Control-Entity):** a variant of the same spirit, used in email.
- **Vertical slices (paddock):** instead of layers — vertical slices per feature
  (registry/, events/, feed/...), each with its own routes.
- **Contract-first:** the SCHEMA first (JSON Schema in `formula-simulator/contracts/`),
  then the code on both sides. The contract = the Java↔Python↔user-bots treaty.
- **Offline JWT vs introspection:** a token can be verified (a) by asking security
  `GET /me` on every request (introspection — simple, but couples availability), or
  (b) by checking the token's SIGNATURE with the public key from
  `/.well-known/jwks.json` (offline — our stack-wide standard). Offline's price:
  sign-out "doesn't work" until the token expires.
- **Outbox / saga:** see the Kafka section above.
- **Determinism + pins (the game's physics):** a simulation with the same seed ALWAYS
  gives the same result; tests "pin" specific histories (e.g. "the scrape fight on
  seed 0"). You change the physics → the pin migrates consciously. Since R1 every car
  has ITS OWN randomness stream, so adding a car does not reshuffle the others' fates.
- **Fuzzy senses:** bots don't see "9.34 m", they see `CLOSE 4/5` — like a driver.
  The full physics description: `formula-simulator/docs/physics.md`.

---

## 5. Typical tasks — step by step

### "I want to change something in service X"
1. `cd microservice-X` — you are in ITS repo.
2. Change the code. Tests: `../mvnw test` (or `./mvnw` if the project has its own
   wrapper).
3. Build the jar: `../mvnw -q package -DskipTests` (from the root:
   `-pl microservice-X -am`).
4. Reload the container: `docker compose up -d --build X` (from the workspace ROOT).
5. Commit IN THE SUB-REPO. Done.

### "I want to dig into the game's physics"
1. `cd formula-simulator/sim/race`.
2. Read `../../docs/physics.md` (how it is) and `../../docs/expansion-plan.md`
   (where it's heading).
3. You change the equations → `./run-tests.sh`. Red history pins = either a bug or
   a conscious migration (seed sweeps — patterns in git).
4. Manual driving feels the changes after: `docker compose up -d --build race-sim`
   (from the root).

### "Something in the stack is broken"
1. `docker compose ps` — what's down?
2. `docker compose logs NAME --tail 50` — what's screaming?
3. Grafana → the containers dashboard: who's eating CPU/RAM?
4. `./infra-smoke.sh` — which step of the flow snaps?

### "Where is the documentation for what?"
- `todo.md` (here and in every project) — WHAT is to be done and what was decided.
- `docs/adr/` — architecture decisions (e.g. "the domain does not defend against null").
- `docs/deployment-plan.md` — the deployment plan (Compose → VPS → k3s).
- `docs/glossary/` + `build_glossary.py` — the interactive domain-language dictionary.
- `formula-simulator/docs/` — the game plan, the expansion plan, the physics, tracks,
  bots (`bots/README.md` = the SDK for writing your own driver!).
- `maven-cheatsheet.md` — the Maven cheat sheet.

---

## 6. Things juniors break most often (don't be that junior)

1. **A commit in the wrong repo.** You changed a service, you commit in the workspace —
   the change "vanishes". Always check `pwd` and `git remote -v`.
2. **A stale image.** You changed the code but the container is still old — you forgot
   `--build` or the jar. Symptom: "but I fixed that!".
3. **A test on a busy machine.** The physics has bots with a time deadline — the suite
   raises it itself (`run-tests.sh`), but a single test run next to a full build may
   flake. Suspect a flake? Run it solo.
4. **Editing files while tests run in the background** — modules import code at
   DIFFERENT moments; the verdict will lie. Verdict first, edits after.
5. **Secrets into git.** `.env`, `creds.txt` — they belong in `.gitignore`. Always.
