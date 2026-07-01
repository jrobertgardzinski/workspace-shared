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

## Aktywne — priorytet

- **Ujednolicić autora commitów (C2) — WYSOKI PRIORYTET.** Dryf
  „Robert Gardziński" vs „jrobertgardzinski" w historii. Kanon:
  `Robert Gardziński <jrobertgardzinski@gmail.com>`. Global config już OK (nowe
  commity dobre); zostaje REWRITE historii per-repo + `git push --force`.
  ⚠️ nieodwracalne/wychodzące — potwierdzić przed force-push.

## Kolejność większych tematów

- **Glosariusz UL (skeleton) PRZED dodawaniem use case'ów.** Decyzja: nie dokładać
  nowych use case'ów (Verify email, Reset hasła itd.) zanim nie powstanie cienki
  walking-skeleton glosariusza + konwencja tagów — wtedy use case'y zasilają
  gotowy pipeline zamiast rosnąć jako materiał do retrofitu.
  (Backlog use case'ów: microservice-security/todo.md.)
