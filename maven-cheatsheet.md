### Automatic bump

`mvn versions:use-latest-releases -DallowMajorUpdates=false -DallowMinorUpdates=false -DgenerateBackupPoms=false`

### Dependency tree

`mvn dependency:tree -Dverbose`

be cautious on `(omitted for conflict with X.Y.Z)`. It requires investigation:

- Maven's "nearest wins" rule picked another version of the same artifact. The kept one appears elsewhere in the tree without `(omitted ...)`.
- Often harmless — Maven made a choice and the build works. Becomes a problem when:
  - runtime `NoSuchMethodError`, `NoClassDefFoundError`, or `IncompatibleClassChangeError`
  - tests start failing after a version bump
  - the kept version is older than what some consumer in the graph actually needs

To see which version wins for a specific artifact:

`mvn dependency:tree -Dverbose -Dincludes=<groupId>:<artifactId>`

To force the version you want (in order of preference):

1. **Bump the dependency that brings the older version** — uses the transitive its maintainers tested with, lowest risk of subtle bytecode/API mismatches
2. **Pin in `<dependencyManagement>`** of the appropriate parent POM — forces a version regardless of who brings it
3. **`<exclusion>` + explicit `<dependency>`** with the desired version — last resort, hides intent

Always comment in the POM *why* a pin or exclusion is there. Without context it becomes unmaintainable — the next person (or you in 6 months) won't know if it's still needed.