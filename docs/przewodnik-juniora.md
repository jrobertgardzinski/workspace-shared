# Przewodnik po projekcie — dla juniora, krok po kroku

Ten dokument prowadzi Cię za rękę przez cały projekt: **co to jest**, **jakie narzędzia go
budują**, **za co każde odpowiada** i **jak to wszystko uruchomić**. Czytaj po kolei — każda sekcja
zakłada, że rozumiesz poprzednią. Żargon tłumaczę na bieżąco, a na końcu jest słowniczek.

---

## 1. Czym w ogóle jest ten projekt?

To **nie jest jeden program**. To **portfolio mikroserwisów** — kilka niezależnych serwisów, które
razem tworzą backend gry/serwisu społecznościowego. Kluczowa idea:

> **Ta sama domena (konta, treści, głosy, komentarze) zbudowana w RÓŻNYCH frameworkach.**

Po co tak? Bo to portfolio — pokazuje, że tę samą architekturę (hexagonalną, o niej za chwilę)
umiesz zrealizować w Micronaucie, Springu, Quarkusie, Javalinie, Helidonie i „gołym" Javie. Każdy
serwis to osobne repozytorium git, z własną historią i własnym `pom.xml`.

**Workspace (repo `security`)** to „spinacz" — sam nie zawiera kodu serwisów (są gitignorowane),
tylko:
- `pom.xml` agregatora (buduje wszystkie serwisy w jednej komendzie),
- `docker-compose.yml` (uruchamia całość lokalnie),
- skrypty (`infra-up.sh`, `infra-smoke.sh`),
- dokumentację (m.in. ten plik).

### Mapa serwisów

| Serwis | Framework („smak") | Język | Port | Za co odpowiada |
|---|---|---|---|---|
| `microservice-security` | **Micronaut** | Java | 8080 | Konta, logowanie, JWT, MFA, OAuth, saga usuwania konta |
| `microservice-email` | **Quarkus** | Java | 8082 | Wysyłka maili (weryfikacja, reset hasła, kody) |
| `microservice-memes` | **Spring Boot** (wielomodułowy) | Java | 8083 | Galeria memów, upload, głosowanie, moderacja |
| `microservice-comments` | **Spring Boot** (jednomodułowy) | Java | 8085 | Komentarze pod memami + głosy |
| `microservice-paddock` | **Javalin** | Java | 8086 | Hub społecznościowy: serwery, ludzie, wydarzenia (PWA) |
| `formula-simulator` | **brak frameworka** (JDK HttpServer) | Java | 8084 | Menedżer F1: sterowanie wyścigiem, SSE |
| `microservice-user-collections` | **Helidon 4 SE** (virtual threads) | Java | 8092 | Kolekcje referencji użytkownika (ulubione, zapisane) |
| `microservice-idp` | (Python) | Python | 8091 | Stub „Zaloguj przez Google" do dev/testów |
| `microservice-image` | (Python + Pillow) | Python | 8087 | Konwersja PNG→WebP dla memów (wewnętrzny) |
| `microservice-sms` / `push` | (Python) | Python | 8088/8089 | Kanały powiadomień (wewnętrzne, stub) |
| `race-sim` | (Python) | Python | 8090 | Prototyp symulacji wyścigu dla formula |

Do tego **biblioteki współdzielone** (osobne repa): `password`, `email` (libka walidacji),
`constraint`, `config`, `voting`, `test-starter`, `adjustable-clock` — używane głównie przez
`microservice-security`.

---

## 2. Fundament: język i budowanie

### JDK 25 (Java)
Cały backend to Java w wersji **25**. JDK = Java Development Kit, czyli kompilator (`javac`) +
maszyna wirtualna (`java`) + biblioteki standardowe. Wersja 25 daje m.in. **virtual threads**
(wątki wirtualne z projektu Loom — tanie wątki, których możesz mieć miliony; wykorzystuje je
`user-collections` na Helidonie).

### Maven — narzędzie do budowania
**Maven** kompiluje kod, ściąga biblioteki (zależności), uruchamia testy i pakuje aplikację do
pliku `.jar`. Konfiguracja siedzi w plikach **`pom.xml`** (Project Object Model). Każdy `pom.xml`
mówi: „nazywam się tak, zależę od tych bibliotek, buduj mnie tak".

- **`./mvnw`** to „Maven Wrapper" — skrypt, który sam ściąga właściwą wersję Mavena (3.9.9), żebyś
  nie musiał jej instalować ręcznie. Zawsze używaj `./mvnw`, nie systemowego `mvn`.
- **Reaktor / agregator**: `pom.xml` w workspace wypisuje wszystkie serwisy jako `<module>`.
  Komenda `./mvnw clean install` w katalogu workspace buduje je **wszystkie naraz, w kolejności
  zależności**. „Reaktor" to Mavenowa nazwa na budowanie wielu modułów razem.

Najważniejsze komendy Mavena (co robią):
| Komenda | Co robi |
|---|---|
| `./mvnw clean` | Kasuje katalog `target/` (wyniki poprzedniego buildu) |
| `./mvnw compile` | Kompiluje kod (bez testów) |
| `./mvnw test` | Kompiluje + uruchamia testy jednostkowe |
| `./mvnw verify` | test + testy integracyjne |
| `./mvnw package` | Buduje plik `.jar` |
| `./mvnw install` | Pakuje i wrzuca do lokalnego repozytorium `~/.m2` (żeby inne moduły mogły użyć) |
| flaga `-pl X -am` | Buduj tylko moduł X i to, od czego zależy (`-am` = „also make") |
| flaga `-DskipTests` | Pomiń testy (szybciej, gdy chcesz tylko zbudować) |

---

## 3. Uruchamianie całości: Docker i Compose

### Docker
**Docker** pakuje aplikację razem z jej środowiskiem (Java, biblioteki, ustawienia) w **kontener** —
lekką, izolowaną „paczkę", która działa tak samo na każdym komputerze. Obraz (image) to szablon,
kontener to działająca instancja obrazu. Każdy serwis ma swój **`Dockerfile`** — przepis na obraz.

### Docker Compose
Jeden serwis to za mało — potrzebujesz bazy, Kafki, poczty itd. **`docker-compose.yml`** opisuje
**wszystkie** kontenery i jak się łączą (sieć, porty, zmienne środowiskowe). Jedną komendą
podnosisz cały stack.

W tym projekcie nie uruchamiasz Compose'a ręcznie — są skrypty:
| Skrypt | Co robi |
|---|---|
| **`./infra-up.sh`** | Buduje jary serwisów + podnosi cały stack w Dockerze (i ściąga agenta OTel) |
| **`./infra-down.sh`** | Zatrzymuje i sprząta stack |
| **`./infra-smoke.sh`** | „Smoke test" — sprawdza, że kluczowe przepływy działają end-to-end (rejestracja→mail→logowanie→upload→usunięcie konta) |

„Smoke test" = szybki test „czy się w ogóle dymi", czyli czy podstawy żyją po uruchomieniu.

---

## 4. Przechowywanie danych

### PostgreSQL
**Postgres** to relacyjna baza danych (tabele, SQL). Każdy serwis, który trzyma dane trwale, ma
**własną** bazę Postgresa (security, memes, comments, paddock, formula, user-collections) — to
zasada mikroserwisów: **każdy pilnuje swoich danych**, nikt nie grzebie w cudzej bazie.

### H2
**H2** to baza „w pamięci" (in-memory) — znika po wyłączeniu programu. Używamy jej w **dev i
testach**, gdy nie ustawisz zmiennej `DB_URL`. Sztuczka: H2 pracuje w „trybie PostgreSQL", więc te
same zapytania SQL działają w testach (H2) i na produkcji (Postgres) — **jeden adapter, żadnego
drugiego kodu do utrzymania**.

### Flyway — migracje bazy
Schemat bazy (tabele) zmienia się w czasie. **Flyway** wersjonuje te zmiany plikami SQL:
`V1__cos.sql`, `V2__cos_innego.sql`. Przy starcie serwis odpala Flyway, który wykonuje brakujące
migracje po kolei. Dzięki temu każda baza (dev, test, prod) ma **identyczny, powtarzalny schemat**.
Przykład: `microservice-user-collections/src/main/resources/db/migration/V1__collection_items.sql`.

### MinIO / S3
**Obiektowa** pamięć na pliki (nie tabele — całe binaria, np. obrazki memów). **S3** to standard
Amazona; **MinIO** to serwer mówiący tym samym protokołem, który stawiamy lokalnie. `memes` trzyma
metadane w Postgresie, a **bajty obrazków w MinIO**. Adapter `S3ObjectStore` obsługuje i MinIO, i
prawdziwe S3 — bez zmiany kodu.

---

## 5. Komunikacja między serwisami

Serwisy gadają ze sobą na dwa sposoby:

### a) Synchronicznie — HTTP/REST
Jeden serwis woła drugi po HTTP i **czeka na odpowiedź**. Przykład: `memes` przy uploadzie pyta
`security` „kim jest ten user?" (endpoint `/me`) i czeka na wynik. Proste, ale jak `security`
padnie, `memes` też się zablokuje na tym wywołaniu.

### b) Asynchronicznie — Apache Kafka
**Kafka** to „szyna zdarzeń" (event backbone). Serwis **publikuje zdarzenie** na „temat" (topic),
a inne serwisy je **konsumują**, każdy w swoim tempie. Nadawca nie czeka. Przykłady:
- security publikuje „wyślij maila" → email konsumuje i wysyła,
- security publikuje „usuń treści usera X" → memes i comments konsumują i sprzątają.

Kafka daje **luźne powiązanie** (nadawca nie wie, kto słucha) i **odporność** (gdy konsument
chwilowo padnie, zdarzenie na niego poczeka).

### Transactional Outbox — dlaczego jest potrzebny
Problem: security musi **zapisać do bazy** (np. „konto skasowane") **i** wysłać zdarzenie na Kafkę.
Jak baza się zapisze, a Kafka padnie między jednym a drugim — zdarzenie zginie. Rozwiązanie
**outbox**: zdarzenie zapisujesz do specjalnej tabeli `outbox_events` **w tej samej transakcji** co
zmianę stanu. Osobny wątek („poller") czyta outbox i wypycha na Kafkę, oznaczając wysłane. Gwarancja:
**zmiana i jej zdarzenie nigdy się nie rozjadą** (dostawa „co najmniej raz").

### Saga usuwania konta — przykład złożonego przepływu
Gdy user kasuje konto, dane ma w kilku serwisach. **Saga** to rozproszona sekwencja kroków:
1. security zapisuje „usuwam konto" i publikuje `PURGE_USER_CONTENT` na Kafkę,
2. memes kasuje memy usera → potwierdza (`USER_CONTENT_PURGED`),
3. comments kasuje/anonimizuje komentarze → potwierdza,
4. security czeka na **wszystkie** potwierdzenia i dopiero wtedy kasuje samo konto.

`AccountDeletionOrchestrator` w security pilnuje, kto już potwierdził. **Ważne:** gdy dojdzie nowy
serwis z treściami usera (np. `user-collections`), trzeba go dopisać jako **kolejnego uczestnika
sagi**, bo inaczej saga będzie czekać w nieskończoność albo zostawi osierocone dane.

---

## 6. Poczta

### microservice-email + Mailpit
`microservice-email` (Quarkus) wysyła maile (weryfikacja adresu, reset hasła, kody logowania).
W dev nie wysyłamy prawdziwych maili — przechwytuje je **Mailpit**, czyli fałszywy serwer SMTP z
podglądem w przeglądarce (**http://localhost:8025**). Rejestrujesz konto → wchodzisz na Mailpit →
widzisz maila z linkiem/kodem. Testy i smoke czytają Mailpit przez jego API.

---

## 7. Bezpieczeństwo i logowanie (microservice-security)

To najbogatszy serwis. Pojęcia, które musisz znać:

- **JWT (JSON Web Token)** — podpisany cyfrowo „bilet", który dostajesz po zalogowaniu i pokazujesz
  przy każdym żądaniu (nagłówek `Authorization: Bearer <token>`). Zawiera kim jesteś i jakie masz
  role. Podpis (EdDSA) gwarantuje, że nikt go nie podrobił.
- **JWKS** — publiczne klucze security wystawia pod adresem JWKS. Inne serwisy pobierają je i
  **same weryfikują podpis** JWT bez pytania security (tryb „offline", tak robi comments/paddock).
  Alternatywa: „introspekcja" — pytać security `/me` przy każdym żądaniu (tak robi memes; wolniej,
  ale natychmiast wie o wylogowaniu).
- **OAuth / OIDC** — „Zaloguj przez Google/GitHub". Standard, w którym zewnętrzny dostawca
  potwierdza tożsamość. W dev używamy **stub-a** (`microservice-idp`), żeby nie potrzebować
  prawdziwego Google.
- **MFA (uwierzytelnianie wieloskładnikowe)** — poza hasłem drugi czynnik: kod z maila, TOTP
  (aplikacja typu Google Authenticator), albo **WebAuthn / passkey** (podpis z klucza w telefonie/
  laptopie, bez hasła). Kody odzyskiwania to zapasowy czynnik.
- **Step-up** — podniesienie uprawnień przed wrażliwą operacją (np. przed usunięciem konta system
  każe potwierdzić hasło jeszcze raz).

---

## 8. Architektura: hexagonal (porty i adaptery)

To najważniejszy wzorzec w tym projekcie. Wyobraź sobie kod w **trzech warstwach**:

```
   infrastructure (adaptery)          <- świat zewnętrzny: HTTP, baza, Kafka
        |  wywołuje
   application (use case'y)           <- logika: "co robi aplikacja"
        |  używa
   domain (model + reguły)            <- czyste reguły biznesowe, zero technologii
```

- **domain** — czysty model (np. `ItemRef`, `Comment`), bez ani jednej linijki o bazie czy HTTP.
- **application** — **use case'y** („przypadki użycia"), np. `SaveItem`, `DeleteComment`. Klasa z
  metodą `execute(...)` zwracającą wynik. Zależy tylko od **portów** (interfejsów).
- **port** — interfejs opisujący, czego use case potrzebuje od świata (np. `CollectionStore` —
  „umiem zapisać/usunąć/wypisać"). Nie mówi JAK.
- **adapter** (w infrastructure) — konkretna implementacja portu: `JdbcCollectionStore` (baza),
  `InMemoryCollectionStore` (pamięć, do testów). Zamieniasz adapter → zmieniasz technologię bez
  ruszania logiki.

**Po co to?** Logika biznesowa jest **odseparowana od technologii**. Testujesz use case'y na
adapterze in-memory (szybko, bez bazy), a na produkcji wpinasz JDBC. To jest sedno „hexagonal".

### Spec-first / Gherkin
Zanim napiszemy kod, opisujemy zachowanie w **Gherkinie** — języku „Given/When/Then" czytelnym dla
człowieka (pliki `.feature`). Potem **Cucumber** (o nim niżej) wykonuje te scenariusze jako testy.
Przykład z `user-collections`:
```gherkin
Scenario: Saving the same reference twice is idempotent
  Given alice has saved meme 42 into "favourites"
  When alice saves meme 42 into "favourites"
  Then the save reports it was already there
```
Zaleta: **specyfikacja = test**. Dokumentacja nigdy się nie rozjedzie z kodem, bo jak się rozjedzie,
test świeci na czerwono.

---

## 9. Testy — czym się je pisze

| Narzędzie | Za co odpowiada |
|---|---|
| **JUnit 5** | Podstawowy framework testów jednostkowych (`@Test`, asercje) |
| **Cucumber** | Wykonuje scenariusze Gherkina (`.feature`) jako testy — „glue" (kroki) w Javie łączy zdania z kodem |
| **Testcontainers** | Odpala **prawdziwą** bazę/MinIO w Dockerze na czas testu (np. test S3 na realnym MinIO) — wymaga Dockera |
| **RestAssured** | Testy HTTP „po drucie" — wysyła prawdziwe żądania i sprawdza odpowiedzi |
| **Playwright** | Testy E2E przez **prawdziwą przeglądarkę** (klika w UI jak człowiek), sterowany przez cucumber-js |
| **Allure** | Ładne raporty z testów (agregowane przez `aggregate_allure.py`) |

**Piramida testów**: dużo szybkich jednostkowych (domain/application), mniej integracyjnych
(HTTP/baza), najmniej wolnych E2E (przeglądarka). Ten sam scenariusz Gherkina bywa uruchamiany na
kilku poziomach (logika / HTTP / UI).

---

## 10. Frontend (interfejsy użytkownika)

| Narzędzie | Za co odpowiada |
|---|---|
| **React** | Biblioteka do budowania UI z komponentów |
| **TypeScript** | JavaScript z typami — łapie błędy zanim odpalisz |
| **Vite** | Szybki bundler/dev-server dla frontu (buduje `security-ui`, `memes-ui`) |
| **Material UI** | Gotowe komponenty graficzne (przyciski, dialogi) dla galerii memów |
| **Expo / React Native** | Aplikacja mobilna (`formula-simulator/app`) — jeden kod na iOS/Android |
| **PWA** | „Progressive Web App" — strona, którą da się zainstalować jak apkę (paddock) |

Zbudowany front jest **pakowany do jara** serwisu (np. `memes-ui` → serwowane przez Spring pod `/`).

---

## 11. Observability — „widzieć, co się dzieje"

Gdy masz kilkanaście serwisów, musisz **widzieć**, jak działają. Trzy filary:

### a) Metryki — Prometheus + Grafana
- **Prometheus** co chwilę „skrobie" (scrape) z każdego serwisu liczby: ile żądań, jak szybko, ile
  pamięci. Trzyma je jako szeregi czasowe. Endpoint metryk to np. `/actuator/prometheus`.
- **Grafana** (**http://localhost:3000**) rysuje z tego wykresy i dashboardy.
- **cAdvisor** i **node-exporter** dokładają metryki kontenerów i hosta (CPU, RAM) bez zmiany kodu.

### b) Logi — Loki + Promtail
- **Loki** to „baza logów" (jak Prometheus, ale dla tekstu logów).
- **Promtail** zbiera logi ze **wszystkich** kontenerów (przez gniazdo Dockera) i wysyła do Loki.
- W Grafanie robisz zapytanie `{service="email"} |= "abc123"` i widzisz logi wszystkich serwisów
  naraz, przefiltrowane.

### c) Traces (ślady) — OpenTelemetry + Tempo
- **Trace** to „oś czasu" pojedynczego żądania **przez wiele serwisów** — widzisz, że upload zajął
  200 ms, z czego 50 ms to wywołanie security.
- **Agent OpenTelemetry** doczepia się do serwisu JVM przez `JAVA_TOOL_OPTIONS` (bez zmiany kodu!)
  i wysyła „spany" (odcinki) do **Tempo**. Grafana rysuje z nich wodospad.
- **Uwaga praktyczna:** agent musi być w wersji **2.29.0+**, bo starszy (2.11.0) ładuje się na
  JDK 25, ale nic nie instrumentuje.

### Correlation-id (cid) — nić przez wszystko
Każde żądanie dostaje **identyfikator korelacji** (`cid`) na wejściu (nagłówek `X-Correlation-Id`).
Ten cid:
- ląduje w **każdej linijce logu** (`[cid=abc123]`),
- jest **przekazywany dalej** — po HTTP do innych serwisów **i w nagłówku Kafki** przez sagę/outbox.

Dzięki temu w Loki wpisujesz jeden cid i widzisz **całą podróż** żądania przez wszystkie serwisy.
(Ciekawostka: żeby cid przetrwał outbox, jest zapisywany w kolumnie tabeli outbox i wysyłany jako
nagłówek Kafki przy drenażu — bo MDC/wątek się gubi na granicy asynchronicznej.)

---

## 12. CI — automatyczne testy przy każdym push

**CI (Continuous Integration)** = serwer, który **przy każdym wypchnięciu kodu** buduje projekt i
uruchamia testy. Tu robi to **GitHub Actions** (pliki `.github/workflows/ci.yml`). Workflow w
workspace checkoutuje wszystkie sub-repa i odpala `./mvnw clean install` — jak nie przejdzie,
dostajesz czerwony znaczek i wiesz, że coś zepsułeś, **zanim** trafi to dalej.

---

## 13. Jak zacząć — krok po kroku

```bash
# 0. Wymagania: JDK 25, Docker, git. (Maven NIE musisz mieć — jest wrapper ./mvnw.)

# 1. Zbuduj cały backend (reaktor zbuduje wszystkie serwisy w kolejności):
cd ~/Documents/git/security
./mvnw clean install            # pierwszy raz potrwa (ściąga zależności)

# 2. Podnieś cały stack w Dockerze (bazy, Kafka, serwisy, observability):
./infra-up.sh                   # poczekaj aż wszystko wstanie

# 3. Sprawdź, że kluczowe przepływy działają:
./infra-smoke.sh                # powinno przejść na zielono

# 4. Zajrzyj do środka:
#    Grafana (metryki/logi/trace): http://localhost:3000
#    Mailpit (podgląd maili):       http://localhost:8025
#    Galeria memów:                 http://localhost:8083
#    Prometheus:                    http://localhost:9090

# 5. Gdy skończysz:
./infra-down.sh
```

Chcesz zbudować/przetestować **jeden** serwis (szybciej)? Wejdź w jego katalog:
```bash
cd microservice-user-collections
mvn test                        # ten serwis jest samodzielny (Helidon, własny parent)
```
(`microservice-security` zależy od bibliotek-sióstr — jego buduj z workspace przez reaktor.)

---

## 14. Jak pracować z kodem (konwencje)

- **Commity trafiają do konkretnego sub-repo.** Workspace jest tylko spinaczem — żeby zmienić kod
  serwisu, wejdź do jego katalogu (`cd microservice-...`) i commituj tam, względem JEGO historii.
  Commit w workspace **nie** rusza sub-repów.
- **Autor:** `Robert Gardziński <jrobertgardzinski@gmail.com>`.
- **Komentarze i Javadoc po angielsku**, dokumentacja/todo po polsku.
- **Backlog** (co do zrobienia) jest w `todo.md` — w workspace (przekrojowe) i w każdym sub-repo.
- **Nie zamieniaj agregatora w rodzica** sub-repów — każdy serwis musi być budowalny samodzielnie.

---

## 15. Gdzie co leży (mapa)

```
security/                         <- WORKSPACE (repo-spinacz)
├── pom.xml                       <- agregator: lista wszystkich modułów
├── docker-compose.yml            <- definicja całego stacku
├── infra-up.sh / down / smoke    <- skrypty uruchomieniowe
├── observability/                <- configi Prometheus/Grafana/Loki/Tempo/Promtail
├── docs/                         <- dokumentacja (m.in. ten plik)
├── todo.md                       <- backlog przekrojowy
│
├── microservice-security/        <- (własne repo git) Micronaut
│   ├── security-domain/          <- warstwa domain
│   ├── security-system/          <- warstwa application (use case'y)
│   ├── security-infrastructure/  <- adaptery: HTTP, JDBC, Kafka
│   └── security-ui/              <- front React
├── microservice-memes/           <- (własne repo) Spring Boot, wielomodułowy
├── microservice-comments/        <- (własne repo) Spring Boot
├── microservice-user-collections/<- (własne repo) Helidon 4 SE
│   ├── src/main/java/.../domain
│   ├── src/main/java/.../application
│   ├── src/main/java/.../infrastructure
│   └── src/main/resources/db/migration  <- migracje Flyway
└── ...pozostałe serwisy i biblioteki
```

W każdym serwisie szukaj tego samego układu: **domain → application → infrastructure**. Jak to
zrozumiesz w jednym, zrozumiesz we wszystkich — bo o to w tym portfolio chodzi.

---

## 16. Słowniczek (szybkie definicje)

- **Mikroserwis** — mały, niezależnie wdrażany serwis odpowiedzialny za jeden obszar.
- **Hexagonal / porty i adaptery** — architektura oddzielająca logikę od technologii.
- **Use case** — jeden przypadek użycia aplikacji (klasa z `execute`).
- **Port** — interfejs (czego logika potrzebuje). **Adapter** — implementacja portu (jak).
- **Domain** — czyste reguły biznesowe, bez technologii.
- **JWT** — podpisany token tożsamości. **JWKS** — publiczne klucze do jego weryfikacji.
- **OAuth/OIDC** — logowanie przez zewnętrznego dostawcę.
- **MFA** — drugi składnik logowania (kod/TOTP/passkey).
- **Kafka / topic / event** — szyna zdarzeń; kanał; wiadomość na kanale.
- **Outbox** — tabela gwarantująca, że zmiana i jej zdarzenie wyjdą razem.
- **Saga** — rozproszona sekwencja kroków z potwierdzeniami (np. usuwanie konta).
- **Flyway / migracja** — wersjonowane zmiany schematu bazy plikami SQL.
- **Testcontainers** — prawdziwa baza w Dockerze na czas testu.
- **Gherkin / Cucumber** — język Given/When/Then i silnik wykonujący go jako testy.
- **Metryki / logi / traces** — trzy filary observability (liczby / tekst / oś czasu żądania).
- **cid (correlation-id)** — identyfikator śledzący jedno żądanie przez wszystkie serwisy.
- **Reaktor** — Mavenowe budowanie wielu modułów naraz w kolejności zależności.
- **Smoke test** — szybki test „czy podstawy działają" po uruchomieniu.
- **Virtual threads (Loom)** — tanie wątki JVM; miliony naraz (używa Helidon SE).

---

Jak przeczytasz to od góry do dołu, będziesz rozumiał **po co jest każdy klocek** i **jak je razem
uruchomić**. Następny krok praktyczny: odpal sekcję 13, otwórz Grafanę i poklikaj — najwięcej
zrozumiesz, patrząc na żywy system.
