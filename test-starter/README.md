# test-starter

Multi-module Maven project providing reusable test dependency sets.

## Versioning

Each module has its own independent version. The parent POM is an aggregator with a stable version that rarely changes.

`bdd-test-starter` depends on `unit-test-starter`. The version of this dependency is managed centrally in the parent's `<dependencyManagement>` section.

### Updating unit-test-starter

1. Bump the version in `unit-test-starter/pom.xml`
2. Update the `unit-test-starter` entry in the parent's `<dependencyManagement>` to match
3. Bump the version of `bdd-test-starter/pom.xml` (it transitively picks up the new unit-test-starter)

### Updating bdd-test-starter

1. Bump the version in `bdd-test-starter/pom.xml`

No other changes required - `unit-test-starter` remains unaffected.
