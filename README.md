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
| `microservice-security` | Auth microservice (register / authenticate / refresh / authorize / logout) |

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

## Tooling

| Script | Purpose |
|--------|---------|
| `aggregate_allure.py` | Merge Allure results across projects |
| `allure-serve.sh` | Serve the aggregated Allure report |
| `create-documentation.sh` | Generate project documentation |
| `maven-cheatsheet.md` | Maven command reference |
| `allure-summary.md` | Current test/documentation summary |
| `todo.md` | Cross-project backlog |
