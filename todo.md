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

## Otwarte zadania (2026-07-05 wieczór — po domknięciu MFA A–F + admin reset)

Kolejność ~malejącej wartości; szczegóły MFA w microservice-security/docs/mfa-design.md.

1. ~~**OAuth: Facebook + GitHub + GitLab**~~ — ZROBIONE (2026-07-06). `OidcClient` uogólniony na
   dwa źródła tożsamości (`identity-source`): `ID_TOKEN` (Google/GitLab) i `USERINFO`
   (Facebook/GitHub — kod → access_token → GET userinfo; mapowanie pól, `emails-url` GitHub-shaped,
   `assume-email-verified` jako świadoma decyzja — bez niej `EMAIL_NOT_VOUCHED`). `GET
   /oauth/providers` → dynamiczne przyciski w galerii memów (dodanie providera = tylko config).
   Compose: drugi provider „github" na stubie w trybie USERINFO; smoke kryje obie ścieżki (PASS
   live). Przepisy realnych providerów: microservice-security/docs/oauth-providers.md. Realne
   client-id/secret (Google/GitHub/…) pozostają zadaniem usera (pkt 4).
2. **MFA: recovery codes jako czynnik ALTERNATYWNY** (nie obowiązkowe ogniwo!). Rozszerzyć
   egzekutor: przy weryfikacji proofu na bieżącym ogniwie przyjąć też nieużyty recovery code
   (skonsumować, pominąć ogniwo). `RecoveryCodeRepository` (kody hashowane, jednorazowe),
   endpoint generujący (pokazać raz), zużycie w `ContinueAuthentication` i `StepUp`. UI: „use a
   recovery code" na ekranie kodu.
3. **MFA w e2e security-ui** (faza G, opcjonalny szlif). cucumber-js/Playwright: enrollment
   e-mail/TOTP + logowanie dwustopniowe + step-up przy delete. `/test/mailbox` musi wystawić też
   kod AUTH_CODE. (MFA już pokryte 4 testami HTTP + live smoke — niski przyrost wartości.)
4. **(USER, zewnętrzne) Realny Google**: client-id/secret z Google Cloud Console → podmiana 4
   env-ów w compose. Dev/smoke jadą na stub IdP bez tego.
5. **Odświeżanie linku federacyjnego przy change-email**: dziś stały `(provider,subject)→email`
   po zmianie maila bezpiecznie odpada i re-linkuje się przy następnym logowaniu; czystsze byłoby
   aktualizować link w ConfirmEmailChange.
6. **(opc.) Strona konsumencka podłogi MFA**: memes/comments/paddock mogą odmawiać uprzywilejowanym
   niedopełnionym przez `mfaCompliant` z `/me` (security już to raportuje).
7. **(opc.) Trace correlation-id przez Kafkę**: dziś tylko ścieżka synchroniczna; async przez
   outbox/broker wymaga przeniesienia cid z brzegu HTTP do zdarzenia (context-propagation).

## Plan — kolejność realizacji (ustalona 2026-07-05) — SKONSUMOWANY

Szczegóły każdego punktu: microservice-security/todo.md (wpisy OAuth/MFA/step-up).

1. (USER) kasacja gałęzi remote sprzed rewrite'u: `password:gemini-refactor`,
   `microservice-security:overnight/todo-cleanup` (klasyfikator blokuje agenta).
2. ~~infra-up.sh buduje też jary comments/paddock~~ — ZROBIONE (2026-07-05).
3. ~~Stub IdP jako mikroserwis~~ — ZROBIONE (2026-07-05): `microservice-idp` (:8091), HS256
   id_tokeny, kody jednorazowe+PKCE; repo na GitHubie (PRIVATE — decyzja usera), spushowane.
4. ~~Port/adapter OIDC~~ — ZROBIONE: `OidcClient` (JDK HttpClient), provider = @EachProperty.
5. ~~Tożsamości federacyjne~~ — ZROBIONE: V10, `FederatedSignIn`, konta bezhasłowe.
6. ~~Przepływ /oauth/*~~ — ZROBIONE: feature (5 scen.) + OauthFlowHttpTest + smoke PASS live.
7. ~~Łączenie kont a/b/c~~ — ZROBIONE (auto-link / przejęcie squattera / podpowiedź w mailu).
8. ~~UI galerii: przycisk Google~~ — ZROBIONE (token wraca fragmentem, błąd jako notice).
9. [MFA, PO ANALIZIE USERA] egzekutor łańcucha (hasło ALBO provider = ogniwo #1; sesja po
   ostatnim ogniwie).
10. Port kodów + adaptery email/SMS (hash, TTL, jednorazowość, SourceThrottle).
11. Enrollment per user + `PROVIDER_SATISFIES_CHAIN`.
12. (opc.) TOTP + recovery codes.
13. [STEP-UP] elewacja sesji (jednorazowa, TTL) + polityki per akcja NONE/SECOND_FACTORS/FULL_CHAIN.
14. Wpięcie: delete=FULL_CHAIN, change-password=SECOND_FACTORS, revoke-all=TBD; federacyjni
    prompt=login.
15. ~~Konsument offline JWT w memes/comments~~ — ZROBIONE (2026-07-05): brama `security.verify=offline` weryfikuje podpis EdDSA przez JWKS zamiast /me; comments biegnie offline w compose.
16. ~~Anty-enumeracja na /account/email~~ — ZROBIONE (2026-07-05): zajęty adres = 202 jak świeży, notka mailem.
17. ~~UI jako 3. wejście~~ — ZROBIONE (2026-07-05): security-ui (Angular) + cucumber-js/Playwright, 17 scenariuszy specs/ zielonych.
18. ~~(memes) flaga NSFW~~ — ZROBIONE (2026-07-05): FlagMeme (moderator-only) + ContentFlags, V3, blur w galerii.

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
