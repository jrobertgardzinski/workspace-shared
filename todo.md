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
   **UWAGA — SAGA (obowiązkowe przy budowie):** user-collections to trzeci uczestnik sagi
   usuwania konta (dziś: memes + comments). `AccountDeletionOrchestrator` w security kończy
   usuwanie dopiero po potwierdzeniach WSZYSTKICH uczestników — trzeba dodać trzecie
   oczekiwane potwierdzenie (nowy topic `usercollections-events`, uczestnik w `SagaParticipants`
   / tabeli saga_participants). To jednak uczestnik PROSTY: kasuje wszystkie referencje usera
   wholesale (nie parsuje `PurgeRule` — refy są opaque, bez DELETE/ANONYMIZE/KEEP), więc NIE
   jest to „trzeci konsument PurgeRule" i nie wyzwala ekstrakcji wspólnej libki. Bez tej zmiany
   saga albo nigdy się nie domknie (czeka na uczestnika, który nie odpowiada), albo kolekcje
   usuniętego usera osierocą się.
2. **Observability (zlecone 2026-07-07: „Grafana itd.")** — ETAP 1 ZROBIONY tej samej
   sesji: `observability/` + kontenery prometheus/grafana/cadvisor/node-exporter w compose
   (Grafana :3000 anonimowo, provisioned datasource + dashboard „Stack — kontenery";
   Prometheus :9090; metryki wszystkich kontenerów bez zmiany linijki w serwisach), wpięte
   w infra-up/infra-smoke, zweryfikowane live (3 targety UP, 6+ kontenerów z metrykami).
   ETAP 2 W TOKU (2026-07-07 później): security (micrometer, `/prometheus`), race-sim
   (stdlib `/metrics`: requesty/aktywne jazdy/uptime), email (quarkus-micrometer,
   `/q/metrics`), memes i comments (actuator, `/actuator/prometheus`) — 8/8 targetów UP.
   Przy okazji: zastany bug memes z sesji MinIO (S3ObjectStore: dwa konstruktory bez
   @Autowired = śmierć na starcie przy blob-store=s3) naprawiony w sub-repo; race-sim
   Dockerfile nadgonił F0 (obraz bez eras/ padał) i wozi teraz viewer+bolid (drive-ui
   działa Z KONTENERA, ten sam URL). ETAP 2 DOMKNIĘTY (jeszcze później tej sesji):
   paddock (ręczny /metrics jako slice, licznik per route + JVM) i formula backend (JDK,
   ręczny /metrics) — **10/10 targetów UP**. ETAP 3 (traceability) ZROBIONY: correlation-id
   przez Kafkę end-to-end (patrz pkt 5). ETAP 4 (logi) ZROBIONY: Loki + Promtail w compose
   (Promtail zbiera stdout wszystkich 20 kontenerów przez Docker SD — zero zmian w serwisach),
   provisioned datasource Loki w Grafanie obok Prometheusa. Spłata cid: `{service=~".+"} |=
   "<cid>"` grepuje jedno żądanie po wszystkich serwisach naraz — zweryfikowane live (jeden cid
   → linie z security I email w jednym zapytaniu). ZOSTAŁO (etap 5, opcjonalnie): dashboardy
   per serwis + panel logów, alerty; OTel+Tempo (waterfall spanów, datasource Tempo + derived
   field z Loki po cid). CI ZROBIONE: workflow w workspace (`.github/workflows/ci.yml`)
   checkoutuje 13 sub-repo i buduje cały reaktor jednym `./mvnw clean install` (testy włącznie,
   Testcontainers gdy jest Docker) — zweryfikowane lokalnie: BUILD SUCCESS, 43 moduły, 0 porażek.
   email i paddock (samowystarczalne) mają też własne, szybkie CI. ZASTRZEŻENIE: sama komenda
   sprawdzona lokalnie, ale pierwszy run na Actions może wymagać PAT, jeśli któreś sub-repo jest
   prywatne (domyślny GITHUB_TOKEN czyta tylko publiczne repo tego samego właściciela); JDK 25
   temurin przez setup-java. ETAP 5 (traces) ZROBIONY: OTel Java agent (2.29.0 — 2.11.0 ładuje
   się na JDK 25 ale NIC nie instrumentuje) podpięty do 4 serwisów JVM przez JAVA_TOOL_OPTIONS +
   wolumen (bez rebuildu), eksport do Tempo, datasource Tempo w Grafanie (z jumpem do logów Loki).
   Zweryfikowane live: upload → trace `[memes, security]`, odczyt komentarzy → `[comments, memes,
   security]`. LUKA: saga usuwania konta NIE linkuje się w jeden trace — idzie przez outbox
   drenowany async (pooler bez aktywnego spanu), ta sama granica co cid; fix = persystować W3C
   `traceparent` w wierszu outboxa jak cid. ZOSTAŁO (opc.): traceparent w outboxie (domknięcie
   sagi w tracingu), dashboardy per serwis + panel logów, alerty, e2e jako osobny job CI.
3. **Odświeżanie linku federacyjnego przy change-email**: dziś stały `(provider,subject)→email`
   po zmianie maila bezpiecznie odpada i re-linkuje się przy następnym logowaniu; czystsze byłoby
   aktualizować link w ConfirmEmailChange.
4. **(opc.) Strona konsumencka podłogi MFA**: memes/comments/paddock mogą odmawiać uprzywilejowanym
   niedopełnionym przez `mfaCompliant` z `/me` (security już to raportuje).
5. ~~**Trace correlation-id przez Kafkę**~~ — ZROBIONE I ZWERYFIKOWANE LIVE (2026-07-07):
   cid jedzie nagłówkiem Kafki `X-Correlation-Id` przez WSZYSTKIE granice frameworków.
   Producent (security, Micronaut): outbox trzyma cid w kolumnie (V14), poller wysyła go
   nagłówkiem. Konsumenci przywracają cid do MDC: memes/comments (Spring, `@Header`), security
   (`@MessageHeader`), email (Quarkus, `IncomingKafkaRecordMetadata` + `Message<String>` +
   ręczny ack; log `%X{cid}`). Potwierdzenia sagi niosą cid dalej (helper `KafkaTracing`,
   bliźniak w memes/comments). **Bug złapany live**: MDC nie przechodzi przez wątki Micronauta
   (filtr na event-loopie, outbox na wątku `@ExecuteOn`) — cid czytany teraz z atrybutu żądania
   (`ServerRequestContext`), który Micronaut propaguje. Dowód: rejestracja z `X-Correlation-Id`
   → ten sam cid w logu email; usunięcie konta → ten sam cid w memes+comments (saga a941e7a5).
   email dostał brakujący filtr/log (miał 0 cid). ZOSTAŁO (opc.): async SMTP w email biegnie na
   innych wątkach Mutiny — głębokie logi dispatchera wymagałyby smallrye-context-propagation.
6. **(opc., porządek) Sprzątanie po delete-account**: `enrolled_factors` i `recovery_codes` nie
   mają FK na users — saga kasująca konto zostawia osierocone wiersze (hashe, bez plaintextów);
   dołożyć czyszczenie obu tabel do kroku kasującego usera.

### Plany na przyszłość (spisane)

- **Wdrożenie/orkiestracja** → [docs/deployment-plan.md](docs/deployment-plan.md):
  Compose dziś → VPS+Compose+Traefik na publikację → k3s przy piramidzie dywizji;
  Podman i pełny k8s odrzucone z uzasadnieniem (plusy/minusy + wyzwalacze przejść).

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
