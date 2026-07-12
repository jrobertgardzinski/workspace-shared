# CLAUDE.md

Guidance for Claude Code when working in this workspace.

## What this repo is

A **workspace / aggregator** over independent git repositories: the shared
libraries (`test-starter`, `constraint`, `config`, `email`, `password`,
`adjustable-clock`, `infrastructure-micronaut-clock`, `voting`, `offline-jwt`), the hexagonal Micronaut
auth service (`microservice-security`), and standalone services in other
flavours — `microservice-email` (BCE, Quarkus), `microservice-memes` (layered modules,
Spring Boot), `microservice-comments` (Spring Boot + Postgres) and
`formula-simulator` (no framework, JDK HTTP server). Each sub-directory has its **own `.git`, history and remote**
and is gitignored here.

**TWO PRODUCTS, not one (the owner's verdict, 2026-07-11; amended 2026-07-12):**
the social PORTAL (memes, comments, favourites) and the F1 GAME (`formula-simulator`
+ its Python `race-sim` + `microservice-paddock`, the game's social hub: the servers
you race on, campaigns, events) are separate beings that share ONLY identity
(`microservice-security` — one account, one token). Never conflate them in docs
or diagrams. The 2026-07-12 amendment moved paddock to the GAME: its users are
players and its `infra` pulls live game state from the instances — the earlier
"portal's hub, F1-flavoured in name only" line was the naming trap biting its
own author. This repo versions only:

- the aggregating `pom.xml` (a pure aggregator, **not** a parent pom),
- shared scripts / docs (`aggregate_allure.py`, `allure-serve.sh`,
  `create-documentation.sh`, `maven-cheatsheet.md`, `allure-summary.md`,
  `todo.md`), and
- the local deployment of the whole stack (`docker-compose.yml`,
  `infra-up.sh` / `infra-down.sh` / `infra-smoke.sh` — security+Postgres,
  email+Mailpit, memes; smoke script proves the cross-service flows).

## Working across repos — important

- Commits made here **do not** touch the sub-repos. To change project code,
  `cd` into the relevant sub-repo and commit there against **its** history.
- All modules share `com.jrobertgardzinski:*:1.0.0-SNAPSHOT`.
- The reactor resolves inter-project dependencies from the reactor itself, so
  `./mvnw install` at the root builds the PORTAL in dependency order without
  needing each project pre-installed into `~/.m2`.
- **`formula-simulator` is deliberately NOT a reactor module** (separate product):
  build it standalone — `./mvnw -f formula-simulator/pom.xml clean verify` — after
  a one-time `./mvnw -pl offline-jwt -am install` (its only workspace dependency).
  `infra-up.sh` / `formula-up.sh` / workspace CI already do this.
- Every sub-project stays buildable standalone (own parent preserved). Do not
  convert the aggregator into their parent — that would edit several separate
  repos.

## Build & test

```bash
./mvnw clean install          # the whole PORTAL reactor
./mvnw -pl microservice-security -am clean verify   # one project + its deps
./mvnw -f formula-simulator/pom.xml clean verify    # the game (separate product)
```

JDK 25. Wrapper pinned to Maven 3.9.9.

## Conventions

- Javadoc and code comments in **English**.
- Git author is canonical: `Robert Gardziński <jrobertgardzinski@gmail.com>`.
- Backlog lives in `todo.md` (here) and each sub-repo's own `todo.md`.
