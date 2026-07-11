# Dokument wdrożeniowy — cały projekt po kolei, jak dla juniora

Stan na **2026-07-11**. Ten dokument prowadzi Cię przez cały projekt od zera: **co to jest**,
**jakie narzędzia go budują**, **za co każde odpowiada** i **jak to uruchomić**. Zakładam, że
umiesz programować, ale wielu narzędzi widzisz pierwszy raz. Czytaj po kolei — każda sekcja
buduje na poprzednich.

Każdy dział zaczyna się od bloku **🏷️ Tagi** — listy narzędzi/technik, które w nim występują,
z jednozdaniowym wyjaśnieniem. Jak szukasz konkretnego narzędzia, skanuj same tagi.

---

## 1. Czym w ogóle jest ten projekt?

> 🏷️ **Tagi:**
> **mikroserwisy** — system złożony z małych, niezależnie wdrażanych serwisów;
> **multi-repo** — każdy serwis w osobnym repozytorium git (przeciwieństwo monorepo);
> **workspace / agregator** — repo-spinacz, które nie zawiera kodu serwisów, tylko skleja je do wspólnej pracy.

To **nie jest jeden program**. To **portfolio mikroserwisów** — kilkanaście niezależnych
serwisów składających się na **DWA OSOBNE PRODUKTY** (werdykt właściciela 2026-07-11 —
nie sklejać ich w opisach!):

1. **portal społecznościowy** — galeria memów, komentarze, ulubione, hub „paddock"
   (serwery/ludzie/wydarzenia; F1 tylko z nazwy);
2. **gra F1** (`formula-simulator` + `race-sim`) — menedżer z autorytatywną symulacją,
   osobny byt z własną bazą i własnym modelem wdrożenia (serwery grupowe per liga).

Produkty dzielą wyłącznie **TOŻSAMOŚĆ** (jedno konto + MFA w `microservice-security`,
jeden token działa w obu) oraz ten dev-compose; gra nie ma ani jednej krawędzi do
memów/komentarzy/Kafki (dowód: diagramy C4 generowane z compose). Kluczowa idea portfolio:

> **Ta sama architektura (hexagonalna) zrealizowana w RÓŻNYCH frameworkach — sześciu „smakach".**

Micronaut, Quarkus, Spring Boot, Javalin, Helidon SE i „goły" JDK. Portfolio pokazuje, że wzorzec
jest przenośny; jak zrozumiesz jeden serwis, zrozumiesz wszystkie.

**Każdy podkatalog to osobne repozytorium git** z własną historią i własnym remote na GitHubie.
Repo `security` (ten katalog) to tylko **workspace-spinacz** — kod serwisów jest w nim
gitignorowany. Workspace wersjonuje wyłącznie:

- `pom.xml` agregatora (buduje wszystko jedną komendą),
- `docker-compose.yml` + skrypty `infra-*.sh` (uruchamiają cały stack lokalnie),
- dokumentację (`docs/`, w tym ten plik), backlog (`todo.md`), narzędzia
  (`aggregate_allure.py`, `build_glossary.py`).

**Najważniejsza konsekwencja praktyczna:** commit w workspace **nie dotyka** serwisów. Żeby
zmienić kod serwisu, wchodzisz do jego katalogu (`cd microservice-...`) i commitujesz **tam**,
względem JEGO historii.

---

## 2. Fundament: język i budowanie

> 🏷️ **Tagi:**
> **JDK 25** — kompilator + maszyna wirtualna Javy; **virtual threads (Loom)** — tanie wątki JVM, można mieć ich miliony;
> **Maven** — narzędzie budowania (kompilacja, zależności, testy, pakowanie); **pom.xml** — plik konfiguracyjny Mavena;
> **Maven Wrapper (`./mvnw`)** — skrypt, który sam ściąga właściwą wersję Mavena (3.9.9);
> **reaktor** — Mavenowe budowanie wielu modułów naraz, w kolejności zależności;
> **`~/.m2`** — lokalne repozytorium artefaktów Mavena na Twoim dysku.

Cały backend to **Java 25**. Frontendy to TypeScript (o nich w sekcji 12), pomocnicze stuby —
Python. Virtual threads wykorzystują `user-collections` (Helidon SE) i `formula-simulator`.

**Maven** czyta `pom.xml` („nazywam się tak, zależę od tych bibliotek, buduj mnie tak").
Zawsze używaj `./mvnw`, nie systemowego `mvn`. Workspace'owy `pom.xml` to **czysty agregator** —
wypisuje serwisy jako `<module>`, ale **nie jest ich rodzicem** (każdy serwis ma własnego parenta
i buduje się samodzielnie; nie zmieniaj tego, bo to edycja wielu osobnych repozytoriów).

Reaktor sam rozwiązuje zależności między projektami — `./mvnw clean install` w workspace buduje
moduły PORTALU w dobrej kolejności, bez wcześniejszego instalowania czegokolwiek do `~/.m2`.
Wszystkie moduły mają wspólne współrzędne `com.jrobertgardzinski:*:1.0.0-SNAPSHOT`.

**Wyjątek — gra F1 (osobny produkt!):** `formula-simulator` celowo NIE jest modułem
reaktora (werdykt 2026-07-11). Budujesz ją standalone, na jej własnym pomie:
`./mvnw -f formula-simulator/pom.xml clean verify` — a jedyną bibliotekę workspace'u,
której potrzebuje, instalujesz raz: `./mvnw -pl offline-jwt -am install`.

| Komenda | Co robi |
|---|---|
| `./mvnw clean install` | Zbuduj cały PORTAL (pierwszy raz długo — ściąga zależności); gra buduje się osobno (wyjątek wyżej) |
| `./mvnw -pl microservice-security -am clean verify` | Jeden projekt + to, od czego zależy (`-am` = also make) |
| `./mvnw test` / `verify` / `package` | testy jednostkowe / +integracyjne / zbuduj jar |
| `-DskipTests` | pomiń testy (gdy chcesz tylko jar) |

Ściąga: `maven-cheatsheet.md` w korzeniu workspace.

---

## 3. Git w tym projekcie

> 🏷️ **Tagi:**
> **git multi-repo** — kilkanaście niezależnych repozytoriów obok siebie; **remote / origin** — zdalna kopia repo na GitHubie;
> **gh (GitHub CLI)** — narzędzie do GitHuba z terminala (PR-y, repo, API); **PAT** — Personal Access Token, hasło do pushowania przez HTTPS;
> **Co-Authored-By** — stopka commita wskazująca współautora.

- Autor kanoniczny: `Robert Gardziński <jrobertgardzinski@gmail.com>` (historia wszystkich repo
  została pod to przepisana).
- Push działa przez `credential.helper store` + PAT. `gh` jest zainstalowane w `~/.local/bin/gh`
  i zalogowane.
- Część repo jest **prywatna** (`formula-simulator`, `microservice-idp`), reszta publiczna —
  ma to znaczenie dla CI (sekcja 15).
- Backlog: `todo.md` w workspace (przekrojowy) + `todo.md` w każdym sub-repo (lokalny).
  **To pierwsze miejsce, gdzie sprawdzasz „co się działo i co dalej".**

---

## 4. Architektura: hexagonal (porty i adaptery)

> 🏷️ **Tagi:**
> **architektura heksagonalna / porty i adaptery** — logika biznesowa odseparowana od technologii;
> **DDD (Domain-Driven Design)** — modelowanie kodu wokół pojęć domeny biznesowej; **value object** — mały niemutowalny obiekt z inwariantami (np. `EmailAddress`);
> **use case** — jedna operacja aplikacji jako klasa z `execute(...)`;
> **port** — interfejs opisujący, czego logika potrzebuje; **adapter** — konkretna implementacja portu (JDBC, HTTP, in-memory);
> **ADR (Architecture Decision Record)** — krótki dokument utrwalający decyzję architektoniczną i jej powody.

Najważniejszy wzorzec w projekcie. Kod ułożony jest w warstwy, zależności **tylko w dół**:

```
infrastructure (adaptery)      <- świat zewnętrzny: HTTP, baza, Kafka, UI
     |  wywołuje
application (use case'y)       <- "co robi aplikacja": SaveItem, DeleteComment...
     |  używa portów (interfejsów)
domain (model + reguły)        <- czyste reguły biznesowe, zero technologii
```

- **domain** — czysty model (`ItemRef`, `Comment`, `Driver`), bez jednej linijki o bazie czy HTTP.
- **application** — use case'y zależne wyłącznie od **portów** (np. `CollectionStore` — „umiem
  zapisać/usunąć/wypisać", nie mówi JAK).
- **infrastructure** — adaptery: `JdbcCollectionStore` (produkcja), `InMemoryCollectionStore`
  (testy). Podmieniasz adapter → zmieniasz technologię bez ruszania logiki.

`microservice-security` dzieli to jeszcze drobniej, na **sześć modułów-warstw**:
`security-domain` → `security-config` → `security-system` → `security-application` →
`security-infrastructure` → `security-ui`. Warstwa **config** to osobliwość projektu: wszystkie
„pokrętła" (limity, TTL-e, polityki) są **framework-free rekordami** w `security-config`;
infrastruktura tylko binduje na nie properties. Dzięki temu konfiguracja jest testowalna
jednostkowo.

### Jeden wzorzec, różne dialekty — mapa per serwis

**Wspólna dla wszystkich jest REGUŁA ZALEŻNOŚCI (do środka), nie nazwy pakietów** —
różnorodność jest celowa (portfolio!). Kto jak mówi:

| Serwis | Układ pakietów/modułów | Dialekt |
|---|---|---|
| `microservice-security` | `domain → config → system → application → infrastructure → ui` (6 MODUŁÓW Mavena) | pełny hexagon, najdrobniejszy podział; `system` = use case'y, `application` = orkiestracja/serwisy |
| `formula-simulator` | `domain / config / system / application / infrastructure` (te same warstwy, JEDEN moduł) | ten sam dialekt co security — pakietami zamiast modułami; sprawdzone: zero importów `infrastructure` z wewnętrznych warstw |
| `microservice-comments` | `domain / application / config / infrastructure` | hexagon 4-pakietowy (bez osobnego `system` — use case'y w `application`) |
| `microservice-user-collections` | `domain / application / infrastructure` | hexagon minimalny, 3 pakiety |
| `microservice-memes` | `domain / application / config / infrastructure` + `image`, `tags` (7 modułów) | „layered modules": warstwy + wydzielone zdolności; framework w JEDNYM module z siedmiu |
| `microservice-email` | `boundary / control / entity` | **BCE** — świadomie INNY wzorzec z tej samej rodziny (Quarkus); boundary≈adapter, control≈use case, entity≈domena |
| `microservice-paddock` | `events / feed / myservers / notifications / registry / workshop / infra` | **package-by-feature**: pionowe ficzery zamiast poziomych warstw — trzeci sposób krojenia |

Morał: jak przechodzisz między serwisami, szukaj tej samej REGUŁY („logika nie zna
technologii; zależności do środka"), a nie tych samych katalogów. `system` vs
`application` to kwestia dialektu security/formula: tam use case'y i orkiestracja
mieszkają osobno.

**Decyzje są spisane w ADR-ach** — `docs/adr/` w workspace (0001: domena nie broni się przed
null, pilnuje tego warstwa aplikacji; 0002: prefiks `_` dla kroków use case'ów; 0003–0005 —
sekcje 9–10; **0006: komendy idempotentne DOMYŚLNIE** — prawo egzekwuje generyczny test,
scenariusze BDD zostają dla wyjątków i kontraktów odpowiedzi). Zanim zakwestionujesz coś
„dziwnego", sprawdź, czy nie ma o tym ADR-a.

### Spec-first: Gherkin i Cucumber

> 🏷️ **Tagi:**
> **Gherkin** — język scenariuszy `Given/When/Then` czytelny dla człowieka (pliki `.feature`);
> **Cucumber** — silnik wykonujący scenariusze Gherkina jako testy; **glue/kroki** — kod łączący zdania scenariusza z wywołaniami aplikacji;
> **spec-first** — najpierw wykonywalna specyfikacja, potem kod.

Zachowanie opisujemy **najpierw** w plikach `.feature`, potem Cucumber wykonuje je jako testy:

```gherkin
Scenario: Saving twice tells the caller it was already there
  Given alice has saved meme 42 into "favourites"
  When alice saves meme 42 into "favourites"
  Then the save reports it was already there
  And alice's "favourites" contains meme 42 once
```

Uwaga do czytania (i **ADR 0006**, werdykt właściciela): scenariusz NIE testuje
idempotencji — **idempotencja komend obowiązuje domyślnie, jako prawo**, egzekwowane
JEDNYM generycznym testem per serwis (`IdempotentCommandsTest`: każda komenda 2× =
stan jak po 1×), a nie szablonowym scenariuszem per operacja. Scenariusz wyżej pinuje
to, co jest naprawdę per-operacyjne: **kontrakt ODPOWIEDZI** („already there" — UI wie,
że nic nowego nie powstało). Różna odpowiedź nie łamie idempotencji — jak PUT: pierwszy
raz 201, drugi 200, stan ten sam. `Given has saved` vs `When saves` to idiom Gherkina:
stan zastany vs badana akcja.

Specyfikacja = test: jak dokumentacja rozjedzie się z kodem, test świeci na czerwono.
Sztandarowy zabieg projektu: **ten sam scenariusz prowadzony przez kilka wejść** — raz przez
warstwę aplikacji (szybko), raz przez czarną skrzynkę HTTP, raz przez prawdziwe UI w przeglądarce.
W `microservice-security/specs/` leży 16 plików `.feature` — to najlepszy katalog „co ten serwis
umie".

---

## 5. Mapa systemu

> 🏷️ **Tagi:**
> **smak (flavour)** — framework, w którym zrealizowano dany serwis; **BCE (Boundary-Control-Entity)** — alternatywny podział na warstwy używany w microservice-email;
> **PWA** — strona instalowalna jak aplikacja mobilna; **SSE (Server-Sent Events)** — strumień zdarzeń z serwera do przeglądarki po HTTP.

### Serwisy

| Serwis | Smak | Port | Za co odpowiada |
|---|---|---|---|
| `microservice-security` | **Micronaut** (hexagonal, 6 warstw) | 8080 | Konta, logowanie, JWT, MFA, OAuth, sesje, saga usuwania konta |
| `microservice-email` | **Quarkus** (BCE, szablony Qute) | 8082 | Wysyłka maili (`POST /mails*`, X-Api-Key), konsument `mail-requests` |
| `microservice-memes` | **Spring Boot** (wielomodułowy, layered) | 8083 | Galeria memów: upload, miniatury, głosy, moderacja/NSFW, UI na `/` |
| `microservice-comments` | **Spring Boot** (jednomodułowy) | 8085 | Wątki komentarzy pod memami + głosy |
| `microservice-paddock` | **Javalin** (vertical slices, PWA) | 8086 | Hub społecznościowy: serwery gry, członkostwa, wydarzenia z RSVP |
| `formula-simulator` | **bez frameworka** (JDK HttpServer) | 8084 | Menedżer F1 z autonomicznymi kierowcami; SSE; OSOBNY PRODUKT poza reaktorem (buduje się standalone) — sekcja 16 |
| `microservice-user-collections` | **Helidon 4 SE** (virtual threads) | 8092 | Generyczne kolekcje referencji usera (ulubione); 3. uczestnik sagi |
| `collections-ui` | React Native Web + nginx | 8093 | UI ulubionych na WŁASNYM originie (celowo, dla ćwiczenia CORS) |
| `microservice-idp` | Python | 8091 | **Stub OIDC** — „Zaloguj przez Google" bez Google (dev/testy) |
| `microservice-image` | Python + Pillow | wewn. (8087) | Konwersja PNG→WebP dla memów |
| `microservice-sms` / `-push` | Python | wewn. (8088/8089) | Kanały powiadomień paddocka (stub-send) |
| `race-sim` | Python (stdlib) | wewn. (8090) | Moduł symulacji wyścigu formuły — **bez portu na hosta** (sekcja 16) |

### Biblioteki współdzielone (osobne repa, konsumowane głównie przez security)

| Biblioteka | Co daje |
|---|---|
| `test-starter` | Zestawy zależności testowych: `unit-`/`bdd-`/`system-test-starter` |
| `constraint` | Prymitywy walidacji/ograniczeń |
| `config` | Prymitywy konfiguracji (`PropertiesConfigPort`/`Source`) |
| `email` | Value objects adresu e-mail + email-security |
| `password` | Value objects hasła, algorytmy haszowania (**Argon2**), password-security |
| `adjustable-clock` (+ `infrastructure-micronaut-clock`) | Sterowalny zegar do testów + adapter Micronauta |
| `voting` | Bounded context głosowania jako libka (toggle + tally nad portem `Ballots`) — używa memes i comments |
| `offline-jwt` | **Nowość 2026-07-10:** wspólna weryfikacja JWT offline (sekcja 7) |

---

## 6. microservice-security w głąb

> 🏷️ **Tagi:**
> **JWT (JSON Web Token)** — podpisany „bilet" tożsamości noszony w nagłówku `Authorization: Bearer ...`; **EdDSA** — algorytm podpisu tokenów;
> **JWKS** — publiczne klucze pod `/.well-known/jwks.json`, którymi inni weryfikują podpis; **introspekcja** — pytanie security `/me` o token przy każdym żądaniu;
> **refresh token + reuse detection** — odnawianie sesji i wykrywanie kradzieży (użycie zużytego tokenu ubija całą rodzinę sesji);
> **brute-force lockout** — czasowa blokada źródła po serii nieudanych logowań; **anty-enumeracja** — odpowiedzi nie zdradzają, czy adres istnieje;
> **OAuth2 / OIDC** — logowanie przez zewnętrznego dostawcę; **PKCE** — zabezpieczenie wymiany kodu; **MFA** — dodatkowe czynniki logowania;
> **TOTP** — kody z aplikacji typu Authenticator; **WebAuthn / passkey** — logowanie podpisem klucza sprzętowego/telefonu; **recovery codes** — jednorazowe kody zapasowe;
> **step-up** — ponowne uwierzytelnienie przed wrażliwą operacją; **GDPR delete** — pełne usunięcie konta wraz z treściami.

Najbogatszy serwis — rdzeń tożsamości dla całej reszty. Katalog możliwości = `specs/*.feature`:
register, authenticate, verify-email, reset-password, change-email, change-password, logout,
list/revoke sessions, refresh + reuse-detection, federated-sign-in, authorize, mfa,
mfa-webauthn, delete-account.

Co trzeba rozumieć:

- **Tokeny.** Po zalogowaniu dostajesz JWT podpisany EdDSA (w claimach: kto, role,
  `mfaCompliant`). Inne serwisy weryfikują go **na dwa sposoby — celowo oba w stacku**:
  *introspekcja* (memes pyta `/me` — wolniej, ale natychmiast widzi unieważnienie) i *offline*
  (comments/paddock/formula/collections weryfikują podpis same przez JWKS — szybciej, ale ślepe
  na unieważnienie do `exp`). To świadomy pokaz trade-offu.
- **Logowanie jest bramkowane weryfikacją adresu** — świeże konto musi kliknąć link z maila.
  Link z maila otwiera **galerię** (`:8083/?verify=...`), która POST-uje token do security —
  celowo nie GET na API, bo prefetchery klientów pocztowych konsumowałyby tokeny.
- **OAuth/OIDC**: przycisk „Google"/„GitHub" w galerii. W dev oba wskazują **stub IdP** (:8091);
  produkcja podmienia tylko env-y (przepisy: `microservice-security/docs/oauth-providers.md`,
  łącznie z przetestowanym Keycloakiem). Dwa źródła tożsamości: ID_TOKEN (kształt Google)
  i USERINFO (kształt GitHub/Facebook). Konta federacyjne są bezhasłowe; linki federacyjne
  **podążają za kontem** przy zmianie adresu (`relinkAll`).
- **MFA — temat zamknięty w całości**: łańcuch czynników (hasło ALBO provider jako ogniwo #1),
  kody e-mail/SMS, TOTP, WebAuthn/passkeys, recovery codes jako czynnik alternatywny (batch
  pokazany raz, konsumpcja atomowa). **Step-up**: polityki per akcja (delete konta = FULL_CHAIN,
  zmiana hasła = SECOND_FACTORS); dialog usunięcia konta w galerii robi step-up naprawdę.
- **Podłoga MFA u konsumentów**: token niesie claim `mfaCompliant`; moderator/admin bez
  dopełnionego MFA jest w memes/comments traktowany jak zwykły USER (`Caller.withMfaFloor`) —
  fail-closed.
- **Higiena**: throttle rejestracji per IP (w compose podniesiony, żeby smoke nie wpadał),
  lockout po nieudanych logowaniach, zajęty adres przy zmianie e-maila odpowiada tak samo jak
  wolny (202 + notka mailem), `DeleteAccount` czyści sesje → czynniki → kody → linki federacyjne
  → user, i uruchamia sagę treści (sekcja 8).

---

## 7. offline-jwt — lekcja o duplikacji

> 🏷️ **Tagi:**
> **wspólna biblioteka vs kopiowanie kodu** — duplikacja zwykle wygrywa z couplingiem między serwisami, z wyjątkiem kodu krytycznego dla bezpieczeństwa;
> **dryf kopii** — kopie tego samego kodu rozjeżdżają się w czasie.

Weryfikacja JWT offline żyła jako **pięć identycznych kopii** (memes, comments, paddock,
user-collections, formula) z komentarzem „change one, change both". Konwergencja do libki
`offline-jwt` (2026-07-10) złapała **realny dryf**: kopia w memes zgubiła podłogę MFA — moderator
bez MFA zachowywał offline uprawnienia, które introspekcja by zdjęła. Stąd reguła projektu:
między serwisami duplikacja > coupling, **ale nie dla kodu security-critical**. Serwisy trzymają
własne polityki (np. `Caller.withMfaFloor`) — współdzielony jest tylko rdzeń weryfikacji.

```java
OfflineJwtVerifier verifier = OfflineJwtVerifier.overHttp(securityUrl, objectMapper);
Optional<VerifiedToken> caller = verifier.verify(bearerToken);  // empty = fail closed
```

---

## 8. Komunikacja między serwisami

> 🏷️ **Tagi:**
> **REST/HTTP synchroniczny** — wołasz i czekasz na odpowiedź; **Apache Kafka** — szyna zdarzeń (event backbone); **topic** — nazwany kanał zdarzeń;
> **transactional outbox** — tabela gwarantująca, że zmiana stanu i jej zdarzenie wyjdą razem; **poller** — wątek drenujący outbox do Kafki;
> **at-least-once + dedup** — dostawa „co najmniej raz", konsument deduplikuje; **DLQ (dead-letter queue)** — parking dla zdarzeń nie do przetworzenia, z redrive;
> **saga** — rozproszona sekwencja kroków z potwierdzeniami; **orkiestrator** — komponent pilnujący, kto już potwierdził;
> **correlation-id (cid)** — identyfikator podróżujący z żądaniem przez wszystkie serwisy (nagłówek HTTP i nagłówek Kafki).

Dwa sposoby, oba używane świadomie:

**a) Synchronicznie (HTTP)** — np. memes pyta security `/me` przy uploadzie. Proste; jak
odpytywany padnie, wołający czeka/degraduje.

**b) Asynchronicznie (Kafka)** — publikujesz zdarzenie na topic, konsumenci czytają w swoim
tempie. Topiki stacku: `mail-requests`, `content-commands`, `memes-events`, `comments-events`,
`usercollections-events`.

**Transactional outbox**: security zapisuje zdarzenie do tabeli `outbox_events` **w tej samej
transakcji** co zmianę stanu; poller wypycha je do Kafki. Dzięki temu zmiana i jej zdarzenie
nigdy się nie rozjadą. Outbox niesie też **cid** (kolumna V14) i **W3C `traceparent`** (V16) —
dlatego log i trace jednej operacji sklejają się przez granice asynchroniczne (sekcja 14).

**Saga usuwania konta** — sztandarowy przepływ:
1. security publikuje `PURGE_USER_CONTENT` na `content-commands`,
2. **trzej uczestnicy** sprzątają i potwierdzają (`USER_CONTENT_PURGED`): memes (wg
   `ContentPurgePolicy` — DELETE/ANONYMIZE/KEEP), comments (jw.), user-collections (wholesale,
   refy są nieprzezroczyste),
3. `AccountDeletionOrchestrator` czeka na **wszystkie** potwierdzenia i dopiero wtedy kasuje konto.

**Pułapka do zapamiętania:** każdy nowy serwis trzymający treści usera musi zostać dopisany jako
uczestnik sagi (tabela `saga_participants`), inaczej saga nigdy się nie domknie albo osieroci dane.

**ADR 0005 — dwa style integracji z email, celowo:** maile *należne* po zmianie stanu
(rejestracja, reset, saga) idą przez outbox/Kafkę (nie mogą zginąć); powiadomienia *best-effort*
paddocka (przypomnienie o evencie) idą synchronicznym fan-outem HTTP do email/sms/push (krótki
timeout, pusty URL wyłącza kanał). Nowa integracja wybiera **po regule zobowiązania**, nie przez
kopiowanie sąsiada.

**ADR 0004 — wersjonowanie zdarzeń:** każda koperta niesie `"version": 1`. W ramach wersji zmiany
**tylko addytywne** (pola, które ktoś czyta, nigdy nie znikają ani nie zmieniają typu); konsumenci
to **tolerant readers** (biorą swoje pola, resztę ignorują). Zmiana łamiąca = bump wersji +
expand/contract (stary kształt emitowany obok nowego, aż wszyscy przejdą).

---

## 9. Kontrakty między serwisami (CDC / Pact)

> 🏷️ **Tagi:**
> **CDC (consumer-driven contracts)** — konsument deklaruje, czego używa, producent to weryfikuje; **Pact** — standardowe narzędzie CDC;
> **pakt** — wygenerowany plik JSON z oczekiwaniami konsumenta; **Pact broker** — centralny serwer paktów (tu ZASTĄPIONY układem workspace);
> **provider state** — stan, który producent przygotowuje przed weryfikacją paktu HTTP; **tolerant reader** — konsument ignorujący nieznane pola.

Problem: producent mógł zmienić nazwę pola, które konsument czyta, i **oba buildy zostawały
zielone** — pękało dopiero w smoke teście całego stacku. Rozwiązanie (**ADR 0003**): Pact
w trybie **plikowym, bez brokera** — bo w workspace każdy konsument i producent i tak stoją obok
siebie:

- **Konsument** ma test paktowy, który karmi jego REALNY kod konsumujący payloadem paktu
  i deklaruje **tylko pola, które faktycznie czyta**. Wygenerowany pakt jest commitowany do
  `pacts/` w repo konsumenta (pakty HTTP osobno w `pacts-http/` — plik V3 nie miesza stylów).
- **Producent** (security) weryfikuje pakty na swoim REALNYM kodzie produkującym
  (`@PactFolder` wskazuje sibling-checkout `../../<konsument>/pacts`). Brak siblinga = **skip,
  nie fail** (standalone build się nie wywraca); CI zawsze checkoutuje i weryfikuje.
- Pokryte obie strony sagi (komendy purge ORAZ potwierdzenia — security bywa i producentem,
  i konsumentem), 6 kształtów maili, HTTP: JWKS (konsumentem jest libka `offline-jwt`!)
  i introspekcja `/me` (pakt memes, z provider state: register→verify→authenticate).

Efekt: łamiąca zmiana producenta robi się czerwona **w buildzie producenta**, z nazwą konsumenta
w komunikacie — nie w żywym stacku.

---

## 10. Przechowywanie danych

> 🏷️ **Tagi:**
> **PostgreSQL** — relacyjna baza danych; **database-per-service** — każdy serwis ma WŁASNĄ bazę, nikt nie grzebie w cudzej;
> **H2** — baza in-memory do dev/testów (tryb zgodności z Postgresem → jeden adapter); **Flyway** — wersjonowane migracje schematu plikami `V1__*.sql`;
> **MinIO / S3** — obiektowy magazyn na binaria (obrazki), lokalny serwer mówiący protokołem S3.

- Trwałe dane ma 6 serwisów i **każdy własnego Postgresa** (security, memes, comments, paddock,
  formula, collections).
- Bez `DB_URL` serwis wstaje na **H2 w pamięci** — tak biegną testy i szybki dev.
- Schemat wersjonuje **Flyway**: przy starcie serwis wykonuje brakujące migracje
  (`src/main/resources/db/migration/V*.sql`). W security migracje doszły do V16 — numery
  zobaczysz w todo jako „V13 recovery codes", „V14 cid w outboxie" itd.
- `memes` trzyma metadane w Postgresie, a **bajty obrazków w MinIO** (adapter `S3ObjectStore`
  działa i z MinIO, i z prawdziwym S3). Uwaga: memes **deduplikuje identyczne bajty** po hashu —
  dwa uploady tego samego PNG to jeden mem (raz ugryzło to testy e2e).

---

## 11. Uruchamianie całości: Docker i Compose

> 🏷️ **Tagi:**
> **Docker** — pakuje aplikację ze środowiskiem w izolowany kontener; **obraz vs kontener** — szablon vs działająca instancja; **Dockerfile** — przepis na obraz;
> **Docker Compose** — jeden plik YAML opisujący wszystkie kontenery, sieć i zmienne; **healthcheck** — sonda „czy serwis NAPRAWDĘ gotowy", na którą czekają zależni;
> **smoke test** — szybki test end-to-end „czy podstawy żyją"; **Mailpit** — fałszywy serwer SMTP z webowym podglądem maili.

Nie odpalasz Compose ręcznie — są skrypty w korzeniu workspace:

| Skrypt | Co robi |
|---|---|
| `./infra-up.sh` | Buduje jary na hoście, ściąga agenta OTel, podnosi cały stack |
| `./infra-smoke.sh` | Dowodzi przepływów end-to-end (rejestracja→mail→weryfikacja→logowanie→upload→saga usunięcia konta→CORS ulubionych) |
| `./infra-down.sh` | Zatrzymuje i sprząta (`-v` kasuje też wolumeny baz) |
| `./memes-up.sh` / `./formula-up.sh` | Wycinki stacku (galeria / formuła z dev drive-ui przez `docker-compose.dev.yml`) |

Wszystkie kontenery mają **healthchecki** (gołe sondy TCP — obrazy temurin/python nie mają curla),
a zależni czekają na `service_healthy` zamiast ścigać się ze startem Kafki/security.

**Porty, które warto znać na pamięć:**

| Port | Co |
|---|---|
| 8080 | security (API) |
| 8082 | email (API, X-Api-Key) |
| 8083 | **galeria memów** (UI; tu też przycisk Google i gwiazdki ulubionych) |
| 8084 | formula-simulator (viewer wyścigu) |
| 8085 / 8086 | comments / paddock (PWA) |
| 8091 | stub IdP (formularz „logowania Google") |
| 8092 / 8093 | user-collections (API) / collections-ui (UI ulubionych) |
| 8025 | **Mailpit** — skrzynka odbiorcza dev (tu lądują wszystkie maile) |
| 3000 / 9090 | Grafana / Prometheus |

Kafka (KRaft, single-node), MinIO, Loki, Tempo, Promtail, cAdvisor, node-exporter biegną
wewnątrz sieci compose. `race-sim` **celowo nie ma portu na hosta** (sekcja 16).

---

## 12. Frontendy

> 🏷️ **Tagi:**
> **React** — biblioteka UI z komponentów; **TypeScript** — JavaScript z typami; **Vite** — bundler/dev-server frontu;
> **Material UI** — gotowe komponenty (galeria); **React Native Web** — komponenty React Native renderowane w przeglądarce;
> **nginx** — serwer statycznych plików (serwuje collections-ui); **CORS** — mechanizm przeglądarki kontrolujący żądania między originami; **preflight** — próbne żądanie OPTIONS przed właściwym;
> **Expo / React Native** — aplikacja mobilna formuły; **PWA** — instalowalna strona paddocka.

- **security-ui** (w repo security) — React + TS (przepisane z Angulara; preferencja: React).
  Ekrany konta, MFA, sesji; rozcięte na komponenty prezentacyjne + stan w `App`.
- **memes-ui** (moduł memes) — React + TS + Material UI, budowane Vite przez
  frontend-maven-plugin i **pakowane do jara** (Spring serwuje pod `/`). Dev:
  `cd memes-ui && npm run dev`.
- **collections-ui** (w repo user-collections) — React Native **Web** + Vite, serwowane nginxem
  na **własnym originie :8093 celowo**: przeglądarka woła security i collections **cross-origin**,
  więc ćwiczymy prawdziwy CORS (ręczny `CorsFilter` w Helidonie, allowlista originów, preflight
  204). Gwiazdka na kafelku galerii zapisuje ulubione wprost do collections; ściana „Favourites"
  hydratuje refy, a skasowany mem pokazuje kafelek „unavailable" — **degradacja cicha**: gdy
  collections leży, galeria działa bez gwiazdek.
- **paddock** — PWA mobile-first serwowana przez sam serwis.
- **formula-simulator/app** — aplikacja mobilna (Expo/React Native); viewer wyścigu na :8084 to
  osobny, wbudowany front (SVG + SSE).

---

## 13. Testy — poziomy i narzędzia

> 🏷️ **Tagi:**
> **JUnit 5** — podstawowy framework testów (`@Test`); **piramida testów** — dużo szybkich jednostkowych, mniej integracyjnych, najmniej E2E;
> **Testcontainers** — prawdziwa baza/MinIO w Dockerze na czas testu; **RestAssured** — testy HTTP „po drucie";
> **cucumber-js + Playwright** — scenariusze Gherkina wykonywane w PRAWDZIWEJ przeglądarce (Chromium), z wirtualnym authenticatorem do passkeys;
> **Allure** — raporty z testów; **glosariusz UL** — generowany słownik języka wszechobecnego (ubiquitous language) z adnotacji testów.

Piętra (od dołu):
1. **Jednostkowe** (JUnit 5) — domain/config/system, sekundy.
2. **Use case'y przez Cucumbera** — scenariusze `.feature` na adapterach in-memory.
3. **Integracyjne/wire** (RestAssured, Testcontainers) — czarna skrzynka HTTP, prawdziwa baza.
4. **Kontraktowe (Pact)** — sekcja 9.
5. **E2E przez przeglądarkę** (cucumber-js + Playwright) — security-ui 36 scenariuszy, memes-ui,
   e2e galerii z ulubionymi; **te same specyfikacje Gherkina co niżej**, tylko wejście inne.
6. **Smoke żywego stacku** — `./infra-smoke.sh`.

Raporty i dokumentacja z testów:
- `aggregate_allure.py` + `allure-serve.sh` — zbiorczy raport Allure ze wszystkich projektów;
  `create-documentation.sh` generuje `Documentation.md` security z raportów.
- `build_glossary.py` + `glossary-serve.sh` — **interaktywny glosariusz** pojęć domenowych
  zbudowany ze źródeł testów (Allure: domain/config/system; Cucumber: system/app/infra/UI).
- `allure-summary.md` — bieżące podsumowanie pokrycia.

---

## 14. Observability — widzieć, co się dzieje

> 🏷️ **Tagi:**
> **Prometheus** — zbiera metryki (liczby: RPS, czasy, heap) przez scrape endpointów `/metrics`; **Grafana** — dashboardy nad wszystkimi źródłami;
> **cAdvisor / node-exporter** — metryki kontenerów i hosta bez zmian w kodzie; **Loki + Promtail** — baza logów + zbieracz stdout wszystkich kontenerów;
> **OpenTelemetry (OTel)** — standard telemetrii; **agent OTel** — doczepiany do JVM przez `JAVA_TOOL_OPTIONS`, instrumentuje HTTP i Kafkę bez zmiany kodu;
> **Tempo** — baza trace'ów; **trace / span** — oś czasu żądania przez wiele serwisów / jej odcinek; **alerting** — reguły Prometheusa (TargetDown, Http5xxBurst).

Trzy sygnały, wszystko w **Grafanie (http://localhost:3000)**:

- **Metryki**: Prometheus skrobie 10+ targetów (każdy serwis wystawia `/metrics` w swoim smaku:
  micrometer, actuator, quarkus-micrometer, ręczne endpointy w paddock/formula). Dashboardy:
  „Stack — kontenery", „Serwisy — HTTP, JVM i logi", „Stack — dostępność i zdrowie" + alerty.
- **Logi**: Promtail zbiera stdout **wszystkich** kontenerów przez gniazdo Dockera → Loki.
- **Trace'y**: agent OTel (**musi być 2.29.0+** — 2.11.0 ładuje się na JDK 25, ale nic nie
  instrumentuje) eksportuje spany do Tempo. Usunięcie konta = **jeden trace** przez security,
  memes, comments, collections i email, bo outbox przenosi `traceparent`.

**Nić przez wszystko — cid**: każde żądanie dostaje `X-Correlation-Id`, cid ląduje w każdej linii
logu, jedzie po HTTP i w nagłówkach Kafki. W Grafanie: `{service=~".+"} |= "<cid>"` pokazuje
całą podróż jednego żądania przez wszystkie serwisy; klik w `trace=<id>` w logu otwiera waterfall
w Tempo. **Gotcha z życia:** MDC nie przechodzi przez granice wątków/async — dlatego cid jest
wożony w kolumnie outboxa i atrybucie żądania, nie „w wątku".

---

## 15. CI — GitHub Actions

> 🏷️ **Tagi:**
> **CI (Continuous Integration)** — automatyczny build+testy przy każdym pushu; **GitHub Actions** — silnik CI GitHuba (pliki `.github/workflows/*.yml`);
> **independent deployability** — każdy serwis testowalny i wdrażalny osobno, stąd CI per repo.

Dwa poziomy:
- **Workspace** (`.github/workflows/ci.yml`): checkoutuje 13 sub-repo, buduje cały reaktor
  jednym `./mvnw clean install` **plus grę F1 osobnym krokiem** (osobny produkt poza
  reaktorem, bez własnego CI — workspace ją testuje standalone), a drugi job **e2e**
  prowadzi specyfikacje przez prawdziwe Chromium (oba UI, passkeys na wirtualnym
  authenticatorze).
- **Per repo** (od 2026-07-10 serwisy PORTALU mają własne `ci.yml`; gra nie ma): buduje serwis
  samodzielnie; CI security checkoutuje dodatkowo repa konsumentów, żeby weryfikacja paktów
  biegła też u producenta. Uwaga: repo prywatne wymaga PAT w sekretach (domyślny GITHUB_TOKEN
  czyta tylko publiczne repo właściciela).

---

## 16. formula-simulator — osobny świat

> 🏷️ **Tagi:**
> **JDK HttpServer** — serwer HTTP wbudowany w Javę, zero frameworka; **contract-first** — schematy JSON w `contracts/` jako umowa Java↔Python↔boty;
> **SSE** — transmisja wyścigu do widzów; **determinizm per seed** — ta sama symulacja przy tym samym ziarnie;
> **sandbox / egzamin botów** — user-supplied code uruchamiany w kontrolowanych warunkach; **defense in depth** — limit czasu + cache werdyktów + brak dostępu z sieci;
> **ery jako dane** — pakiety regulacji/fizyki jako pliki, nie kod.

Gra menedżerska F1 inspirowana Jagged Alliance 2 (kierowcy mają atrybuty i osobowość; historia
emerguje z symulacji). **Repo PRYWATNE**, ma własny, obszerny świat dokumentów —
**kanon: `formula-simulator/docs/zalozenia-projektu.md`**, plan rozbudowy:
`docs/expansion-plan.md`, backlog: `todo.md` (historia sesji: `todo-archiwum.md`).

- **Backend Java** (bez frameworka) jest autorytatywny: buduje żądanie symulacji, woła moduł
  Pythona, przechowuje timeline i replayuje klientom przez SSE — serwer wysyła STAN, nie piksele.
- **Moduł wyścigu** (`sim/race/`, Python stdlib): fizyka ticków, opony, dirty air, safety car,
  kontrolery kierowców za kontraktem `DriverController`.
- **Boty użytkowników**: gracze wgrywają własne kontrolery; przed dopuszczeniem bot przechodzi
  **egzamin**. Zabezpieczenia (świeże, 2026-07-10): twardy limit czasu egzaminu (wrogi bot nie
  weźmie egzaminatora jako zakładnika), werdykty keszowane po sha pliku (te same bajty nie mielą
  się dwa razy), a **race-sim nie ma portu na hosta** — silnik z sekretami żyje tylko w sieci
  wewnętrznej, świat rozmawia z nim wyłącznie przez bramkę gry z JWT.
- **Aktywny kierunek rozwoju**: „ery jako dane" — 6 pakietów er (CIGAR/WINGS/TURBO/V10/V8/HYBRID)
  z fizyką per podzespół (turbolag, ERS, fade hamulców, opony crossply/radial...), fazy F0–F9
  (F0 zrobiona) oraz **dwa niezależne mistrzostwa** na wspólnym silniku: branch agentów
  (benchmark AI) i branch użytkowników (persona JA2).
- Jazda manualna właściciela (drive-ui) wymaga `docker-compose.dev.yml` → `./formula-up.sh`.

---

## 17. Jak zacząć — pierwszy dzień

```bash
# 0. Wymagania: JDK 25, Docker, git. Mavena NIE instalujesz — jest ./mvnw.

cd ~/Documents/git/security
./mvnw clean install        # 1. cały reaktor PORTALU (pierwszy raz potrwa)
./mvnw -f formula-simulator/pom.xml package -DskipTests   # 1b. gra F1 (osobny produkt)
./infra-up.sh               # 2. cały stack w Dockerze
./infra-smoke.sh            # 3. dowód, że przepływy działają (zielony = OK)

# 4. Poklikaj w żywy system:
#    http://localhost:8083  galeria: załóż konto, kliknij link w Mailpicie (:8025),
#                           zaloguj się, wrzuć mema, daj gwiazdkę
#    http://localhost:8093  ściana ulubionych (cross-origin!)
#    http://localhost:3000  Grafana: metryki, logi, trace'y
./infra-down.sh             # 5. sprzątanie
```

Potem, w tej kolejności:
1. Przeczytaj `microservice-security/specs/*.feature` — katalog zachowań rdzenia.
2. Prześledź w `microservice-user-collections` układ domain → application → infrastructure —
   to najmniejszy i najczystszy przykład hexagonu.
3. Otwórz `docs/adr/` — pięć krótkich decyzji, które tłumaczą „dziwności".
4. Zrób jedno żądanie z własnym `X-Correlation-Id` i odszukaj je w Loki + Tempo.
5. Zmień coś małego w jednym serwisie, odpal jego testy w jego katalogu i zobacz w `pacts/`,
   czy nie ruszyłeś kontraktu.

## 18. Gdzie szukać odpowiedzi

| Pytanie | Miejsce |
|---|---|
| Jak serwisy się ze sobą łączą? | [`docs/c4-architecture.md`](c4-architecture.md) — diagramy C4 **generowane** z compose + paktów (`python3 build_c4.py`) |
| Co się ostatnio działo / co dalej? | `todo.md` (workspace) + `todo.md` sub-repo + `git log` sub-repo |
| Dlaczego tak zdecydowano? | `docs/adr/`, `formula-simulator/docs/zalozenia-projektu.md` |
| Co umie security? | `microservice-security/specs/`, `Readme.md`, `docs/mfa-design.md`, `docs/oauth-providers.md` |
| Jak podpiąć prawdziwego Google'a? | `microservice-security/docs/oauth-providers.md` |
| Plany wdrożeniowe (VPS, k3s)? | `docs/deployment-plan.md` |
| Słownik pojęć domenowych | `./glossary-serve.sh` (generowany z testów) |
| Komendy Mavena | `maven-cheatsheet.md` |

## 19. Słowniczek (minimum do rozmowy)

- **Hexagonal / porty i adaptery** — logika oddzielona od technologii; port = interfejs, adapter = implementacja.
- **Use case** — jedna operacja aplikacji jako klasa z `execute`.
- **JWT / JWKS** — podpisany token tożsamości / publiczne klucze do jego weryfikacji.
- **Introspekcja vs offline** — pytać security o token vs weryfikować podpis samemu.
- **MFA / TOTP / passkey / step-up** — dodatkowe czynniki logowania i ponowne uwierzytelnienie przed wrażliwą operacją.
- **Kafka / topic / event** — szyna zdarzeń; kanał; wiadomość.
- **Outbox** — zdarzenie zapisane w tej samej transakcji co zmiana stanu; poller wypycha do Kafki.
- **Saga** — rozproszona sekwencja z potwierdzeniami (usuwanie konta: 3 uczestników).
- **Pact / pakt / CDC** — konsument deklaruje pola, które czyta; producent weryfikuje to w swoim buildzie.
- **Tolerant reader** — konsument ignoruje nieznane pola (dlatego zmiany addytywne są bezpieczne).
- **Koperta z wersją** — każdy event niesie `version: 1`; łamiąca zmiana = bump + expand/contract.
- **Flyway / migracja** — wersjonowane zmiany schematu bazy plikami SQL.
- **Testcontainers** — prawdziwa baza w Dockerze na czas testu.
- **Gherkin / Cucumber / spec-first** — wykonywalne specyfikacje Given/When/Then, pisane przed kodem.
- **cid** — correlation-id; jeden identyfikator przez logi wszystkich serwisów.
- **Trace / span (OTel, Tempo)** — oś czasu żądania przez serwisy; agent instrumentuje JVM bez zmian w kodzie.
- **Healthcheck** — sonda gotowości kontenera; zależni czekają na `service_healthy`.
- **Smoke test** — szybki test end-to-end żywego stacku.
- **Reaktor** — Mavenowe budowanie wielu modułów w kolejności zależności.
- **Virtual threads (Loom)** — tanie wątki JVM (Helidon SE, formula).
- **CORS / preflight** — kontrola żądań między originami w przeglądarce (collections-ui ćwiczy to naprawdę).

---

Jak przeczytasz to od góry do dołu, wiesz **po co jest każdy klocek**. Najwięcej i tak zrozumiesz
z sekcji 17: odpal stack, klikaj i podglądaj w Grafanie, co się dzieje pod spodem.

---

# Aneks A — microservice-memes pod mikroskop (przygotowanie do rekrutacji)

Memy to serwis-wizytówka: publiczne repo, widowiskowe UI, a pod spodem komplet „dorosłych"
technik. Ten aneks daje Ci głębię na rozmowę: anatomię, **decyzje z uzasadnieniem** (rekruterzy
pytają „dlaczego", nie „co") i listę pytań, które prawdopodobnie padną.

## A.1 Anatomia — 7 modułów

> 🏷️ **Tagi:**
> **layered modules** — podział na moduły Mavena per warstwa (spokrewniony z hexagonem, mniej rozdrobniony niż w security);
> **frontend-maven-plugin** — Maven buduje front (własny przypięty Node) i pakuje do jara.

| Moduł | Zawartość | Technologie |
|---|---|---|
| `memes-domain` | Encje: `Meme`, `RankedMeme`, `VoteDirection` | czysta Java |
| `memes-config` | Typowane pokrętła: `ImageLimits`, `ThumbnailSize`, `ContentPurgePolicy`, `RateLimit`, `TagLimits` | czysta Java |
| `memes-image` | `WebImageOptimizer`: re-enkodowanie dowolnego obrazka do PNG w limitach | czysty JDK (`java.desktop`) |
| `memes-tags` | VO `Tag` (normalizacja: lowercase/trim, 2–30 znaków `[a-z0-9-]`) | czysta Java |
| `memes-application` | Use case'y (`PublishMeme`, `ServeMeme`, `MakeThumbnail`, `CastVote`, `RankMemes`, `TagMeme`, `SearchMemesByTag`, `FlagMeme`, `PurgeUserContent`) + porty (`MemeRepository`, `VoteRepository`, `MemeContentIndex`, `ObjectStore`, `ImageEncoder`, `PublicationLog`, `PurgePolicyOverride`) | bez frameworka |
| `memes-ui` | Galeria: React + TS + Material UI, Vite; dist w jarze jako `META-INF/resources` | frontend-maven-plugin |
| `memes-infrastructure` | Aplikacja Spring Boot: kontrolery, brama sign-in, adaptery JDBC/S3/HTTP, Flyway, Kafka | Spring Boot 3.5 |

**Puenta na rozmowę:** framework występuje w JEDNYM module z siedmiu. Domena, use case'y,
obróbka obrazu i tagi kompilują się i testują bez Springa — Spring to wymienialny detal
infrastruktury.

## A.2 Przepływ uploadu — bezpieczeństwo obrazków

> 🏷️ **Tagi:**
> **re-encoding** — obrazek jest dekodowany i kodowany od nowa (PNG), co niszczy metadane i ładunki w pliku;
> **EXIF stripping** — usunięcie metadanych (GPS!) z plików; **rate limiting** — 429 + `Retry-After`;
> **dedup po treści** — SHA-256 bajtów; **operacja atomowa / putIfAbsent** — wyścig rozstrzyga jedna operacja, nie „check-then-act".

1. `POST /memes` (multipart) wymaga Bearer tokena; autor = **potwierdzona tożsamość z security**,
   nigdy pole z requesta (niemożliwa impersonacja przez body).
2. **Rate limit** per uploader (domyślnie 12/min, env) → 429 + `Retry-After`.
3. `WebImageOptimizer` re-enkoduje wszystko, co czyta ImageIO (BMP, JPEG…), do PNG w limitach
   rozmiaru. **Efekt uboczny = feature bezpieczeństwa:** re-encoding gubi EXIF (jest test ze
   spreparowanym JPEG-iem z segmentem GPS — wchodzi z lokalizacją, wychodzi czysty) i unieważnia
   ewentualne ładunki doklejone do pliku.
4. **Dedup:** SHA-256 bajtów *po* optymalizacji; drugi upload tych samych bajtów zwraca
   istniejące id. Współbieżność rozwiązana **atomowym `claim(data, candidateId)`**
   (putIfAbsent w pamięci; w Postgresie wyścig rozstrzyga constraint PK) — zapis następuje
   dopiero PO wygranym claimie, więc nic osieroconego nie ląduje w bazie. Jest test z dwoma
   wątkami na jednej bramce.

## A.3 Gdzie żyją bajty — port `ObjectStore` z trzema adapterami

> 🏷️ **Tagi:**
> **port z wieloma adapterami** — podręcznikowy hexagon w praktyce; **bytea vs object storage** — blob w bazie vs S3;
> **path-style S3** — adresowanie kubełków zgodne z MinIO; **path traversal** — atak `../../` na adapter plikowy;
> **`@Primary` gotcha** — bezwarunkowy bean-domyślny potrafi zepsuć przełączanie adapterów.

Metadane zawsze w bazie; bajty za portem `ObjectStore` z adapterami wybieranymi env-em
`MEMES_BLOB_STORE`: `db` (tabela blobów — spójność transakcyjna z metadanymi), `filesystem`
(z testem ochrony przed path traversal) i `s3` (AWS SDK, path-style dla MinIO, bucket tworzony
idempotentnie na starcie; round-trip na żywym MinIO przez Testcontainers). W compose stack jedzie
na MinIO.

**Historia na rozmowę (real bug):** `DbObjectStore` był bezwarunkowo `@Primary`, więc
`memes.blob-store=filesystem` niczego nie przełączał — wyszło przy podpinaniu S3. Naprawa:
dokładnie jeden bean per tryb, **przypięte testem** `BlobStoreSelectionTest`. Drugi real bug:
`S3ObjectStore` z dwoma konstruktorami bez `@Autowired` = śmierć na starcie tylko w trybie s3.

## A.4 Serwowanie: WebP, miniatury, ranking

> 🏷️ **Tagi:**
> **content negotiation** — wybór formatu po nagłówku `Accept`; **degradacja jakości, nie dostępności** — padnięty enkoder = PNG zamiast błędu;
> **cache-once** — WebP kodowany raz i trzymany w ObjectStore; **hot ranking z decay** — świeżość ważona wiekiem (wzór Reddit-like).

- `ServeMeme` negocjuje po `Accept: image/webp`: WebP kodowany **raz** przez osobny mikroserwis
  `microservice-image` (Python + Pillow) i cache'owany w ObjectStore pod `{id}.webp`; enkoder
  padł → serwujemy PNG (**degradacja jakości, nie dostępności**). Zmierzone: PNG 1790 B → WebP 900 B.
- Miniatury generowane **na żądanie** (`MakeThumbnail`), nie przy uploadzie.
- Ranking hot: `score / (ageHours+2)^1.5` — decay tylko **porządkuje**, zwracany score bez zmian,
  więc kontrakt `GET /memes/hot` nie drgnął. Czas publikacji zna port `PublicationLog`
  (mem nieznany = świeży, fail-safe); czas płynie przez bean `java.time.Clock` (testowalność).
- Głosy: semantyka z libki `voting` — jeden głos na usera per mem/komentarz, ponowny głos
  **zastępuje** poprzedni (nigdy nie kumuluje).

## A.5 Integracja z security — dwie bramy i podłoga MFA

> 🏷️ **Tagi:**
> **RequireSignInFilter / brama** — jedno miejsce egzekwujące tożsamość; **introspekcja vs offline JWT** — trade-off świeżości unieważnienia vs hop sieciowy;
> **RBAC** — role MODERATOR/ADMIN z security; **MFA floor** — zdjęcie ról uprzywilejowanych, gdy `mfaCompliant=false`, fail-closed.

- Odczyty publiczne; każdy zapis wymaga `Authorization: Bearer` — inaczej
  `401 {"status":"SIGN_IN_REQUIRED"}`.
- Brama ma **dwie implementacje**: introspekcja (`GET /me` — natychmiast widzi logout; tak jedzie
  compose) i offline JWT po JWKS (mniej ruchu, ślepa na unieważnienie do `exp`). Umiesz
  opowiedzieć ten trade-off = duży plus na rozmowie.
- Moderacja: MODERATOR/ADMIN kasuje cudze memy i flaguje **NSFW** (blur w galerii; `FlagMeme`,
  tabela V3). Panel admina ustawia default polityki czystki (`/admin/purge-policy`, rola ADMIN).
- **Podłoga MFA:** uprzywilejowany bez dopełnionego MFA jest w bramie ścinany do USER
  (`Caller.withMfaFloor`, fail-closed). Anegdota do opowiedzenia: kopia offline gate'a **zgubiła
  kiedyś ten floor** i wykryła to dopiero konwergencja pięciu kopii do libki `offline-jwt` +
  test regresyjny — argument, czemu kod security-critical nie może żyć w kopiach.

## A.6 Memy w sadze i kaskadzie zdarzeń

> 🏷️ **Tagi:**
> **uczestnik sagi** — memes konsumuje `PURGE_USER_CONTENT`, potwierdza na `memes-events`;
> **polityka per oś** — los treści jako reguła DELETE / ANONYMIZE_AUTHOR / KEEP_POPULAR_ANONYMIZED:n;
> **precedens rozstrzygania** — wybór usera z wizarda > ustawienie w bazie (`settings`, V4) > env; **fail-safe** — niesparsowana reguła = default, saga nigdy się nie klinuje;
> **kaskada zdarzeń** — skasowany mem ogłasza `MEME_DELETED`, comments kasuje wątek.

Przy usunięciu konta memes decyduje o losie treści odchodzącego **regułą per oś**:
`DELETE` (mem znika z wątkiem i głosami), `ANONYMIZE_AUTHOR` („deleted account"),
`KEEP_POPULAR_ANONYMIZED:n` (score ≥ n przeżywa zanonimizowany — „społeczność na to zapracowała").
Głosy odchodzącego są wycofywane **zawsze** — dane kluczowane tożsamością nie mają furtki
w polityce (argument GDPR). Wybór usera z wizarda usuwania (niesiony w komendzie sagi) nadpisuje
default. Potwierdzenie idzie na `memes-events` z cid i `version: 1`.

Osobno: skasowanie pojedynczego mema publikuje `MEME_DELETED`, na co `microservice-comments`
kasuje wątek — przykład, czemu komentarze wydzielono do osobnego serwisu (inny cykl życia,
własna baza), a spójność między nimi jest **ostateczna** (eventual), nie transakcyjna.

## A.7 Jak memes dowodzi, że działa

> 🏷️ **Tagi:**
> **testy per warstwa** — unit (image/config/application), MockMvc, Cucumber czarna skrzynka HTTP, e2e Playwright;
> **pakty konsumenckie** — memes deklaruje, co czyta z komendy sagi (`pacts/`) i z `GET /me` (`pacts-http/`);
> **Testcontainers** — round-trip S3 na żywym MinIO; **Allure + glosariusz UL** — dokumentacja generowana z testów.

- Feature'y Cucumbera (`upload`, `vote`, `tag-meme`, `moderate-meme`, `admin-purge-policy`) to
  **żywy kontrakt** — czarna skrzynka po HTTP, zielone w każdym buildzie.
- Suita przeglądarkowa (cucumber-js + Playwright) klika galerię na realnym trio
  security+memes+comments; konta i seed przez API, UI tylko w to, o czym scenariusz.
- **Pakty:** memes jako konsument deklaruje pola, które czyta z komendy purge (pakt message
  w `pacts/`) i z introspekcji `/me` (pakt HTTP w `pacts-http/` — osobny katalog, bo plik Pact V3
  nie miesza stylów); security weryfikuje oba na realnym kodzie.
- Metryki `/actuator/prometheus`, trace'y przez agenta OTel, logi z cid — upload widać w Tempo
  jako trace `[memes, security]`.

## A.8 Pytania, które prawdopodobnie usłyszysz (z sednem odpowiedzi)

1. **„Czemu Spring Boot tutaj, skoro gdzie indziej Micronaut?"** — Portfolio celowo realizuje ten
   sam wzorzec w wielu frameworkach; memes pokazuje, że warstwy nie zależą od frameworka
   (Spring żyje w 1 module z 7).
2. **„Co się dzieje, gdy dwóch userów wgra ten sam obrazek jednocześnie?"** — Dedup po SHA-256
   z atomowym claim; w bazie rozstrzyga constraint, zapis po wygranym claimie; test z dwoma wątkami.
3. **„Gdzie trzymasz obrazki i czemu?"** — Port ObjectStore, 3 adaptery (db/filesystem/S3);
   metadane zawsze w Postgresie; wybór to kwestia wdrożenia, nie kodu. Bonus: historia buga z `@Primary`.
4. **„Jak chronisz się przed złośliwym uploadem?"** — Re-encoding do PNG (niszczy EXIF i ładunki,
   test z GPS-em w JPEG), limity rozmiaru, rate limit 429, moderacja NSFW, autor z tokenu nie z body.
5. **„Skąd wiesz, że nie zepsułeś integracji z security?"** — Pakty: łamiąca zmiana robi się
   czerwona w buildzie producenta z nazwą konsumenta; do tego e2e i smoke żywego stacku.
6. **„Introspekcja czy weryfikacja offline?"** — Mam obie i umiem wskazać kiedy którą: introspekcja
   = świeżość unieważnienia, offline = brak hopa i odporność na awarię security; offline wymaga
   przeniesienia werdyktów do claimów (`mfaCompliant`).
7. **„Co się dzieje z treściami po usunięciu konta?"** — Saga z potwierdzeniami; polityka per oś
   z precedensem wizard > baza > env; głosy zawsze wycofane; niesparsowana reguła nie klinuje sagi.
8. **„Jak byś to skalował?"** — Bajty już za portem (S3), WebP zmniejsza transfer, miniatury on
   demand, ranking liczony z decay bez zmiany kontraktu; odczyty publiczne = łatwy cache/CDN przed serwisem.
9. **„Czemu komentarze to osobny serwis?"** — Inny cykl życia i model danych; spójność ostateczna
   przez `MEME_DELETED`; wspólna semantyka głosów wyciągnięta do libki `voting` zamiast kopii.
10. **„Jak testujesz?"** — Piramida z tym samym scenariuszem na kilku wejściach: unit → use case'y →
    MockMvc/HTTP czarna skrzynka → pakty → Playwright w przeglądarce → smoke stacku.

**Przed rozmowami przećwicz demo (15 minut):** `./infra-up.sh` → rejestracja → link w Mailpicie →
upload → gwiazdka (cross-origin do collections) → flaga NSFW moderatorem → usunięcie konta
wizardem → pokaż w Tempo JEDEN trace sagi przez 5 serwisów. To robi większe wrażenie niż slajdy.
