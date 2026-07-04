# Workspace TODO

Cross-project backlog. Per-project backlogs live in each repo's own `todo.md`.

## Rozstrzygnięte

- **Null safety w domenie** — DECYZJA: domena NIE broni się przed `null`, infra
  (warstwa application) pilnuje, żeby null nie dotarł do domeny. Czytelny kod
  kosztem wrażliwości domeny na null. Spisane jako
  [ADR 0001](docs/adr/0001-no-null-guards-in-value-objects.md) (2026-07-01).
  - Follow-up (drobny) — ZROBIONE (2026-07-01): audyt VO w `security-domain`.
    `IpAddress` już nie miał `requireNonNull`; usunięto guardy `null` z
    `AbstractToken` (blank check zostaje) i `AbstractTokenExpiration`. Reszta VO
    czysta. `security-{domain,config,system}` build zielony, 24 testy jedn. OK.
- **Ujednolicenie testów w Allure** — ZAMKNIĘTE jako przedawnione; format
  dopieszczony na tyle, na ile się dało (password policy jako wzorzec).
- **Topologia gita (C1)** — OSOBNE REPA PER LIB. Workspace `security` = tylko
  agregator (pom + skrypty, dzieci gitignorowane). Do rozważenia kiedyś: monorepo
  tylko dla common libs (test-starter, adjustable-clock itp.), nie dla całości.

## Rozstrzygnięte (cd.)

- **Ujednolicić autora commitów (C2)** — ZROBIONE (2026-07-01). Rewrite historii
  (`git filter-branch`, autor+committer → kanon `Robert Gardziński
  <jrobertgardzinski@gmail.com>`, w tym web-UI committer `GitHub`) + force-push
  we wszystkich 6 sub-repo. Zweryfikowane na remote: wszystkie **lokalne**
  gałęzie kanoniczne i w sync (main + feature/mfa wszędzie; ms-security też
  development/interactive-documentation/restructure/simplify-modules/smarter-factory).
  Global `git config user.*` OK. Zapas starych SHA w scratchpadzie sesji.
  - Follow-up (opcjonalny): gałęzie **tylko-remote** poza rewritem —
    `password:gemini-refactor`, `microservice-security:overnight/todo-cleanup`
    oraz omyłkowa gałąź `origin` na kilku remote (kandydat do skasowania).

## Kolejność większych tematów

- **Glosariusz UL (skeleton) PRZED dodawaniem use case'ów** — SKONSUMOWANE
  (2026-07-01/02): glosariusz stoi (`build_glossary.py` + `docs/glossary`,
  skanuje też warstwy BCE i memes), a nowe use case'y (verify-email, reset
  hasła, change password/email, delete account, list/revoke sessions) weszły
  już w gotowy pipeline. (Backlog use case'ów: microservice-security/todo.md.)

## W toku

- **microservice-paddock (socjale wokół gry)** — walking skeleton ZROBIONY (2026-07-04):
  vertical slices + Javalin (piąty smak) + PWA mobile-first; rejestr serwerów
  (PRIVATE/OPEN/CHAMPIONSHIP, członkostwa), „mój paddock" z żywym stanem instancji,
  wydarzenia z RSVP; tożsamość przez introspekcję w security; Postgres+Flyway (H2 bez
  DB_URL); wpięty w compose (:8086). Backlog w microservice-paddock/todo.md (wydarzenia
  wywiedzione ze stanu gry, feed aktywności z powtórkami, rejestr→provision, powiadomienia).
