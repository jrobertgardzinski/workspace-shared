# TODO

Tylko otwarte rzeczy. Historia zrobionego = git log.

## Otwarte — use-case'y (2026-09-02)

- **GenerateCompliantPassword** (Robert: „dać możliwość wygenerowania hasła i chuj"). Ląduje w
  `password-usecase` obok `CreatePasswordHash` — moduł ma wtedy DWA use-case'y.
  - Reguła: wygenerowane hasło spełnia KAŻDY constraint `PasswordPolicy` w mocy (długość,
    znaki specjalne z zestawu polityki, wielka, mała, cyfra). Test naturalny jako property jqwik:
    dla każdego wygenerowanego hasła `policy.constraints()` przechodzi w komplecie.
  - Wariacje: krótkie / średnie / długie (długości względem minimum polityki, np. min, min+4,
    min+12 — do ustalenia). „Można poszaleć": wymawialne, bez znaków mylących (l/1/O/0) itd.
  - Klienci: konta zakładane przez admina, hasła tymczasowe, glue testowe („a valid password"
    względem polityki — patrz microservice-security/specs/README.md o próbkach stopnia Rebuild).
  - Losowość: `SecureRandom` w use-casie; port tylko jeśli testy potrzebują determinizmu.
  - Żywe długości wariantów: MOŻLIWE jako kolejne zamówienie w `security-custom`, ale „nie ma
    musu" — najpierw sam use-case.
- Kandydaci omówieni, NIE zaplanowani: VerifyAndUpgradeHash (rehash po zmianie parametrów
  argon2 — domyka wzorzec drabinki po stronie hashowania; dziś serwis woła `verify` wprost w
  3 miejscach), NotReused (port historii hashów), NotBreached (k-anonimowość), MaxAge (wymaga
  znacznika czasu w `HashedPassword`). Odrzucony: estymator siły obok polityki.

## Otwarte — dowody (2026-09-02)

- ADR 0008 w shared/docs/adr: wzorzec `*-ladder` WYCOFANY 2026-09-05, `password-application`
  SKASOWANY 2026-09-06. Biblioteka nie zna `config`: rekordy w `password-config` niosą klucz (`KEY`)
  i domyślną (`DEFAULT`), `password-usecase` tylko port odczytu `PasswordPolicyInForce`. Drabinki
  (`ConfigLadder.of(KEY, MinLength::new, Rung.live(snapshot, Parse::integer), Rung.restart(props,
  Parse::integer), Rung.rebuild(DEFAULT.value()))`) deklaruje serwis w swojej fabryce beanów; poziom
  live to jeden snapshot tabeli `security_settings` co TTL (`SnapshotLiveConfigPort` w `config`),
  więc KAŻDY klucz ma live/restart/rebuild i nie ma „zamówień" na live per klucz. Do spisania jako
  ADR: dlaczego biblioteka nie zna drabinki i dlaczego live-config to zwykły stan za use-casem.
  `argon2` NIE potrzebuje drabinki (parametry hashowania zmienia się przez rehash, nie na żywo).
