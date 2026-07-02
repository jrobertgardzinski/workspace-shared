# CLAUDE.md

Guidance for Claude Code when working in this workspace.

## What this repo is

A **workspace / aggregator** over independent git repositories: the shared
libraries (`test-starter`, `constraint`, `config`, `email`, `password`,
`adjustable-clock`, `infrastructure-micronaut-clock`), the hexagonal Micronaut
auth service (`microservice-security`), and standalone services in other
flavours — `microservice-email` (BCE, Quarkus), `microservice-memes`
(layered modules, Spring Boot) and `formula-simulator` (no framework, JDK
HTTP server). Each sub-directory has its **own `.git`, history and remote**
and is gitignored here. This repo versions only:

- the aggregating `pom.xml` (a pure aggregator, **not** a parent pom), and
- shared scripts / docs (`aggregate_allure.py`, `allure-serve.sh`,
  `create-documentation.sh`, `maven-cheatsheet.md`, `allure-summary.md`,
  `todo.md`).

## Working across repos — important

- Commits made here **do not** touch the sub-repos. To change project code,
  `cd` into the relevant sub-repo and commit there against **its** history.
- All modules share `com.jrobertgardzinski:*:1.0.0-SNAPSHOT`.
- The reactor resolves inter-project dependencies from the reactor itself, so
  `./mvnw install` at the root builds everything in dependency order without
  needing each project pre-installed into `~/.m2`.
- Every sub-project stays buildable standalone (own parent preserved). Do not
  convert the aggregator into their parent — that would edit several separate
  repos.

## Build & test

```bash
./mvnw clean install          # whole reactor
./mvnw -pl microservice-security -am clean verify   # one project + its deps
```

JDK 25. Wrapper pinned to Maven 3.9.9.

## Conventions

- Javadoc and code comments in **English**.
- Git author is canonical: `Robert Gardziński <jrobertgardzinski@gmail.com>`.
- Backlog lives in `todo.md` (here) and each sub-repo's own `todo.md`.
