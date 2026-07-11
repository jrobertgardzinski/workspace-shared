# security

Workspace for the security projects. Each sub-directory is an **independent git
repository** with its own history and remote; this repo only ties them together
for convenience:

- an **aggregating `pom.xml`** so the whole thing builds as one Maven reactor and
  imports as a single project in the IDE, and
- **shared scripts and tooling** used across the projects (Allure aggregation,
  documentation generation, cheatsheets).

The sub-repositories are gitignored here — this workspace does **not** version
their contents.

## Projects

Build order is computed automatically by the Maven reactor; the natural
dependency order is:

| Module | Description |
|--------|-------------|
| `test-starter` | Shared JUnit5 / BDD / system test starters |
| `constraint` | Constraint / validation primitives |
| `config` | Configuration primitives (`PropertiesConfigPort`/`Source`) |
| `email` | Email value objects + email-security |
| `password` | Password value objects, hash algorithms (Argon2), password-security |
| `adjustable-clock` / `infrastructure-micronaut-clock` | Steerable test clock + its Micronaut adapter |
| `microservice-security` | Auth microservice, hexagonal on Micronaut (13 use cases: register, authenticate, sessions, verify-email, resets, …) |
| `microservice-email` | Mail microservice, BCE on Quarkus (`POST /mails*`, Qute templates) |
| `voting` | Voting bounded context as a library (toggle + tally over the Ballots port) |
| `microservice-memes` | Meme microservice, layered modules on Spring Boot (upload/thumbnails/votes, gallery UI) |
| `microservice-comments` | Comment threads + comment votes; Spring Boot with its own Postgres |
| `formula-simulator` | F1 simulator with autonomous drivers, no framework (JDK HTTP server) |

## Build

The aggregator is a **pure aggregator**, not the parent of the modules — every
sub-project keeps its own parent and stays buildable standalone.

```bash
# Whole reactor, correct order, one command:
./mvnw clean install

# A single project standalone:
cd microservice-security && ./mvnw clean verify
```

Requires JDK 25. The Maven Wrapper (`./mvnw`, Maven 3.9.9) is committed, so no
system Maven is needed.

## Run the whole stack

`docker-compose.yml` starts the three deployable services together with their
infrastructure: `microservice-security` + Postgres, **Kafka** (mail requests travel as events
from security's transactional outbox), `microservice-email`
delivering through [Mailpit](https://mailpit.axllent.org/) (web inbox at
http://localhost:8025 — that's where the verification/reset mails land), and
`microservice-memes`, whose **gallery UI** lives at http://localhost:8083/
(browsing is public; sign-up/sign-in goes through security, and only signed-in
users upload, comment and vote).

```bash
./infra-up.sh      # package the jars, build the images, start everything
./infra-smoke.sh   # end-to-end proof: register -> mail -> verify -> sign-in -> /me, meme upload
./infra-down.sh    # stop (add -v to drop the database volume)
```

Ports: security 8080, email 8082, memes 8083 (gallery UI), comments 8085, Mailpit UI 8025.

## Tooling

| Script | Purpose |
|--------|---------|
| `build_c4.py` | Generate C4 diagrams (`docs/c4-architecture.md`) from docker-compose.yml + the Pact files |
| `aggregate_allure.py` | Merge Allure results across projects |
| `allure-serve.sh` | Serve the aggregated Allure report |
| `create-documentation.sh` | Generate project documentation |
| `infra-up.sh` / `infra-down.sh` / `infra-smoke.sh` | Run and smoke-test the whole service stack (see above) |
| `maven-cheatsheet.md` | Maven command reference |
| `allure-summary.md` | Current test/documentation summary |
| `todo.md` | Cross-project backlog |
