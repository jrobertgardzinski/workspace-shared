# Przewodnik po projekcie — dla juniora, za rękę

Czytasz to, bo chcesz zrozumieć, co tu jest, po co, i jak tego dotykać bez strachu.
Idziemy od ogółu do szczegółu. Każde narzędzie ma sekcję „PO CO ono jest" — bo nazwa
bez powodu istnienia nic nie uczy.

---

## 1. Co to w ogóle jest (mapa z lotu ptaka)

To jest **workspace-agregator**: jeden katalog, w którym mieszka KILKANAŚCIE niezależnych
projektów, każdy z WŁASNYM repozytorium gita. Katalog nadrzędny (`security/`) trzyma tylko
spoiwo: wspólny `pom.xml` (agregator Mavena), skrypty uruchomieniowe, `docker-compose.yml`
i dokumentację przekrojową. Dzieci są w jego `.gitignore` — commit tutaj NIE dotyka
projektów, a commit w projekcie nie dotyka workspace'u.

Dwa „światy" w środku:

**Świat portfolio (mikroserwisy wokół galerii memów)** — każdy serwis celowo w INNYM
stylu architektonicznym i innym frameworku, żeby pokazać różne szkoły:

| Serwis | Port | Framework / styl | Za co odpowiada |
|---|---|---|---|
| microservice-security | 8080 | Micronaut, **hexagon 5-warstwowy** | konta, logowanie, MFA, OAuth, sesje, JWT |
| microservice-email | 8082 | Quarkus, **BCE** | wysyłka maili (weryfikacja, reset, pożegnanie) |
| microservice-memes | 8083 | Spring Boot, **warstwy** | galeria: upload, głosy, moderacja, NSFW |
| microservice-comments | 8085 | Spring Boot + Postgres | wątki komentarzy pod memami |
| microservice-paddock | 8086 | **Javalin, vertical slices** + PWA | socjale wokół gry: serwery, członkostwa, wydarzenia |
| microservice-idp | 8091 | Python | ATRAPA Google'a: OAuth/OIDC do testów logowania społecznościowego |
| microservice-image | — | Python + Pillow | PNG→WebP dla galerii (wewnętrzny) |
| sms / push | — | Python | kanały powiadomień paddocka (stub) |

**Świat gry (formula-simulator)** — menedżer F1 inspirowany Jagged Alliance 2:

| Kawałek | Port | Technologia | Za co odpowiada |
|---|---|---|---|
| backend gry | 8084 | **czysty JDK** (zero frameworka!) | stan gry, drużyny, sezony, ekonomia, transmisja SSE |
| race-sim | 8090 | **czysty Python** (stdlib) | fizyka: wyścigi, kwalifikacje, R&D, szkoła jazdy, ręczna jazda |
| app/ | — | React Native (Expo) | aplikacja menedżera na telefon/PC |
| viewer/ | — | HTML+JS | podglądy: powtórki, edytor torów, ręczna jazda 3D |

Do tego infrastruktura wspólna: Postgres (osobna baza per serwis!), Kafka, Mailpit,
MinIO, Prometheus+Grafana.

**Najważniejsza zasada świata portfolio:** każdy serwis ma własną bazę i NIE zagląda do
cudzej. Serwisy rozmawiają przez HTTP (synchronicznie) albo przez Kafkę (zdarzenia).

---

## 2. Narzędzia — które za co odpowiada i dlaczego go używamy

### Git — wiele repozytoriów
- **Po co:** każdy projekt żyje własnym życiem (własna historia, własny remote na GitHubie).
- **Pułapka nr 1 juniora:** `git status` w katalogu nadrzędnym NIE pokaże zmian w
  `microservice-security/`. Chcesz commitować zmianę w serwisie? `cd` DO NIEGO.
- Autor commitów jest skanonizowany: `Robert Gardziński <jrobertgardzinski@gmail.com>`.
- Backlogi: `todo.md` w workspace (przekrojowy) i osobne `todo.md` w każdym projekcie.

### Java 25 + Maven (przez `./mvnw`)
- **Po co Maven:** buduje Javę, zarządza zależnościami. `mvnw` to „wrapper" — pobiera
  właściwą wersję Mavena sam, więc niczego nie instalujesz.
- **Reaktor:** workspace'owy `pom.xml` AGREGUJE projekty — `./mvnw clean install`
  w korzeniu buduje wszystko w kolejności zależności. Jeden projekt + jego zależności:
  `./mvnw -pl microservice-security/security-infrastructure -am package`.
- Agregator NIE jest parentem — każdy projekt buduje się też solo, u siebie.

### Docker + Docker Compose
- **Po co:** każdy serwis pakujemy w kontener (izolowane, powtarzalne środowisko),
  a Compose stawia CAŁĄ układankę jednym poleceniem, z sieciami i zależnościami.
- **Ważny niuans tego repo:** Dockerfile'e są „runtime-only" — jar budujesz NA HOŚCIE
  (Mavenem), obraz go tylko kopiuje. Dlatego skrypty najpierw wołają `mvnw`, potem
  `docker compose up --build`.
- Wewnątrz sieci Compose serwisy widzą się po NAZWACH (`http://security:8080`),
  a Ty z przeglądarki po `localhost:PORT`.

### Postgres (×6!)
- **Po co:** trwałe dane. KAŻDY serwis ma własny kontener bazy (security, memes,
  comments, paddock, formula…) — to celowe: „database per service" = serwisy nie
  sprzęgają się przez wspólne tabele.
- Migracje schematu: **Flyway** (pliki `V1__...sql` w `src/main/resources/db`) —
  baza sama „dogania" wersję przy starcie serwisu.

### Kafka
- **Po co:** szyna ZDARZEŃ — rozmowa asynchroniczna. Przykłady u nas:
  - **outbox → mail:** security nie wysyła maila sam; zapisuje żądanie do tabeli
    (transakcyjnie, razem z danymi) i osobny wątek publikuje je do Kafki; email-serwis
    konsumuje i wysyła. Padnie mailer? Zdarzenia poczekają. To jest wzorzec
    **transactional outbox**.
  - **saga kasowania konta:** jedno zdarzenie, wielu konsumentów — memes czyści swoje,
    comments anonimizuje swoje.
- Jednowęzłowa (KRaft) — do dev wystarcza.

### Mailpit
- **Po co:** dev-owa „skrzynka pocztowa". Prawdziwych maili nie wysyłamy — Mailpit
  łapie wszystko i pokazuje pod `http://localhost:8025`. Stąd bierzesz linki
  weryfikacyjne przy rejestracji.

### MinIO
- **Po co:** magazyn obiektów mówiący językiem S3 (Amazona). Memes trzyma w nim BAJTY
  obrazków (metadane zostają w Postgresie). Na produkcji podmieniasz endpoint na
  prawdziwe S3 — kod się nie zmienia.

### Prometheus + Grafana (+ cAdvisor, node-exporter)
- **Po co:** obserwowalność, czyli „co się dzieje w środku, bez zgadywania".
  - **Prometheus** (`:9090`) co 10 s ODPYTUJE (scrape) każdy serwis o metryki
    i zapisuje je jako szeregi czasowe.
  - **Grafana** (`:3000`) rysuje z tego dashboardy (gotowy: „Stack — kontenery").
  - **cAdvisor** widzi każdy kontener (CPU/RAM/sieć), **node-exporter** — hosta.
  - Każdy serwis wystawia metryki w SWOIM stylu: Micronaut przez micrometer
    (`/prometheus`), Quarkus (`/q/metrics`), Spring przez actuator
    (`/actuator/prometheus`), a czyste JDK/Python — ręcznie sklejony format tekstowy
    (`/metrics`) — bo format Prometheusa to po prostu kilka linijek tekstu.
- Konfiguracja: `observability/` (scrape lista, provisioning Grafany, dashboard).

### Python 3 (czysty stdlib)
- **Po co:** moduły symulacyjne gry (`formula-simulator/sim/race/`). Świadoma decyzja:
  ZERO zależności (żadnego numpy) — prototyp do balansowania, łatwy do czytania.
  Testy: wbudowany `unittest`.

### Stub IdP (microservice-idp)
- **Po co:** „Zaloguj przez Google" bez Google. Odgrywa protokół OAuth2/OIDC
  (authorize → code → token → id_token/userinfo), więc cały przepływ logowania
  społecznościowego testujesz lokalnie. Produkcja = podmiana URL-i w env.

### Testy — trzy piętra
- **Jednostkowe/feature per projekt:** JUnit 5 + Cucumber (Java; scenariusze
  `*.feature` = wykonywalne specyfikacje), `unittest` (Python). Raporty ładne w
  **Allure** (`allure-serve.sh`).
- **UI:** security-ui (React) testowane przez cucumber-js + **Playwright**
  (przeglądarka sterowana kodem).
- **Smoke całego stacku:** `./infra-smoke.sh` — PRAWDZIWY test end-to-end na żywych
  kontenerach: rejestracja → mail → weryfikacja → logowanie → MFA → memy → saga
  kasowania → wyścig w grze → metryki. Jak przechodzi, znaczy że ŚWIAT DZIAŁA.

---

## 3. Jak to odpalić (ściąga)

```bash
# cały stack (wszystko naraz):
./infra-up.sh          # buduje jary + docker compose up
./infra-smoke.sh       # dowód, że działa end-to-end
./infra-down.sh        # sprzątanie

# tylko jeden świat:
./memes-up.sh          # galeria + komentarze + logowanie + mail + monitoring
./formula-up.sh        # gra + fizyka + logowanie + monitoring

# co gdzie klikać po starcie:
#   http://localhost:8083                     galeria memów (rejestracja/logowanie tu)
#   http://localhost:8025                     skrzynka Mailpit (linki z maili!)
#   http://localhost:8084                     panel menedżera F1
#   http://localhost:8090/school/drive-ui     RĘCZNA JAZDA bolidem (strzałki, C, Z/X, M)
#   http://localhost:3000                     Grafana
#   http://localhost:9090                     Prometheus (zakładka Status→Targets)

# testy:
./mvnw test                                   # cała Java
( cd formula-simulator/sim/race && ./run-tests.sh )   # cała fizyka, równolegle (~15 min)
```

---

## 4. Pojęcia, które tu spotkasz (słowniczek z sensem)

- **Hexagon (porty i adaptery):** domena w środku nie zna świata; „porty" to interfejsy,
  „adaptery" to implementacje (HTTP, baza, Kafka). W security dodatkowo 5 warstw:
  domain / config / system (use case'y) / application (fasada) / infrastructure.
- **BCE (Boundary-Control-Entity):** wariant tego samego ducha, użyty w email.
- **Vertical slices (paddock):** zamiast warstw — pionowe plastry per funkcja
  (registry/, events/, feed/...), każdy z własnymi trasami.
- **Contract-first:** najpierw SCHEMAT (JSON Schema w `formula-simulator/contracts/`),
  potem kod po obu stronach. Kontrakt = umowa Java↔Python↔boty użytkowników.
- **JWT offline vs introspekcja:** token można zweryfikować (a) pytając security
  `GET /me` przy każdym żądaniu (introspekcja — proste, ale sprzęga dostępność), albo
  (b) sprawdzając PODPIS tokenu kluczem publicznym z `/.well-known/jwks.json`
  (offline — u nas standard w całym stacku). Cena offline: wylogowanie „nie działa"
  do wygaśnięcia tokenu.
- **Outbox / saga:** patrz sekcja Kafka wyżej.
- **Determinizm + piny (fizyka gry):** symulacja z tym samym ziarnem (seed) daje
  ZAWSZE ten sam wynik; testy „przypinają" konkretne historie (np. „walka z otarciem
  na seedzie 0"). Zmieniasz fizykę → pin świadomie migruje. Od R1 każdy samochód ma
  WŁASNY strumień losowości, więc dodanie auta nie tasuje losów pozostałych.
- **Zmysły rozmyte:** boty nie widzą „9.34 m", widzą `CLOSE 4/5` — jak kierowca.
  Pełny opis fizyki: `formula-simulator/docs/physics.md`.

---

## 5. Typowe zadania — krok po kroku

### „Chcę zmienić coś w serwisie X"
1. `cd microservice-X` — jesteś w JEGO repo.
2. Zmień kod. Testy: `../mvnw test` (albo `./mvnw` jeśli projekt ma własny wrapper).
3. Zbuduj jar: `../mvnw -q package -DskipTests` (z korzenia: `-pl microservice-X -am`).
4. Przeładuj kontener: `docker compose up -d --build X` (z KORZENIA workspace'u).
5. Commit W SUB-REPO. Gotowe.

### „Chcę pogrzebać w fizyce gry"
1. `cd formula-simulator/sim/race`.
2. Przeczytaj `../../docs/physics.md` (jak jest) i `../../docs/expansion-plan.md`
   (dokąd zmierza).
3. Zmieniasz równania → `./run-tests.sh`. Czerwone piny historii = albo bug, albo
   świadoma migracja (sweep seedów — wzorce w gicie).
4. Ręczna jazda czuje zmiany po: `docker compose up -d --build race-sim` (z korzenia).

### „Coś nie działa w stacku"
1. `docker compose ps` — co leży?
2. `docker compose logs NAZWA --tail 50` — co krzyczy?
3. Grafana → dashboard kontenerów: kto zjada CPU/RAM?
4. `./infra-smoke.sh` — który krok przepływu pęka?

### „Gdzie jest dokumentacja czego?"
- `todo.md` (tu i w każdym projekcie) — CO jest do zrobienia i co postanowiono.
- `docs/adr/` — decyzje architektoniczne (np. „domena nie broni się przed nullem").
- `docs/deployment-plan.md` — plan wdrożeń (Compose → VPS → k3s).
- `docs/glossary/` + `build_glossary.py` — interaktywny słownik języka domeny.
- `formula-simulator/docs/` — plan gry, plan rozbudowy, fizyka, tory, boty
  (`bots/README.md` = SDK pisania własnego kierowcy!).
- `maven-cheatsheet.md` — ściąga Mavena.

---

## 6. Rzeczy, które junior psuje najczęściej (nie bądź tym juniorem)

1. **Commit w złym repo.** Zmieniłeś serwis, commitujesz w workspace — zmiana „znika".
   Zawsze sprawdź `pwd` i `git remote -v`.
2. **Stary obraz.** Zmieniłeś kod, ale kontener dalej stary — zapomniałeś `--build`
   albo jara. Objaw: „przecież to naprawiłem!".
3. **Test na zajętej maszynie.** Fizyka ma boty z deadline'em czasowym — suita
   podnosi go sama (`run-tests.sh`), ale pojedynczy test odpalony obok pełnego builda
   może flakować. Podejrzewasz flake? Odpal solo.
4. **Edycja plików w trakcie biegu testów w tle** — moduły importują kod w RÓŻNYCH
   momentach; werdykt będzie kłamał. Najpierw werdykt, potem edycja.
5. **Sekrety do gita.** `.env`, `creds.txt` — mają być w `.gitignore`. Zawsze.
