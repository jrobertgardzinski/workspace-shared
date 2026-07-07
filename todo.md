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

## Otwarte zadania (2026-07-06 — po domknięciu OAuth USERINFO + całego MFA A–G)

Kolejność ~malejącej wartości. Zamknięte 2026-07-06 (szczegóły: microservice-security/todo.md
+ git log): OAuth Facebook/GitHub/GitLab (`identity-source` ID_TOKEN/USERINFO, `GET
/oauth/providers` → dynamiczne przyciski, provider = `OauthProviderSettings` w warstwie config,
przepisy w docs/oauth-providers.md); recovery codes jako czynnik ALTERNATYWNY (V13, batch
pokazany raz, konsumpcja atomowa w `MfaChain.verify` — sign-in i step-up za darmo); faza G
(mfa.feature przez realny UI, 22/22 e2e; znalazła i naprawiła martwą gałąź MFA w signIn/
submitFactor — fetch `r.ok` true dla 202). **Temat MFA zamknięty w całości.**

0. **formula-simulator: wielki plan rozbudowy (ZMIANA PLANÓW 2026-07-07 — aktywny temat)** —
   plan spisany w `formula-simulator/docs/expansion-plan.md`: ery jako dane (EraPack w
   RegulationSet), fizyka jazdy per era i podzespół (silnik/turbolag+ERS, skrzynia, hamulce
   z temperaturą i fade, opony crossply/radial, aero mech+aero-split, elektronika TC/ABS/LC,
   masa/paliwo/tankowanie, bezpieczeństwo), katalog 6 er, fazy F0–F9, trzy tory pracy.
   Realizacja w sub-repo formula-simulator (jego todo.md wskazuje plan). user-collections
   (pkt 1) schodzi na drugi plan do odwołania.
1. **microservice-user-collections (DECYZJA 2026-07-07: wydzielamy)** — generyczne kolekcje
   referencji użytkownika (ulubione memy, zapisane komentarze, kiedyś watch/paddock).
   Kształt granicy: element = opaque ref `(itemType, itemId)`; serwis NIE interpretuje
   treści (zero synchronicznych wołań przy zapisie), brak inwariantów dzielonych z domeną
   źródłową — to jest test wejścia dla każdego nowego typu. Hydracja szczegółów po stronie
   UI (skasowany item = „niedostępny"), sprzątanie przez event `UserDeleted` (outbox/Kafka),
   auth = offline JWT jak w memes/comments. NIE wchodzą: preferencje MFA (inwarianty
   security), motyw/język/zgody (nie są referencjami — ewentualny osobny profil kiedyś).
   Odrzucona alternatywa: favourites w memes + saved w comments (prostsza, transakcyjnie
   spójna, ale rozmywa konteksty treści i duplikuje wzorzec per serwis).
2. **Observability (zlecone 2026-07-07: „Grafana itd.")** — ETAP 1 ZROBIONY tej samej
   sesji: `observability/` + kontenery prometheus/grafana/cadvisor/node-exporter w compose
   (Grafana :3000 anonimowo, provisioned datasource + dashboard „Stack — kontenery";
   Prometheus :9090; metryki wszystkich kontenerów bez zmiany linijki w serwisach), wpięte
   w infra-up/infra-smoke, zweryfikowane live (3 targety UP, 6+ kontenerów z metrykami).
   ETAP 2 OTWARTY: `/metrics` per serwis (micrometer w security/email, actuator w memes/
   comments, ręczny eksporter w formule + race-sim), dashboardy per serwis, correlation-id
   w logach (stary temat „observability GO"), ew. Loki na logi.
3. **Odświeżanie linku federacyjnego przy change-email**: dziś stały `(provider,subject)→email`
   po zmianie maila bezpiecznie odpada i re-linkuje się przy następnym logowaniu; czystsze byłoby
   aktualizować link w ConfirmEmailChange.
4. **(opc.) Strona konsumencka podłogi MFA**: memes/comments/paddock mogą odmawiać uprzywilejowanym
   niedopełnionym przez `mfaCompliant` z `/me` (security już to raportuje).
5. **(opc.) Trace correlation-id przez Kafkę**: dziś tylko ścieżka synchroniczna; async przez
   outbox/broker wymaga przeniesienia cid z brzegu HTTP do zdarzenia (context-propagation).
6. **(opc., porządek) Sprzątanie po delete-account**: `enrolled_factors` i `recovery_codes` nie
   mają FK na users — saga kasująca konto zostawia osierocone wiersze (hashe, bez plaintextów);
   dołożyć czyszczenie obu tabel do kroku kasującego usera.

### (USER, zewnętrzne)

- **Realny Google/GitHub/…**: client-id/secret z konsoli providera → podmiana env-ów w compose
  wg microservice-security/docs/oauth-providers.md. Dev/smoke jadą na stub IdP bez tego.
- **Kasacja gałęzi remote sprzed rewrite'u**: `password:gemini-refactor`,
  `microservice-security:overnight/todo-cleanup` (klasyfikator blokuje agenta).

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
