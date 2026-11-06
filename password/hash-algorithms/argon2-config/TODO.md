# TODO

## Argon2Config

- [ ] `Argon2Config` powinno implementować `PropertiesConfigSource`
  - aktualnie ma tylko statyczną metodę `from(PropertiesConfigPort<String>)`

- [ ] Rozróżnienie `PropertiesConfigSource` vs `PropertiesConfigPort`:
  - `PropertiesConfigPort` — interfejs (functional), implementowany przez adapter w warstwie infra (np. Spring, Micronaut)
  - `PropertiesConfigSource` — klasa domenowa, trzyma `PropertiesConfigPort` jako field

- [ ] Test `loadsFromProperties` w `Argon2ConfigRulesTest` do przepisania
  - powinien weryfikować że `Argon2Config` implementuje `PropertiesConfigSource`, nie testować konkretnych wartości
  - UWAGA: test `loadsFromProperties` już nie istnieje (jest `builderWithoutValuesProducesDefaults`);
    ten punkt jest sprzężony z designem `PropertiesConfigSource` wyżej — zrobić razem z nim

## Testy VO

- [x] Refaktoryzacja `IterationsRulesTest`, `MemLimitInKBRulesTest`, `ParallelismRulesTest`
  do abstrakcyjnej klasy bazowej `BoundedIntRulesTest` — ZROBIONE
  - subklasy implementują `min()`, `max()` (OptionalInt — Iterations bez górnej granicy), `create(int)`
  - eliminuje powielony kod testów `acceptsValidValues` / `rejectsInvalidValues`
