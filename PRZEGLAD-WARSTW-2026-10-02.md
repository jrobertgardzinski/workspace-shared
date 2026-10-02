# Przegląd warstw — domain / config / usecase (system) / application

Skan całej posiadłości pod JEDNO założenie właściciela, wypowiedziane 2026-10-02:

> **Repozytoria siedzą w `domain`. `application` to pomost pomiędzy frameworkiem a domeną:**
> zgrupowanie use case'ów w serwisy, które jako argumenty przyjmują javowe klasy / prymitywy,
> z nich budują klasy domenowe i dopiero potem odpalają use case'y.
> **Wzorzec:** rejestracja i uwierzytelnianie w `microservice-security`.

Czyli cztery pytania do każdego modułu:

1. **`*-domain`** — czy porty (repozytoria) są TU i tylko tu? Czy nie ma tu frameworka, konfiguracji
   ani wiedzy o transporcie?
2. **`*-config`** — czy to wyłącznie typy konfiguracyjne, czy ktoś tu podejmuje decyzje?
3. **`*-system` / `*-usecase`** — czy use case'y biorą typy DOMENOWE (nie prymitywy) i czy nie
   sklejają w sobie pomostu?
4. **`*-application`** — czy to pomost: prymitywy/javowe klasy na wejściu, budowanie typów
   domenowych, potem wywołanie use case'u? Czy nie ma tu decyzji biznesowych i czy nie przyjmuje
   gotowych typów domenowych od frameworka?

Skan jest **czytający**. Nic nie jest zmieniane; to lista ustaleń, nie refaktor.

## Stan: SKAN ZAKOŃCZONY, POTEM ZWERYFIKOWANY ADWERSARYJNIE 2026-10-02

Wszystkie sześć punktów checklisty odhaczone, a potem cały plik przeszedł weryfikację przez 128
agentów — **wynik jest w ANEKSIE na końcu i on tu rządzi.** Sekcje 1-6 oraz sekcja „ODPOWIEDŹ NA
ZAŁOŻENIE" zostały napisane PRZED weryfikacją i zawierają 24 odstępstwa z błędnymi cytatami lub
liczbami, pięć nieprawdziwych twierdzeń przekrojowych oraz tezę nośną wypowiedzianą nad ćwiercią
kodu, której nikt nie otworzył. **Nie czytaj ich bez aneksu.**

Ten plik jest całym stanem przeglądu. Gdyby trzeba było go kiedyś rozszerzyć o nowy cel:

> **„Czytaj `shared/PRZEGLAD-WARSTW-2026-10-02.md`, dopisz cel do checklisty i leć tym samym
> trybem — jeden agent na serwis, raport po każdym, weryfikacja tez nośnych przed wpisaniem."**

## Checklista

- [x] 1. `shared/microservice-security` — security-domain / -config / -system / -application **(wzorzec)**
- [x] 2. `portal/microservice-memes` — memes-domain / -config / -system / -application (+ memes-tags, memes-image)
- [x] 3. `portal/microservice-comments` — comments-domain / -config / -system / -application
- [x] 4. `portal/microservice-user-collections` — collections-domain / -config / -system / -application
- [x] 5. `portal/microservice-offboarding` — offboarding-domain / -system / -application (bez `-config`)
- [x] 6. `shared/email` + `shared/password` — biblioteki z tym samym podziałem (`-domain` / `-config` / `-usecase`)
      **Korekta z sekcji 6:** `email` ma trzy moduły, `password` cztery. Katalogi `email-security`,
      `password-security-config`, `password-security-system` i `hash-algorithm-contract` to widma
      bez pomów, skasowane z gita w `6d6031b` i `ae7026c`.

Poza zakresem (brak podziału na warstwy): `microservice-idp`, `microservice-sms`, `microservice-push`
(Python), `microservice-image` (Python), `microservice-email` (płaskie `src`, BCE Quarkus),
`formula/microservice-paddock` (płaskie `src`), `formula-simulator`, `formula-sdk`.


---

# ODPOWIEDŹ NA ZAŁOŻENIE (synteza sześciu skanów)

## Krótko

Założenie jest zrealizowane **w jednym miejscu w całej posiadłości**: na ścieżce rejestracji w
`microservice-security`. I nawet tam nie w module `application` — ten nie ma `src/main` — lecz w
kontrolerze plus bibliotece `constraint` z `shared/libs`. Wszędzie indziej pomost zbudował
framework, a `*-application` dostaje gotowe typy domenowe albo nie istnieje.

Druga połowa założenia — **„repozytoria siedzą w domain"** — trzyma się znacznie lepiej: w portalu
prawie wszędzie, a w `microservice-user-collections` bez jednego wyjątku. Łamie ją najbardziej
**sam wzorzec**: `security-system` trzyma 12 portów, w tym jeden o nazwie `SettingsRepository`.

## Gdzie faktycznie jest pomost

| serwis | czym jest `*-application` | kto buduje typy domenowe |
|---|---|---|
| microservice-security | **nie ma `src/main`** — glue Cucumbera | kontroler + biblioteka `constraint` (rejestracja: `Supplier<VO>`) |
| microservice-memes | 12 use case'ów + 3 porty | filtr HTTP i trzy kontrolery; **3 z 4 kontrolerów omijają application** |
| microservice-comments | 6 use case'ów + 2 porty | filtr HTTP; kontroler **duplikuje** niezmienniki encji |
| microservice-user-collections | 3 jednolinijkowe przelotki | kontroler; **nic nie omija application** |
| microservice-offboarding | centralka sagi, **na produkcji** | sama parsuje JSON, ale **nie ma VO do zbudowania** |
| shared/email, shared/password | brak takiego modułu (`-usecase`) | **tu żyje jedyna realizacja wzorca** |

## Siedem rzeczy, które powtarzają się wszędzie

1. **Zero `Supplier<VO>` poza security.** Mechanizm, który właściciel wskazał jako dobry przykład,
   nie został użyty ani raz w żadnym z czterech serwisów portalu.
2. **Zero `sealed` wyników use case'ów poza security.** Portal zwraca `enum`, rekord, `Optional`,
   `int` albo `void` — w comments cztery use case'y mają cztery różne konwencje wyniku.
3. **Brak typu domenowego na id treści** w memes, comments i collections. To nie detal: **to główna
   przeszkoda w przyjęciu wzorca z security** (patrz sekcja 6). Id są losowane jako
   `UUID.randomUUID().toString()` i nigdy nie walidowane — jedyne miejsce, gdzie id mema jest
   sprawdzane, to druty sagowe, więc przez Kafkę przychodzi id poprawny, a przez HTTP dowolny string.
4. **Brak ArchUnit w całej posiadłości.** Kierunek zależności trzymają wyłącznie pomy. Trzy wyjątki,
   żaden nie pilnuje warstw wprost: `ObservabilityIsOptionalTest` w offboarding (szuka słów
   dostawcy metryk w cudzym drzewie źródeł), `ConfigValueLawTest` w `password-config` (czyta źródła
   pakietu), oraz `analyze-only` z `failOnWarning=true` w pomach `email` i `password`.
5. **Martwe krawędzie w pomach** w czterech z pięciu serwisów (zadeklarowana zależność bez ani
   jednego importu). **Jedyne dwa repozytoria bez ani jednej martwej krawędzi to `email` i
   `password`** — dokładnie te, które mają `analyze-only` z `failOnWarning`. Najgorszy wariant jest
   w offboarding: krawędź martwa w `system`, ale nośna tranzytywnie dla `application`, więc jej
   usunięcie wywróci kompilację piętro wyżej.
6. **Każdy README jest o jeden refaktor do tyłu** — bez wyjątku, we wszystkich sześciu celach.
   W bibliotekach dotknęło to nawet spisu modułów: cztery katalogi to widma bez pomów, skasowane z
   gita pół roku temu.
7. **Słowo „system" ma trzy znaczenia.** W security to moduł trzymający porty; w portalu moduł use
   case'ów sagowych POD `application`; w offboarding wszystkie use case'y, przy `application` bez
   ani jednego. Biblioteki miały czwarte wystąpienie i **jako jedyne z niego zrezygnowały**,
   przechrzcząc `*-security-system` na `*-usecase` (`32e5735`, `9ed45e3`).

## Co jest zrobione lepiej niż we wzorcu

- **`microservice-user-collections`** — jedyny serwis portalu, w którym **żaden port nie wyciekł**
  poza domenę, i jedyne miejsce w całym przeglądzie, gdzie deklarowana granica (opaque'ość
  `ItemRef`) jest naprawdę dotrzymana. W `config` nie ma stanowej maszyny.
- **Porty w domenie:** memes trzyma 7 w domain, comments 4, collections 3 — wszystkie bliżej reguły
  właściciela niż `security-system` z dwunastoma.
- **`email` i `password`** trzymają granice najlepiej w posiadłości, ale **Mavenem, nie testem**.
- **`microservice-comments`** to jedyny serwis, w którym nic nie omija `application`.

## Pięć rzeczy, które wyszły przy okazji i nie są o warstwach

- **`libs/constraint` nie ma ani jednego testu** (`libs/src/test` nie istnieje), a to w nim siedzi
  cała maszyneria wzorca. Wariant `RejectedDueToInvariantBreakage` nie jest pokryty testem nigdzie.
- **`Constraints.validate` łapie `Exception`**, więc NPE w supplierze staje się „odmową z powodu
  złamanego niezmiennika" z komunikatem `null`.
- **`AbstractEmail.equals` zrównuje `Email` i `NormalizedEmail`** — dwa typy istniejące po to, by
  ich nie mylić, są nierozróżnialne w `Set` i `Map`.
- **Mechanizm sagi w offboarding siedzi w infrastrukturze** (kworum, zatrzask, dedup w
  `JdbcSagaStore`), a reguła pierwszeństwa zamknięcia własnego nad administracyjnym jest
  zaimplementowana dwa razy — w adapterze i w fejku — spięta tylko testem kontraktowym.
- **Retencja danych osobowych (30 dni) jest stałą w kodzie** use case'u offboarding, bez źródła
  konfiguracji, bo „nobody ever set one".

## Czego ten przegląd NIE zrobił

Nie zmieniono ani jednego pliku. Nie uruchamiano buildów. Nie proponuje się tu refaktoru ani
kolejności prac — to lista ustaleń pod decyzje właściciela, nie plan.

---
## 1. `shared/microservice-security` — wzorzec

**Nagłówek całego przeglądu, sprawdzony ręcznie:** `security-application` **nie ma `src/main`**.
Katalog `security-application/src/` zawiera wyłącznie `test`. Jedyna klasa produkcyjna, jaka tam
była — `SecurityService` z metodą `register(String email, String password)` — jest skasowana
commitem `401c7ed` („Delete SecurityService: a facade nothing ever called"). Pomost, który
założenie umieszcza w `application`, siedzi w tym serwisie **w kontrolerach
`security-infrastructure`**. To zmienia miarę dla pozostałych pięciu celów: pytanie brzmi nie tylko
„czy application jest pomostem", ale najpierw „gdzie w ogóle ten pomost jest".

### Wzorzec, jaki tu jest

Repozytoria faktycznie siedzą w `security-domain/.../domain/repository/` (11 interfejsów, m.in.
`UserRepository.java:15`, `SessionRepository.java:25`), a use case'y w `security-system` biorą typy
domenowe. `security-application` zawiera tylko glue Cucumbera
(`security-application/src/test/java/.../feature/*/…Steps.java`) i jest tak opisany przez
właściciela w `docs/opus-playbook.md:11-13` („`security-application` NIE MA `src/main` — to warstwa
testowa, która napędza te use case'y Cucumberem; sprostowane 2026-09-12") oraz `todo.md:795-797`.

**Rejestracja.** `SecurityController.java:84` przyjmuje `@Body Map<String,String>` i `HttpRequest`;
z nagłówków/połączenia `ClientIpResolver` buduje domenowe `IpAddress`
(`ClientIpResolver.java:60-78`). Prymitywy `email`/`password` (`:94-95`) **nie są** zamieniane na
typy domenowe w kontrolerze, lecz podane jako **`Supplier<Email>` / `Supplier<PlaintextPassword>`**
do `Register.execute` (`Register.java:26`, wywołanie `SecurityController.java:106`). Typ domenowy
powstaje dopiero wewnątrz use case'u: `_EmailVerdict.judge` (`_EmailVerdict.java:13-19`) →
`CanRegister.evaluate(Supplier)` → `_EmailCandidate.evaluate` (email-usecase) łapie
`InvalidEmailException` i zamienia ją na `Outcome.RejectedDueToInvariantBreakage` z kodem;
analogicznie `_PasswordVerdict.judge` (`_PasswordVerdict.java:15-17`) przez
`CreatePasswordHash.create(Supplier)` → `Constraints.validate`. Czyli **wyjątki domenowe z
konstruktorów VO łapie biblioteka constraintów po stronie use case'u, nie pomost** — po to jest
`Supplier`. `RegistrationAttempt.resolve` (`RegistrationAttempt.java:22-44`) łapie wyjątek
repozytorium `EmailAlreadyTakenException` (`:40`) i zamienia na wariant wyniku. Wynik to
zapieczętowany `RegisterResult` (`Registered | Rejected | EmailAlreadyTaken`,
`RegisterResult.java:8-16`), mapowany `switch`-em na 201/422/201 (`SecurityController.java:126-136`).

**Uwierzytelnianie.** `AuthenticationController.java:74-98`: z `HttpRequest` powstaje
`Source(IpAddress, userAgent)` (`:75`), z `Map` body —
`new AuthenticationRequest(source, Email.of(...), PlaintextPassword.of(...))` (`:87-88`), tu
kontroler **sam łapie `IllegalArgumentException`** (`:89-97`) i odpowiada 401. Gotowy VO idzie w
`transactionBoundary.execute(() -> authentication.execute(authenticationRequest))` (`:98`).
`Authentication.execute(AuthenticationRequest)` (`Authentication.java:48`) z VO buduje kolejne VO
(`Credentials`, `LockoutSubject`, `:50-54`) i orkiestruje pakietowo-prywatne kroki
(`_BruteForceGuard`, `_VerifyCredentials`, `_RequireVerifiedEmail`, `_GenerateSession`,
`_Clean/_UpdateBruteForceRecords`), składane przez `AuthenticationFactory.assemble`
(`AuthenticationFactory.java:36`). Wynik: zapieczętowany `AuthenticationResult`
(`Authenticated | Rejected | Blocked | EmailNotVerified | MfaRequired`,
`AuthenticationResult.java:7-18`) → 200/401/429/403/202 (`AuthenticationController.java:100-119`).
Transakcja, throttle per źródło i cookie refresh-tokena są sprawą kontrolera. Glue w
`security-application` robi to samo bez HTTP: `RegisterSteps.java:46`,
`AuthenticationSteps.java:125,152` budują te same typy i wołają te same `execute`, na fejkach
repozytoriów z test-jara `security-domain`.

**Miara dla pozostałych serwisów:** (1) porty tylko w domain, (2) use case przyjmuje VO albo
`Supplier<VO>` i zwraca zapieczętowany wynik zamiast rzucać, (3) pomost — konstrukcja VO, złapanie
`IllegalArgumentException`/`InvalidEmailException`, mapowanie wyniku na kod HTTP, transakcja —
siedzi w adapterze HTTP, (4) `*-application` to osobne wejście testowe na te same use case'y.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| security-domain | odstępstwa | Zero adnotacji i importów frameworka, wszystkie repozytoria tutaj, VO pilnują niezmienników — ale dwa typy konfiguracyjne (`*TokenValidityInHours implements ConfigValue`) i enum z nazwami „on the wire" oraz kluczami properties (`StepUpAction`) siedzą w domain, nie w config. |
| security-config | zgodny | Wyłącznie rekordy wartości z deploymentu z defaultami i walidacją w konstruktorze; czyta z niego system (`_BruteForceGuard`, `MfaChain`, `StepUp`…) i infrastruktura (`BeanFactory`), domain nie. |
| security-system | odstępstwa | Rdzeń (`Register`, `Authentication`) bierze typy domenowe / `Supplier<VO>`, bez frameworka — ale 12 portów jest zadeklarowanych tu zamiast w domain, a MFA/step-up/settings/throttle przyjmują gołe `String`. |
| security-application | niezgodny | Modułu „pomost" nie ma: brak `src/main`, same kroki Cucumbera; pomost String→domena robią kontrolery w `security-infrastructure`. |

### Odstępstwa

1. **`security-application` nie jest warstwą application — pomost zbudował framework.**
   `security-application/pom.xml` (tylko zależności `provided`/`test`), brak
   `security-application/src/main`; `SecurityController.java:84-106`,
   `AuthenticationController.java:74-98`; `uwagi.md:1`; commit `401c7ed`.
   Jest: kontrolery Micronauta przyjmują `Map<String,String>`, budują
   `Email`/`PlaintextPassword`/`IpAddress`/`AuthenticationRequest`, łapią `IllegalArgumentException`
   i wołają use case bezpośrednio. Oczekiwano (wg założenia): serwisu w application, który to robi.
   Właściciel sam to zaakceptował: `uwagi.md:1` zapisuje gniew na pierwszą wersję, która złamała
   „system operuje na domenach, a dopiero application mapuje String na domenę", a `401c7ed` kasuje
   `SecurityService` jako fasadę, której nikt nie wołał. `opus-playbook.md:11-13` i `Readme.md:68`
   („Application — translates between the outside world and the domain") są ze sobą sprzeczne —
   Readme opisuje warstwę, której w kodzie nie ma. Ma znaczenie, bo jako „dobry przykład" ten
   serwis uczy innych: *pomost w adapterze HTTP, application = wejście testowe*.

2. **Pomost jest rozdwojony: rejestracja opóźnia konstrukcję VO przez `Supplier`, uwierzytelnianie
   buduje VO w kontrolerze i samo łapie wyjątek.**
   `Register.java:26` vs `AuthenticationController.java:85-97`; także `ChangePassword.java:46-47`,
   `ResetPassword.java:69` (Supplier) vs `VerifyEmailController.java:64-65,83-84`,
   `PasswordResetController.java:65-66,84-85`, `EmailChangeController.java:59-60` (catch w
   kontrolerze). Oba podejścia są uzasadnione na miejscu (javadoc `:90-95`: nieistniejący adres
   „answers exactly like a wrong password"), ale to dwie konwencje pomostu w jednym serwisie; dla
   pozostałych serwisów trzeba wskazać jedną.

3. **Dwanaście portów zadeklarowanych w `security-system`, nie w `security-domain`.**
   `system/settings/SettingsRepository.java:4`, `system/settings/SettingCatalog.java:11`,
   `system/mfa/PendingAuthenticationStore.java:11`, `StepUpStore`, `EnrolmentChallengeStore`,
   `SessionElevation`, `SpentTotpSteps`, `CodeHasher.java:9`, `RecoveryCodeHasher`,
   `AuthenticationFactor`, `roles/RolesOf`, `authentication/BlockDurationPolicy.java:9`.
   Założenie mówi „repozytoria siedzą w domain"; `SettingsRepository` nosi to słowo w nazwie.
   Javadoc uzasadnia część: `CodeHasher` — „A port so the crypto stays in the infrastructure
   layer… out of the domain and system layers" (uzasadnia *port*, nie *miejsce* portu);
   `PendingAuthenticationStore` — stan przejściowy przebiegu, nie fakt domenowy, broni się;
   `BlockDurationPolicy` — „Extracted as a seam so the duration is injectable", broni się.
   `SettingsRepository`/`SettingCatalog` operują na `String key, String text` i `LiveKey` z
   biblioteki config — to ani domena, ani konfiguracja, tylko mechanika ladderu; świadoma decyzja
   (`todo.md:857-876`, `review-2026-09-08.md:15`), ale odstępstwo od reguły.

4. **Część use case'ów przyjmuje gołe `String` — pomost udaje use case albo pomostu nie ma.**
   `ContinueAuthentication.java:39` (`String ticket, String proof`), `StepUp.java:78`
   (`String accessToken, String passwordAttempt`), `StepUp.java:108`, `EnrolFactor.java:38,49`,
   `SetSetting.java:43` (`String key, String text`), `SourceThrottle.java:72,106`.
   `passwordAttempt` jako `String` zamiast `PlaintextPassword` i `accessToken` jako `String` zamiast
   `domain/vo/token/AccessToken` to dryf obok istniejących typów. `SetSetting` jest celowo
   generyczny (javadoc: „the catalogue supplies the rule's own parser and gate") i to się broni;
   `ticket`/`proof` są wartościami przejściowymi stanu MFA.

5. **Konfiguracja i słownictwo transportu w `security-domain`.**
   `domain/vo/AccessTokenValidityInHours.java:8,11` i `RefreshTokenValidityInHours.java:8`
   (`implements ConfigValue<Integer>`, `KEY = "security.session.access.token.validity.hours"`),
   `domain/vo/SessionTokensConfig.java:3`, `domain/vo/StepUpAction.java:41-48` (`wire()`, `key()`).
   Oczekiwano: wartości z deploymentu i klucze properties w `security-config` (gdzie identyczne
   rekordy `MaxFailures`, `MaxSessionLifetimeHours` leżą). Javadoc `StepUpAction.java:5-10`
   uzasadnia kompletnością katalogu („an action nobody registered does not compile") i ten argument
   się broni, ale koszt to zależność domain → biblioteka `config` (`security-domain/pom.xml:26`) i
   wiedza domeny o formacie body HTTP. `SessionTokensConfig` nie ma uzasadnienia na miejscu.

6. **Decyzje przepływu rejestracji podjęte w kontrolerze, nie w use casie.**
   `SecurityController.java:105-124`, w szczególności `:109` (start weryfikacji po `Registered`) i
   `:112-118` (`emailVerifications.isVerified` → notice vs nowy link). Kontroler wstrzykuje
   `EmailVerificationRepository` i `RegistrationNoticeNotifier` (`:65-66`) i sam decyduje, co po
   rejestracji wysłać — a to reguła biznesowa („zarejestrowany ≠ aktywny"). Javadoc `:97-104`
   uzasadnia tylko *jedną transakcję*, nie *miejsce* decyzji. `todo.md:788-797` sam nazywa problem
   („Sam proces nie ma domu"), a `opus-playbook.md:15` mówi „Nowa reguła biznesowa NIGDY nie
   zaczyna się od kontrolera". Analogicznie `MeController.java:52`, `FactorsController.java:132`,
   `DeleteAccountController.java:151`.

7. **`javax.crypto` w `security-system`.** `system/mfa/TotpFactor.java:6-7`. HMAC-SHA1 RFC 6238
   liczony w warstwie use case'ów, podczas gdy hashowanie kodów wyniesiono za port `CodeHasher`
   właśnie po to, „żeby krypto zostało w infrastrukturze". To JDK, nie framework, więc odstępstwo
   łagodne — ale niespójne z własną regułą z `CodeHasher`.

### Czego NIE znalazłem

- Żadnego importu `io.micronaut`, `jakarta`, `org.springframework`, Kafki, JDBC, SLF4J ani
  adnotacji DI/serializacji w `security-domain`, `security-config` i `security-system` (jedyny
  nie-JDK import spoza posiadłości to `javax.crypto` w `TotpFactor`).
- Żadnego interfejsu `*Repository` poza `security-domain` z wyjątkiem `system/settings/SettingsRepository`
  (pkt 3); wszystkie 11 repozytoriów domenowych ma implementacje tylko w `security-infrastructure`
  (`InMemory*`, `Jdbc*`) i fejki w test-jarze domain.
- Żadnej decyzji biznesowej w `security-config`: każdy rekord to wartość + `KEY` + `DEFAULT` +
  zakres w konstruktorze.
- Żadnego use case'u w `security-system`, który woła framework, HTTP albo zegar systemowy wprost
  (wszędzie wstrzyknięty `Clock`), ani który loguje.
- Żadnego miejsca, w którym `security-application` byłoby zależnością `security-infrastructure` —
  application nie jest w ścieżce produkcyjnej.
- **Żadnego testu architektury** (ArchUnit lub podobnego) pilnującego kierunku zależności; reguła
  „dependencies pointing down" (`Readme.md:51-58`) jest trzymana tylko przez pomy.
- Żadnego `catch` wyjątku domenowego w glue `security-application` — kroki podają zawsze poprawne
  literały, więc ścieżka „String nie da się zbudować w VO" jest testowana wyłącznie po HTTP.

---
## 2. `portal/microservice-memes`

**Sprawdzone ręcznie:** `memes-application/src/main` istnieje i zawiera dokładnie 15 klas — 12 use
case'ów i 3 porty. Typu `MemeId` nie ma w całym serwisie. `MemeController.java:48` importuje
`com.jrobertgardzinski.memes.system.DeleteMeme` wprost, mijając application.

### Gdzie jest pomost

`memes-application` ma kod produkcyjny: 12 use case'ów (`PublishMeme`, `ServeMeme`,
`MakeThumbnail`, `ViewMeme`, `ListMemes`, `SearchMemesByTag`, `TagMeme`, `FlagMeme`, `CastVote`,
`ShowMemeVote`, `ShowMemeScores`, `RankMemes`) i 3 porty (`ObjectStore`, `ImageEncoder`,
`ContentFlags`). **Nie jest jednak pomostem w sensie założenia:** ani jedna klasa nie przyjmuje
surowych danych po to, żeby zbudować z nich typ domenowy — use case'y dostają albo gotowy `UserId`,
albo `String memeId` (bo typu domenowego na id mema nie ma), albo `Tag`. Pomost siedzi w
`memes-infrastructure`.

**Przepływ uploadu.** `RequireSignInFilter.java:68` wkłada do atrybutu żądania gotowy `UserId`,
zbudowany z tokenu przez `Caller.userIdFrom` (`Caller.java:20-22`, `UserId.of(subject)` z łapaniem
`IllegalArgumentException`). `MemeController.upload` (`MemeController.java:81-100`) bierze
`MultipartFile` i ten `UserId`, sprawdza `RateLimit` (`:85`), zamienia multipart na `byte[]` (`:95`)
i woła `publishMeme.execute(file.getBytes(), uploaderId)`. `PublishMeme.execute(byte[], UserId)`
(`PublishMeme.java:42-77`) optymalizuje obraz, losuje id jako `UUID.randomUUID().toString()`
(`:44`), rezerwuje treść w `MemeContentIndex.claim` (`:45`) i zapisuje
`new Meme(candidate, author, format, data)` przez `MemeRepository.save` (`:50`).
`JdbcMemeRepository.save` (`:43-49`) wstawia wiersz i kładzie bajty do `ObjectStore` (`:48`). Błędy
obrazu wracają wyjątkiem `InvalidImageException`, mapowanym w `WebErrorHandler:40-41` na 400.

**Pomost dla sagi** jest bliższy założeniu: `PurgeCommandsListener.read` (`:97-115`) buduje `UserId`
z JSON-a przez `ClosureCommand.userIdOf` (`:112`), a `MemesClosureParticipant` w
`memes_account-closure` (`:60-63`) parsuje regułę do `PurgeRule` i dopiero woła
`purgeUserContent.execute(leaver, requested.rule())`. Czyli warstwa bez frameworka dostaje surowe
dane i sama buduje typ domenowy przed wywołaniem use case'u.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| memes-domain | odstępstwa | Wszystkie 7 portów jest tu i tylko tu, zero importów frameworka; ale id mema jest gołym `String` w całym module, `Meme` nie pilnuje żadnego niezmiennika, a javadoc portów linkuje do klas z `memes-application`, których domena nie widzi. |
| memes-config | odstępstwa | Cztery rekordy to czyste typy konfiguracyjne, ale `RateLimit` jest stanową maszyną decyzyjną (`ConcurrentHashMap` + `Clock`), którą kontroler woła bezpośrednio. |
| memes-system | odstępstwa | Use case'y biorą `UserId`/`PurgeRule`, nie parsują, nie wołają frameworka i zwracają wyniki; ale `DeleteMeme.execute(String)` i `MemeEvents.memeDeleted(String)` operują na stringu, a `PurgeUserContent` sam degraduje `UserId` do `String` dla portu głosów. |
| memes-application | niezgodny | Ma `src/main`, lecz nie jest pomostem: przyjmuje gotowe typy domenowe zbudowane w infrastrukturze (`UserId`, `Tag`, `VoteDirection`), zależy od konkretnej klasy `WebImageOptimizer` zamiast portu, trzyma trzy porty, a kontrolery omijają go, wołając `memes-system` i porty domenowe wprost. |
| memes-tags | zgodny | Jeden VO `Tag` z walidacją w konstruktorze i fabryką `Tag.of(raw)` normalizującą wejście; żadnych zależności. |
| memes-image | odstępstwa | Czyste JDK, bez frameworka, ale `WebImageOptimizer` jest niefinalną klasą konkretną (nie portem) i biblioteka zależy od `memes-config`. |

### Odstępstwa

1. **Nie istnieje typ domenowy na id mema — `String` płynie przez wszystkie warstwy.**
   `Meme.java:8`, `MemeMetadata.java:35`, `MemeRepository.java:29,35,53,83,90`, `MemeEvents.java:9`,
   `MemeContentIndex.java:13,16`, `TagRepository.java:13-19`, `RankedMeme.java:4`;
   `DeleteMeme.java:39`; `PublishMeme.java:44`. Id jest losowane jako
   `UUID.randomUUID().toString()` i dalej nikt go nigdy nie waliduje — `DeleteMeme`, `ServeMeme`,
   `CastVote` dostają dowolny string z `@PathVariable`. Pytanie „czy typ domenowy jest pomijany"
   ma więc odpowiedź: nie da się go pominąć, bo nie istnieje. Kontrast: `UserId` ma konstruktor
   walidujący i `of(String)`.

2. **Pomost buduje framework, a application przyjmuje gotowe typy domenowe — odwrotność założenia.**
   `RequireSignInFilter.java:68`, `Caller.java:20-22`, `MemeController.java:81-83,119`,
   `VoteController.java:114-120`, `TagController.java:46-47`. `UserId` powstaje w filtrze i wjeżdża
   do `PublishMeme.execute(byte[], UserId)` jako gotowy typ. `Tag.of(tag)` dla wyszukiwania buduje
   `MemeController:119` i sam łapie `IllegalArgumentException` (`:121`). `VoteDirection` parsuje
   `VoteController.parseDirection` (`:114-120`). Jedyny przypadek, gdzie application coś buduje z
   surowych danych, to `TagMeme.execute(..., List<String> rawTags)` (`TagMeme.java:56-59`) — więc
   ten sam VO (`Tag`) jest budowany raz w kontrolerze, raz w use casie.

3. **Kontrolery mijają `memes-application` i wołają `memes-system` oraz porty domenowe wprost.**
   `MemeController.java:48,311` (`memes.system.DeleteMeme`), `:50,112,252` (`ContentFlags`),
   `TagController.java:63` (`TagRepository.tagsOf`), `AdminController.java:32,46,74,85`
   (`PurgePolicyOverride`). `AdminController` nie ma żadnego use case'u: parsuje
   `PurgeRule.parse(text)` (`:69`) i pisze bezpośrednio do portu domenowego. Trzy z czterech
   kontrolerów omijają application. Commit `ccbafa6` uzasadnia wyniesienie `DeleteMeme` do
   `memes-system` („reachable without the use cases that put content up") — broni się jako
   warstwowanie, ale nie unieważnia faktu, że kontroler woła warstwę poniżej application.

4. **Decyzja „kto może usunąć" leży w kontrolerze, a „kto może oflagować" w use casie.**
   `MemeController.java:301-311` vs `FlagMeme.java:23-25`. `delete` sam sprawdza
   `isOwnedBy(callerId)` i rolę moderatora, potem woła `deleteMeme.execute(id)`. `FlagMeme` dostaje
   `boolean callerIsModerator` i zwraca `NOT_A_MODERATOR`. Javadoc `DeleteMeme.java:14-16`
   uzasadnia: „WHO may do this […] is the boundary's call; this use case is the teardown" — spójne
   samo w sobie, ale nie tłumaczy, czemu bliźniaczy `FlagMeme` robi odwrotnie.

5. **`PurgeUserContent` i `CastVote` degradują `UserId` do `String`, bo port głosów jest kluczowany
   stringiem.** `PurgeUserContent.java:82`, `VoteRepository.java:46`, `VoteController.java:68,75`,
   `CastVote.java:26`. `voteRepository.purgeVoter(author.toString())` z komentarzem „ballots are
   keyed by the voter's id, in its wire form". Przyczyna w shared `voting.Ballots` (same `String`).
   Skutek: use case bierze typ domenowy i sam zamienia go na prymityw — odwrotność pomostu.

6. **Application zależy od konkretnej klasy `WebImageOptimizer`, nie od portu.**
   `PublishMeme.java:25`, `MakeThumbnail.java:31`; `ConcurrencyGuardedImageOptimizer.java:24-28`.
   Javadoc dekoratora przyznaje wprost: „Extends rather than implements because the use cases depend
   on the concrete WebImageOptimizer" — opisuje skutek, nie powód. Port `ImageOptimizer` kosztowałby
   jeden interfejs i zdjął przymus dziedziczenia po klasie z biblioteki.

7. **Trzy porty siedzą w `memes-application`, nie w `memes-domain`.** `ObjectStore.java:11`,
   `ImageEncoder.java:10`, `ContentFlags.java:10`. `ObjectStore` broni się jako port czysto
   techniczny, ale `JdbcMemeRepository:48` (adapter portu DOMENOWEGO) też go używa, więc nie jest to
   szczegół samej application. `ContentFlags` („laid OVER the memes rather than into them") to
   argument za osobnym agregatem, nie za warstwą application. `ImageEncoder` bez uzasadnienia.
   Commit `ccbafa6` przeniósł `MemeEvents` do domain właśnie dlatego, że port w application „would
   have made memes-system depend on the module it sits below" — ta sama logika nie została
   zastosowana do pozostałych trzech.

8. **`RateLimit` w `memes-config` to nie typ konfiguracyjny, lecz działający mechanizm ze stanem.**
   `RateLimit.java:15-58`, wołany w `MemeController.java:85`. `ConcurrentHashMap<String, Window>`,
   `Clock`, eviction i decyzja `tryAcquire`. Javadoc („server policy against abuse […] Pure logic;
   no framework") nie tłumaczy, czemu polityka siedzi obok rekordów `ImageLimits`/`ThumbnailSize`.
   Przy okazji: `TagLimits.java:4` nie waliduje nawet wartości ujemnej, podczas gdy `ImageLimits`,
   `ThumbnailSize` i `ErasureTolerance` walidują.

9. **`Meme` nie pilnuje żadnego niezmiennika; `MemeMetadata` pilnuje jednego.** `Meme.java:8-12`,
   `MemeMetadata.java:43-49`. `Meme` przyjmie `null` id, `null` format, pusty `byte[]`. Brak choćby
   `Objects.requireNonNull` oznacza, że pomost nie ma czego łapać przy budowie encji.

10. **Javadoc domeny odwołuje się w górę, do klas z `memes-application`, i jest miejscami
    nieaktualny.** `MemeRepository.java:25,62`, `VoteRepository.java:19,26`,
    `PurgeUserContent.java:21`. `{@link ServeMeme}`, `{@link MakeThumbnail}`, `{@link RankMemes}`
    nie rozwiążą się w domain (brak zależności). `{@link MemeErasure#pendingOf(String)}` wskazuje
    sygnaturę, której już nie ma (dziś `UserId`).

11. **Słownictwo transportu w domenie: `Observation.SagaCommandDropped(String topic)`.**
    `Observation.java:42`. Rekord domenowy nazywa pole słowem z Kafki. Javadoc typu broni samego
    faktu („no tool could derive on its own"), nie nazwy pola.

12. **README opisuje układ sprzed commitu `ccbafa6`.** `README.md:57,58-60,65-67`. Wymienia
    `DeletedAccount` jako encję (nie istnieje), `PurgeRule` w `memes-config` (jest w shared),
    `DeleteMeme`/`PurgeUserContent` i cztery porty w `memes-application` (są w `memes-system` i
    `memes-domain`). Nie wspomina `memes-system`.

### Różnice wobec wzorca z security

- **Application istnieje i ma kod produkcyjny** — w security to tylko glue Cucumbera. Za to rolę
  „grupowania use case'ów w serwisy" pełni **nic**: każdy use case to osobna klasa z jedną metodą
  `execute`, a kontroler wstrzykuje po 6-8 takich klas (`MemeController.java:42-53`).
- **Brak `Supplier<VO>`** gdziekolwiek w ścieżce use case'ów. Memes buduje VO w pomoście (wariant
  `AuthenticationController`), nigdy nie odracza konstrukcji do use case'u.
- **Brak zapieczętowanych wyników.** Jedyny `sealed` to `Observation` w domain. Use case'y zwracają
  `enum`/`record`, `Optional`, `int` albo `void`. Kontrolery mapują `switch`-em jak w security, ale
  po `enum`, nie po `sealed interface`.
- **Wyjątki płyną przez warstwy jako kanał błędu.** `InvalidImageException` z `memes-image`
  przechodzi przez `PublishMeme` i jest mapowana w `WebErrorHandler:40` na 400; `MakeThumbnail:128`
  rzuca `IllegalStateException`. W security use case zamienia wyjątek konstruktora VO na wariant
  wyniku.
- **Porty w domain, nie w system** — memes trzyma 7 portów w `memes-domain`, security 12 w
  `security-system`. **Memes robi to bliżej reguły właściciela niż sam wzorzec**, z wyjątkiem trzech
  portów w application.
- **Typy konfiguracyjne mają własny moduł** zamiast siedzieć w domain jak w security
  (`implements ConfigValue`); klucze properties żyją w `@Value` w `MemesConfig`, nie w enumie.
- **Transakcyjność przez dziedziczenie po use casie** (`TransactionalDeleteMeme extends DeleteMeme`)
  — use case'y są niefinalne, żeby infra mogła je dekorować. Osobny wzorzec, którego security nie ma.
- **Dwa pomosty w jednym serwisie**: HTTP (filtr + kontrolery budują `UserId`/`Tag`/`VoteDirection`)
  i Kafka (`memes_account-closure` dostaje `ClosureCommand` i sam buduje typ domenowy). Ten drugi
  jest bliższy założeniu.

### Czego NIE znalazłem

- Typu domenowego na id mema (`MemeId`) — nigdzie w serwisie.
- Żadnego `Supplier<VO>` przekazywanego do use case'u.
- Żadnego `sealed interface` jako wyniku use case'u.
- Żadnego importu frameworka (`org.springframework`, `jakarta`, `org.slf4j`, `com.fasterxml`,
  `org.apache.kafka`, `io.micrometer`) w `src/main` modułów `memes-domain`, `memes-config`,
  `memes-system`, `memes-application`, `memes-tags`, `memes-image` — logowanie przez `System.Logger`
  z jawnym komentarzem „deliberately framework-free".
- Use case'u przyjmującego obiekt frameworka (`MultipartFile`, `HttpServletRequest`) — konwersja
  `MultipartFile → byte[]` jest w kontrolerze.
- Use case'u parsującego stringi na typy domenowe poza `TagMeme`.
- **Testu architektury (ArchUnit)** — ani w kodzie, ani w pomach; kierunek zależności trzymają
  wyłącznie pomy.
- Klasy w `memes-application`, która grupowałaby use case'y w „serwis".

---
## 3. `portal/microservice-comments`

**Sprawdzone ręcznie:** `comments-application/src/main` ma 8 klas (6 use case'ów + 2 porty).
Kontroler duplikuje niezmienniki `Comment` (`CommentController.java:79-85` sprawdza pustość i
`MAX_LENGTH`, które rekord sprawdza sam). Oba pomy sagowe deklarują zależność od
`comments-application`, której ani jeden plik `src/main` tych modułów nie importuje.

### Gdzie jest pomost

**Przepływ „napisz komentarz".** `RequireSignInFilter.java:48-54` pyta bramkę security o `Caller`,
a ta buduje `UserId` z tokena przez `Caller.userIdFrom(String)` (`Caller.java:20-25`), łapiąc
`IllegalArgumentException` z `UserId.of` i zamieniając go na `Optional.empty()`. Gotowy `UserId`
ląduje w atrybucie żądania i trafia do kontrolera jako `@RequestAttribute`
(`CommentController.java:75-78`). Kontroler sam sprawdza pustość i długość tekstu (`:79-85`, czyta
`Comment.MAX_LENGTH`), sam odpytuje `RateLimit` (`:86`) i woła
`AddComment.execute(String memeId, UserId author, String text)` (`AddComment.java:27`).
`AddComment` pyta port `MemeDirectory.exists(String)`, losuje id przez
`UUID.randomUUID().toString()`, buduje rekord `Comment` (`:31`) i woła `CommentRepository.save`
(`JdbcCommentRepository.java:33`). Wynik to `Optional<Comment>`; kontroler mapuje `empty` na 404.

`comments-application` **ma `src/main`** — 6 klas use case'ów (`AddComment`, `ListComments`,
`VoteOnComment`, `DeleteComment`, `HideComment`, `CommentWithScore`) i 2 porty (`MemeDirectory`,
`CommentModeration`). **Nie jest pomostem w sensie założenia:** jego klasy przyjmują mieszankę
`String` i gotowego `UserId` zbudowanego w infrastrukturze przez filtr, a jedyny typ domenowy, jaki
same tworzą, to `Comment` — bo poza `Comment` i `UserId` w tym serwisie nie ma innych typów
domenowych do zbudowania. Pomost „prymityw → VO" siedzi w `Caller` i `RequireSignInFilter`, pomost
„zbuduj `Comment` z prymitywów" jest wewnątrz `AddComment`. Wyjątek z konstruktora `Comment` nie
jest nigdzie łapany — kontroler duplikuje reguły, żeby do konstruktora nie doszło.

Use case'y sagowe (`DeleteThread`, `Mark/Restore/PurgeUserComments`, `WatchErasureBacklog`) od
2026-10-02 (commit `9d39550`) siedzą w osobnym `comments-system` POD application; README
(`README.md:6-14`) tego modułu w ogóle nie wymienia.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| comments-domain | odstępstwa | Cztery porty i `Comment` z niezmiennikami są tu zgodnie z regułą, ale id komentarza i id mema płyną jako goły `String`, a `CommentVotes.UnknownComment` i `CommentEvents` niosą wiedzę o magazynie i o choreografii. |
| comments-config | odstępstwa | `ErasureTolerance` to czysty typ konfiguracyjny, ale `RateLimit` to działający mechanizm z `ConcurrentHashMap` i zegarem — ten sam przypadek co w memes. |
| comments-system | zgodny | Pięć use case'ów bez frameworka, biorących `UserId`/`String memeId`/`Optional<PurgeRule>` i zwracających dane zamiast rzucać; żaden nie udaje pomostu, choć `String memeId` to konsekwencja braku typu w domain. |
| comments-application | niezgodny | Ma `src/main`, ale przyjmuje gotowe `UserId` od frameworka, trzyma 2 porty, duplikuje z kontrolerem walidację tekstu i nie ma ani jednego miejsca, gdzie prymityw zamienia się w VO wzorcem `Supplier<VO>` czy `sealed` wynikiem. |

### Odstępstwa

1. **Nie istnieje typ domenowy ani na id komentarza, ani na id mema.** `Comment.java:25`,
   `CommentRepository.java:11-30`, `CommentErasure.java:23`, `CommentEvents.java:28`,
   `AddComment.java:31`, `DeleteThread.java:45`. Id komentarza losowane jako
   `UUID.randomUUID().toString()` w `AddComment` i nigdy nie walidowane; id mema przechodzi z
   `@PathVariable` do SQL bez sprawdzenia. **Jedyna walidacja id mema w serwisie siedzi na drucie
   sagowym** w `MemeDeleted.of(String)` z `portal-libs/meme-deletion` (`Ids.usable`) — czyli Kafka
   dostaje sprawdzony id, a HTTP nie. Powtórzenie odstępstwa nr 1 z memes.

2. **Kontroler duplikuje niezmienniki `Comment` zamiast łapać wyjątek z konstruktora.**
   `CommentController.java:79-85` kontra `Comment.java:32-37`. Pusty tekst i `MAX_LENGTH` są
   sprawdzane dwa razy. Ani application, ani kontroler nie łapie `IllegalArgumentException` z
   `new Comment(...)` — jeśli reguły się rozjadą, konstruktor rzuci i wyjdzie 500. W security łapie
   to biblioteka reguł albo kontroler; tu nikt. Javadoc `Comment` („null-freedom is the boundary's
   responsibility, ADR 0001") uzasadnia brak `requireNonNull`, ale nie podwójną walidację treści.

3. **`comments-application` przyjmuje gotowe `UserId` od frameworka i nie buduje żadnego VO poza
   `Comment`.** `AddComment.java:27`, `DeleteComment.java:30`, `ListComments.java:45`; źródło:
   `Caller.java:20-25`, `RequireSignInFilter.java:53`. `VoteDirection` parsuje kontroler
   (`CommentController.java:211-216`). Javadoc `AddComment.execute` uzasadnia decyzję o 401, nie
   powód, by VO powstawał poza application. Powtórzenie odstępstwa nr 2 z memes.

4. **`VoteOnComment` bierze `String voter`, a sąsiedni `DeleteComment` bierze `UserId caller` — ta
   sama tożsamość w dwóch postaciach w jednym module.** `VoteOnComment.java:25` kontra
   `DeleteComment.java:30`; kontroler rozpakowuje `voter.toString()` w `CommentController.java:171`.
   Komentarz na miejscu („the ballot is keyed by the voter's id, in its wire form") broni się dla
   portu `Ballots`, ale nie dla sygnatury use case'u — application mógłby brać `UserId` i zrobić
   `toString()` przy styku z biblioteką, jak robi `PurgeUserComments.java:95`.

5. **Dwa porty siedzą w application, nie w domain.** `MemeDirectory.java:7`,
   `CommentModeration.java:11`. `CommentModeration` ma nawet wyjątek `UnknownComment` bliźniaczy do
   `CommentVotes.UnknownComment` z domain — ta sama koncepcja, dwa moduły. Javadoc uzasadnia
   oddzielenie od wiersza komentarza, nie lokalizację w application. Odpowiednik odstępstwa nr 7 z
   memes (tam 3 porty, tu 2).

6. **`RateLimit` w `comments-config` to działający mechanizm ze stanem.** `RateLimit.java:14-61` —
   `ConcurrentHashMap<String, Window>`, `volatile lastSweep`, wstrzykiwany `Clock`, polityka
   eksmisji. README nazywa config „the typed dials", javadoc mówi „Pure logic; no framework" — to
   prawda, ale „bez frameworka" nie znaczy „konfiguracja". Identyczny przypadek jak w memes.

7. **Porty w `comments-domain` niosą wiedzę o magazynie i o transporcie.** `CommentVotes.java:17-26`
   („the V3 foreign key"), `CommentEvents.java:5-18` („outbox row", „the broker", „partition key").
   Uzasadnienie przeniesienia do domain się broni (testowalność hopu bez Springa), ale javadoc sam
   mówi, że port istnieje dla kaskady i „no use case calls it" — czyli to port modułów `comments_*`,
   siedzący w domain tylko dlatego, że inaczej powstałby cykl.

8. **`comments-domain` zależy od dwóch bibliotek wbrew README.** `comments-domain/pom.xml` (deps
   `user-id`, `voting`); `README.md:10` mówi „what it may see: the JDK". Oba importy są uzasadnione
   merytorycznie, ale README kłamie o granicy i nic tego nie wychwytuje.

9. **Cztery use case'y, cztery konwencje wyniku.** `DeleteComment.java:17-20` (enum),
   `HideComment.java:16` (rekord), `AddComment.java:27` i `VoteOnComment.java:25-34` (`Optional`, w
   którym `empty` znaczy „nie ma mema" albo „nie ma komentarza" albo „usunięto w trakcie").
   Kontroler mapuje `switch`-em tylko tam, gdzie jest enum. W security wszystkie use case'y zwracają
   `sealed interface` + rekordy.

10. **Moduły sagowe deklarują zależność od `comments-application`, której nie używają.**
    `comments_account-closure/pom.xml`, `comments_meme-deletion/pom.xml`; zero importów
    `comments.application` w ich `src/main`. Po wydzieleniu `comments-system` (`9d39550`)
    zależności nie przycięto. Drobne, ale pomy są jedynym strażnikiem kierunku zależności — i nie są
    czytane po refaktorze.

11. **`CommentRepository.findByMeme(String)` i `countByMeme` nie mają wołającego w kodzie
    produkcyjnym.** `CommentRepository.java:11,17`; jedyne użycia w testach i fejkach. Javadoc
    `countByMeme` sam przyznaje: „Off the listing's hot path — no client used the total." Martwe
    metody portu, które każdy fejk i adapter musi implementować.

### Spójność z security i memes

- **Trzeci wariant, najbliższy memes.** Jak memes: application ma `src/main`, przyjmuje gotowy
  `UserId` z filtra, trzyma część portów, nie ma typu na id, ma `RateLimit` w config. **Inaczej niż
  memes: kontroler NIE omija application** — wszystkie 5 endpointów idzie przez use case
  (`CommentController.java:90,110,151,171,190`), a `comments-system` i porty domenowe woła tylko
  konfiguracja beanów i listenery Kafka. To jedyny serwis, w którym application jest jedynym
  wejściem HTTP.
- **Od security dzieli go wszystko poza jednym:** zero `Supplier<VO>`, zero `sealed` wyników, porty
  nie w system. Wspólne: łapanie `IllegalArgumentException` z konstruktora VO w infrastrukturze
  (`Caller.java:23`, `CommentController.java:214`), nie w application.
- **Pomost sagowy jest tu SŁABSZY niż w memes.** `CommentsClosureParticipant` i
  `CommentsDeletionParticipant` dostają już gotowe `UserId` (`ClosureCommand.userIdOf` wołane w
  `PurgeCommandsListener.java:115`, w infrastrukturze) i gotowy `MemeDeleted`
  (`MemesEventsListener.java:86`). Moduły `comments_*` same nie budują żadnego typu domenowego — są
  orkiestracją unit-of-work i ogłoszenia. Pomost dla sagi siedzi w listenerach Kafka, tak jak
  pomost HTTP siedzi w filtrze.
- **Nowość względem obu:** osobny `comments-system` POD application na use case'y sagowe, z
  `CommentEvents` przeniesionym do domain, „so the new module does not have to reach upwards"
  (`9d39550`). W security porty są w system; tu system ma zero portów, więc **nazwa „system" znaczy
  w tych dwóch serwisach co innego**.

### Czego NIE znalazłem

- Żadnego testu architektury (ArchUnit) — `grep archunit` po pomach i źródłach pusty. README
  deklaruje granice („what it may see: the JDK"), których nic nie egzekwuje i które
  `comments-domain/pom.xml` już łamie.
- Żadnego `Supplier<VO>` w ścieżce use case'ów.
- Żadnego `sealed` wyniku use case'u — jedyny `sealed interface` to `Observation`.
- Żadnego typu `CommentId` / `MemeId` w całym repo ani w `shared/`.
- Żadnego wołania frameworka z `comments-system` ani `comments-application` (jedyny import spoza
  JDK i bibliotek portalu to `org.slf4j` w `ListComments.java:8-9`).
- Żadnej wzmianki o `comments-system` w `README.md`, `Documentation.md` ani `docs/` — moduł istnieje
  tylko w `pom.xml:56` i w treści commita.
- Żadnego endpointu wołającego port lub `comments-system` z pominięciem application (odwrotnie niż
  `AdminController` w memes).

---
## 4. `portal/microservice-user-collections`

**Sprawdzone ręcznie:** `collections-application/src/main` ma 3 klasy (`SaveItem`, `RemoveItem`,
`ListItems`). `collections-system` ma katalog `src/test/java/.../system`, ale **zero plików w nim** —
puste drzewo katalogów, więc teza „zero własnych testów" stoi. `collections-application/pom.xml`
deklaruje `collections-config`, a ani jeden plik `src/main` go nie importuje.

### Gdzie jest pomost

`collections-application` ma `src/main`, ale to **trzy jednolinijkowe use case'y**, które przyjmują
GOTOWE `UserId` i `ItemRef` i przekazują je do portu — nie są pomostem. Pomost siedzi w kontrolerze
`CollectionsApi`.

**Przepływ zapisu.** `PUT /{collection}/items/{itemType}/{itemId}` → `CollectionsApi.java:65-79`
`save()`: najpierw `authenticate()` (`:160-165`) wyciąga bearer, `SecurityGate.callerFor` buduje
`Caller` z `UserId` — wyjątek konstruktora VO łapie `Caller.userIdFrom` (`Caller.java:11-16`,
`catch IllegalArgumentException` → `Optional.empty()` → 401). Potem `tooLongSegmentOf()`
(`:142-153`) sprawdza szerokości kolumn ze schematu (`:41-43`, `MAX_COLLECTION_LENGTH=64`,
`MAX_ITEM_TYPE_LENGTH=64`, `MAX_ITEM_ID_LENGTH=128`) i odpowiada 400 z kodem. Dopiero wtedy
`itemOf()` (`:155-158`) buduje `new ItemRef(itemType, itemId)` z parametrów ścieżki i woła
`saveItem.execute(userId, collection, itemRef)` (`:76`) → `SaveItem.java:22-24` →
`CollectionRepository.add` (port w domain, `CollectionRepository.java:22`) →
`JdbcCollectionRepository.java:33` (jeden adapter implementuje `CollectionRepository` i
`ItemReferences`; odczyty z widoku `active_collection_items`, `:97-101`). Wynik use case'u to
`enum Status { SAVED, ALREADY_SAVED }`, kontroler mapuje na 201/200. Składanie w `Main.java:299-300`.

**Dla Kafki** pomost siedzi w konsumentach i bibliotekach współdzielonych, nie w modułach
`collections_*`: `PurgeCommandsConsumer.java:336-341` parsuje JSON i buduje `ClosureCommand` przez
`ClosureCommand.userIdOf` (tam łapany `IllegalArgumentException`); `CascadeConsumer.java:204-250`
parsuje przez `MemeDeleted.of`/`CommentsDeleted.of` (`meme-deletion/Ids.java:15-25` filtruje
nie-UUID-y). Oba participanty dostają już typy biblioteczne i tylko wołają use case'y z
`collections-system` — jak w comments.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| collections-domain | zgodny | Trzy porty tu i tylko tu, zero frameworku; `ItemRef` bez żadnego niezmiennika, `SavedItem` pilnuje pary status/instant; opaque'ość `itemType` dotrzymana w domain i infrastrukturze. |
| collections-config | zgodny | Jeden rekord `ErasureTolerance` — czysty typ konfiguracyjny z walidacją w konstruktorze, bez stanu; **nie ma odpowiednika `RateLimit`**. |
| collections-system | odstępstwa | Cztery use case'y na `UserId` są wzorcowe; `PurgeDeletedItem` bierze prymitywy i sam sanityzuje wejście; moduł ma zero własnych testów. |
| collections-application | odstępstwa | Ma `src/main`, ale to trzy przelotki na port przyjmujące gotowe VO — pomost jest w kontrolerze; nic jej jednak nie omija. |

### Odstępstwa

1. **`collections-application` nie jest pomostem — przyjmuje gotowe `UserId` i `ItemRef`, buduje je
   kontroler.** `SaveItem.java:22`, `RemoveItem.java:21`, `ListItems.java:21`; budowa VO w
   `CollectionsApi.java:155-158` i `:160-165`. Trzy klasy po jednej linii delegujące do portu.
   README (`:17-19`) uzasadnia moduł tylko klasycznie („compiles without Helidon, JDBC or Kafka") —
   broni się jako granica classpathu, nie jako pomost. Dokładnie przypadek memes i comments.

2. **`PurgeDeletedItem.execute(String, List<String>)` bierze prymitywy i sam filtruje blanki oraz
   duplikaty — sanityzacja wejścia udająca use case.** `PurgeDeletedItem.java:43-55`; port
   `ItemReferences.java:27-30` wprost zakłada, że „caller has already dropped blanks and duplicates
   (see {@link PurgeDeletedItem})". Javadoc broni deduplikacji jako reguły („one event may name the
   same comment twice… a duplicated id would otherwise widen the SQL IN list"), ale nie broni
   MIEJSCA — to robota VO („partia referencji do usunięcia"), którego nie ma, bo tak jak w memes i
   comments nie ma typu na id itemu. Filtr blanków jest w ścieżce Kafka martwy: `CommentsDeleted.of`
   odrzuca nie-UUID-y wcześniej. Dodatkowo javadoc DOMENY linkuje do klasy z warstwy wyżej
   (`ItemReferences.java:27` → `PurgeDeletedItem`) — kierunek zależności złamany w dokumentacji.

3. **`collections-system` ma zero własnych testów — potwierdzone, nie zmieniło się.** `src/test`
   istnieje jako puste drzewo katalogów; pom deklaruje `junit-jupiter` i test-jar domeny
   (`collections-system/pom.xml:36-48`), ale nie ma czego uruchomić. Moduł powstał 2026-10-02
   (jedyny commit: „Taking saved references down gets its own module: collections-system"). Testy
   use case'ów systemowych żyją w `collections-infrastructure/src/test` —
   `application/PurgeDeletedItemTest.java` siedzi w pakiecie `collections.application`, który **nie
   nadąża za przeprowadzką klasy do `system`**. `collections-application` też nie ma własnych testów
   (w `src/test` tylko helper `TestUsers.java`); scenariusze Gherkin jadą z infrastruktury.

4. **`ErasureTolerance` jest typem konfiguracyjnym, którego nikt nie konfiguruje.**
   `ErasureTolerance.java:20-29`; jedyne użycie produkcyjne `Main.java:322` →
   `ErasureTolerance.DEFAULT`. Javadoc uzasadnia miejsce („belongs to whoever answers for the
   obligation — the same reason its twins in memes and comments sit in config") i to się broni, ale
   `Main` nie czyta żadnej zmiennej środowiskowej dla tego progu, więc walidacja z konstruktora
   nigdy nie ma czego łapać. Odwrotność `RateLimit`: nie mechanizm udający config, lecz config bez
   wejścia.

5. **Kontroler jest jedynym miejscem walidacji długości; domena nie ma niezmienników na `ItemRef`
   ani na nazwie kolekcji.** `CollectionsApi.java:41-43,142-153`; `ItemRef.java:9` (rekord bez
   konstruktora kompaktowego). Javadoc `ItemRef.java:7`: „Per the workspace ADR the domain does not
   guard null; the boundary keeps null out", a stałe kontrolera są opisane jako „the schema's column
   widths". Broni się, dopóki 64/128 to szerokości kolumn, nie reguły biznesowe. **Nie ma tu
   duplikacji jak w comments, bo nie ma czego duplikować** — żaden `IllegalArgumentException` z
   `ItemRef` nie istnieje do złapania.

6. **`collections-application` zależy od `collections-config`, którego nie używa.**
   `collections-application/pom.xml:23`; zero importów `collections.config` w `src/main`. Pusta
   krawędź w grafie zależności — drugi taki przypadek po dwóch pomach sagowych w comments.

7. **README opisuje cztery warstwy — nie zna `collections-system` ani `collections_meme-deletion`.**
   `README.md:7-9` („the estate's four layers as four Maven modules (domain / config / application /
   infrastructure)"), `:11-15` wymienia tylko `collections_account-closure`; `pom.xml:57-63` ma
   siedem modułów. Ten sam wzór co w pozostałych trzech.

### Spójność z trzema poprzednimi

- **Powtórzenie wariantu comments, nie czwarty wariant.** Application ma `src/main` z chudymi use
  case'ami na gotowych VO, pomost w kontrolerze, **wszystkie trzy endpointy idą przez application**
  (nic jej nie omija — jak comments, nie jak memes), osobny `system` POD application na use case'y
  sagowe i kaskadowe (nazwa „system" znaczy to co w comments, nie to co w security).
- **Powtarzają się:** brak pomostu w application; brak typu domenowego na id itemu (`String` płynie
  przez `ItemRef`, `ItemReferences.purge` i `PurgeDeletedItem`); zero `Supplier<VO>`; zero `sealed`
  wyników use case'ów; porty w domain (jak memes i comments, nie jak security); README nieaktualne;
  brak ArchUnit.
- **Lepiej niż w comments:** kontroler nie duplikuje niezmienników domeny (bo ich nie ma); łapanie
  `IllegalArgumentException` z `UserId.of` jest w jednym miejscu na ścieżkę; `FakeCollectionRepository`
  leży obok portów w domain i jest trzymany kontraktem (`ItemErasureContractTest`).
- **Lepiej niż w memes i comments:** w config nie ma stanowej maszyny — `ErasureTolerance` to czysty
  rekord.
- **Nowe tutaj:** use case biorący prymitywy i robiący sanityzację (`PurgeDeletedItem`); typ
  konfiguracyjny bez jakiegokolwiek źródła konfiguracji; `system` powstały tego samego dnia bez
  żadnego testu i z testem w nieaktualnym pakiecie `application`.

### Czego NIE znalazłem

- Żadnego miejsca, które czyta `itemType` z powrotem i na nim rozgałęzia — stałe `"meme"`/`"comment"`
  istnieją tylko w `CollectionsDeletionParticipant.java:21-22` jako mapowanie zdarzenia na klucz;
  domain, application, system i adaptery JDBC traktują parę jako opaque. **Obietnica z javadoca
  `ItemRef` jest dotrzymana** — jedyna taka obietnica w tym przeglądzie, która się trzyma.
- Importów Helidon/JDBC/Kafka/Jackson w `collections-domain`, `-config`, `-application`, `-system`;
  jedyne zależności spoza JDK to `identity.UserId` i `observation.Observations`.
- Portów poza domain — `CollectionRepository`, `ItemReferences`, `ItemErasure` są tylko tam;
  application i system nie deklarują własnych interfejsów. **Jedyny serwis portalu, w którym żaden
  port nie wyciekł do application.**
- Kontrolera omijającego application; `CollectionsApi` nie widzi `collections-system` w ogóle.
- `Supplier<VO>` przekazywanego do use case'u (jedyny `Supplier` to cache JWKS).
- `sealed` wyniku use case'u.
- Testu architektury (ArchUnit).
- Jakiegokolwiek pliku w `src/test` modułu `collections-system`.
- Odczytu zmiennej środowiskowej dla `ErasureTolerance`.

---
## 5. `portal/microservice-offboarding`

**Sprawdzone ręcznie:** serwis ma cztery moduły i **żadnego `-config`**. `EventsRouter` w
`offboarding-application` importuje Jacksona (`JsonNode`, `ObjectMapper`, `ArrayNode`,
`ObjectNode`). `offboarding-system/pom.xml` deklaruje `observation` i `account-closure`, a ani jeden
plik `src/main` tego modułu ich nie importuje. Istnieje
`offboarding-infrastructure/src/test/.../ObservabilityIsOptionalTest.java` — jedyny test w
posiadłości, który chodzi po cudzym drzewie źródeł w imię warstw.

### Gdzie jest pomost

**Jeden przebieg.** `KafkaLoop.consume` (`KafkaLoop.java:290-315`) polluje rekord, mapuje topic na
`Source` przez `SagaTopics.senderOf` (`:50-56`) i oddaje **surowy string JSON** do
`EventsRouter.handle(Source, String)` (`EventsRouter.java:176-196`). Router sam parsuje:
`Envelope.read(payload, mapper)` (`:177`), w `onDeletionRequested` (`:269-325`) czyta
`email`/`id`/`sagaId`/`policy`/`userId` przez `Outcome<...>` z biblioteki `envelope`, odrzuca poison
pill po kodach błędów (`:275-281`), normalizuje `initiatedBy` przez `ClosureInitiator` (`:300`) i
woła `begin.execute(UUID, String, String, UUID, String, UUID, Instant)` (`:303-304`).
`BeginOffboarding.execute` (`BeginOffboarding.java:73-86`) pakuje te prymitywy w rekord `Opening` i
woła port `SagaStore.start` (`SagaStore.java:37`), a przy pustym kworum `complete` (`:83`).
`JdbcSagaStore.start` (`:60-112`) robi read-then-insert z adopcją cudzej sagi po 23505. Router
buduje z `Begun` komendę `PURGE_USER_CONTENT` jako JSON (`EventsRouter.java:381-463`), pętla wysyła
ją pod topic z `SagaTopics.topicFor`, robi `flush`, `settleDeliveries` (`KafkaLoop.java:392-442`),
dopiero potem `commitSync`. Potwierdzenia idą tą samą drogą przez `onConfirmation`
(`EventsRouter.java:337-379`) → `RecordConfirmation.execute` (jednolinijkowa przelotka) →
`JdbcSagaStore.confirm` (`:150-195`), gdzie siedzi kworum i zatrzask (`:185-191`).

`offboarding-application` jest **jednym i drugim, ale asymetrycznie**: jest pomostem w sensie
założenia (JSON → prymitywy → use case), tyle że **nie ma po drodze ani jednej klasy domenowej do
zbudowania**, bo domena ich nie ma. Jest też mechanizmem, ale tylko jego górną połową: choreografią
(co, komu, w jakiej kolejności, deterministyczne id outcome'u, znaczniki outboxu). Dolna połowa
mechanizmu — zatrzask STARTED→COMPLETED, kworum, dedup po `fact_id`, licznik retry — siedzi w
implementacjach `SagaStore`, czyli **w infrastrukturze**.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| offboarding-domain | odstępstwa | Port `SagaStore` jest tu i tylko tu, zero zależności kompilacyjnych, ale to worek na stan: siedem rekordów bez jednego niezmiennika, stan sagi i inicjator jako `String`, polityka jako surowy JSON w `String`. |
| offboarding-system | odstępstwa | Trzy use case'y na prymitywach nad portem z domeny — „system" znaczy tu „wszystkie use case'y", **trzecie znaczenie** tego słowa w posiadłości; pom deklaruje dwie zależności, których moduł nie importuje. |
| offboarding-application | odstępstwa | Jedyna w posiadłości application wołana na produkcji: sama parsuje JSON Jacksonem i sama go buduje, mapuje `Source` na use case, trzyma choreografię — pomost bez typów domenowych po drugiej stronie. |
| (brak `-config`) | zgodny | Timeout, retry, okno republikacji i retencja to stałe `SweepOverdue.DEFAULT_*` w **system**, przepisane w `Main.java:168-171`; topologia i zegary w infrastrukturze; decyzja świadoma, uzasadniona na miejscu i przetestowana (`SagaTimingContractTest`). |

### Odstępstwa

1. **Domena process managera nie ma własnych pojęć — ma tylko dane sagi.** `Opening.java:22`,
   `Recorded.java:19`, `PendingOutcome.java:16`, `Compensated.java:14`, `Retry.java:19`. Rekordy bez
   konstruktorów kompaktowych: `state` to `String` porównywany literałem (`EventsRouter.java:244`
   `"COMPLETED".equals(...)`), `initiatedBy` to `String` mimo istnienia `ClosureInitiator` w
   `account-closure`, `policy` to zserializowany JSON w `String`. Nie ma `SagaId`, `Participant`,
   `SagaState`. Javadoc uzasadnia: „it is a process manager, and the saga IS its model"
   (`Observation.java:13-14`). Broni się połowicznie — tłumaczy brak agregatu treści, ale nie to,
   czemu stan sagi i kworum nie są typami.

2. **Reguły biznesowe sagi żyją w implementacjach portu, nie w domenie ani w use case'ach.**
   `JdbcSagaStore.java:185-191` (kworum), `:203-212` (zatrzask), `:480-495` (`adoptSelfRequest`:
   SELF nadpisuje ADMIN i kasuje politykę), `FakeSagaStore.java:108-110` (**ta sama reguła drugi
   raz**). Javadoc portu wyspecyfikował idempotencję, zatrzask i warunkowe ładowanie licznika, ale
   egzekwuje je adapter JDBC i fejk, zszyte wyłącznie `SagaStoreContractTest`. Reguła SELF-nad-ADMIN
   używa `ClosureInitiator` w adapterze, podczas gdy domena trzyma to pole jako `String`.
   **To odwrócenie założenia: port w domenie, mechanizm w infrastrukturze.**

3. **Application przyjmuje i produkuje surowy JSON — Jackson jest tu zależnością kompilacyjną.**
   `EventsRouter.java:13-16`, `:142` (`ObjectMapper` jako pole), `:177`, `:440-463`, `:475-496`. Pom
   application mówi „knows no transport" i to prawda dla Kafki, ale format wiadomości jest
   transportem. Javadoc uzasadnia: „pure and broker-free so the Gherkin scenarios and the pact tests
   drive it directly" (`:37-38`) — broni się jako decyzja testowa; jako warstwowanie oznacza, że w
   security tę robotę robi kontroler w infrastrukturze, a tu moduł nazwany application.

4. **Wyniki use case'ów nie są `sealed`; trójstan zakodowany w dwóch booleanach albo
   `Optional`+boolean.** `BeginOffboarding.java:30-31` (`Begun(sagaId, nothingToPurge, completedNow)`),
   `RecordConfirmation.java:28`. Javadoc `Begun` tłumaczy słownie, kiedy która kombinacja co znaczy,
   a router odtwarza to `if`-ami (`EventsRouter.java:305-321,356-365`). Wzorzec z security (wariant +
   `switch`) był na wyciągnięcie ręki.

5. **Drabinka przeciążeń z `null` jako znaczeniem domenowym.** `BeginOffboarding.java:41-74` (sześć
   `execute`), `Recorded.java:22-40` (pięć konstruktorów „pre-X spelling"), `SagaStore.java:40-52`
   (trzy `default start`, w tym „Test seeding"). `null participants` = „nie zapisano",
   `null initiatedBy` = SELF, `null securitySagaId` = stary producent. Javadoc nazywa to
   kompatybilnością z wierszami sprzed migracji V3/V5/V6/V7. Każda warstwa pozwala na te same
   dziury, więc kompilator nie wie, która ścieżka jest produkcyjna, a która to szew testowy.

6. **Dwie zależności w pomie `system` bez ani jednego importu w `system` — a konsumuje je
   `application`, która ich nie deklaruje.** `offboarding-system/pom.xml:29-41` (`observation`,
   `account-closure`), `offboarding-application/pom.xml:25-53` (tylko `offboarding-system` i
   `envelope`), `EventsRouter.java:3-4,20` (importy `closure.*`, `observation.*`). Komentarz w pomie
   mówi, że stałe „were a final class of constants here" — już nie są. **To gorszy wariant niż
   zwykła martwa krawędź z pozostałych serwisów**: usunięcie jej w `system` wywróci kompilację
   piętro wyżej.

7. **README i pom `system` opisują układ sprzed commitu `8ab9ae9`.** `README.md:48` (klasa
   `RequestedBy` w domain — nie istnieje nigdzie w repo), `README.md:49-50` i
   `offboarding-system/pom.xml:7-8` („Plus the SagaStore port they work over" — port jest w domain).

8. **`retryDelivered` dostaje zegar systemowy, choć javadoc portu żąda zegara wywołującego.**
   `KafkaLoop.java:430-431` (`Instant.now()`) wobec `EventsRouter.java:205,304,350`
   (`Instant.now(clock)`) i `SagaStore.java:112-115` („The stamp is the CALLER's instant… a second
   clock would put skew straight into that budget"). Na produkcji oba to UTC, więc nic się nie
   dzieje; w teście z zegarem sterowanym cutoff i stempel rozjeżdżają się dokładnie tak, jak javadoc
   ostrzega. Niezmiennik trzymany konwencją, nie okablowaniem.

9. **Stałe deploymentu w module use case'ów — uzasadnienie dobre dla dwóch z czterech.**
   `SweepOverdue.java:30-53`, `Main.java:157-171`. Dla timeoutu i liczby retry javadoc wygrywa:
   „HALF OF A CONTRACT WITH SECURITY", zmierzone 2026-08-08, pilnowane przez
   `SagaTimingContractTest.java:37-49`. Dla `DEFAULT_RETENTION = 30 dni` i `DEFAULT_REPUBLISH_AFTER`
   argument „kontrakt z security" nie stosuje się — **retencja danych osobowych to decyzja prawna i
   deploymentowa**, nie arytmetyka z sąsiadem; usunięto ją z env razem z tamtymi, bo „nobody ever
   set one".

10. **Pozostałości kopiuj-wklej po rozcięciu `SagaStore`.** `Compensated.java:3-5`, `Opening.java:3-4`,
    `PendingOutcome.java:3-5`, `Recorded.java:3-5`, `Retry.java:3-5`, `SweepResult.java:3-7` —
    nieużywane importy w rekordach wyciętych z jednego pliku; `SagaStore.java:54-66` ma **dwa
    javadoki nad jedną metodą** `confirm`, z których pierwszy opisuje stary parametr `required`.

### Spójność z czterema poprzednimi

- **Jedyny serwis, w którym application jest na ścieżce produkcyjnej** i jedyny, w którym
  application rzeczywiście robi to, co założenie nazywa pomostem: dostaje `String`, parsuje, woła
  use case. Pod tym względem jest **bliżej** założenia niż memes, comments i collections, gdzie
  application dostaje gotowe VO.
- Jest zarazem **dalej**, bo pomost nie ma dokąd prowadzić: nie ma klas domenowych, których
  konstruktory mogłyby rzucić. Pytanie „gdzie łapane są wyjątki z konstruktorów VO" ma odpowiedź
  **„nigdzie, bo nie ma VO"**. Rolę biblioteki reguł z security pełni `Envelope`/`Outcome` (sealed,
  kody błędów), a „odpowiedzią błędu" jest zrzucenie rekordu jako poison pill z WARN-em —
  strukturalnie ten sam ruch co `Supplier<VO>` plus biblioteka reguł, tylko bez typu po stronie
  domeny.
- **`system` znaczy tu trzecią rzecz:** nie porty (security), nie use case'y sagowe pod application
  (comments, collections), lecz WSZYSTKIE use case'y — a application nie ma żadnego, jest wyłącznie
  centralką.
- **Miara właściciela da się przyłożyć, ale mierzy co innego.** W serwisie treści sprawdza, czy
  framework nie dotyka agregatu; w process managerze agregatem jest saga, a saga jest w bazie.
  Dlatego mechanizm wylądował w `JdbcSagaStore`, a domena została opisem rekordów — to nie przypadek,
  lecz konsekwencja braku modelu stanu sagi w domenie. Założenie ma tu zastosowanie dokładnie w tym
  punkcie: **gdyby `SagaState`, kworum i zatrzask były typem w domain, i application, i adapter
  stałyby się cieńsze.**
- Wspólne z czwórką: brak `Supplier<VO>`, brak `sealed` wyników, README o refaktor do tyłu, martwa
  (tu: tranzytywnie nośna) krawędź w pomie. **Nowe: pierwszy w posiadłości test pilnujący warstw** —
  `ObservabilityIsOptionalTest.java:16-70` chodzi po `src/main/java` trzech górnych modułów i szuka
  słów vendora (`micrometer`, `prometheus`, `_total`…). To nie ArchUnit i nie pilnuje kierunku
  zależności, ale to jedyny test w posiadłości, który czyta cudze drzewo źródeł w imię warstw.

### Czego NIE znalazłem

- Modułu `-config` ani klasy `*Config`/`*Properties` — wartości są stałymi w `SweepOverdue` i `Main`.
- Typów domenowych z niezmiennikami (`SagaId`, `Participant`, `SagaState`, `Email`) — **zero
  konstruktorów kompaktowych w `offboarding-domain`**.
- `Supplier<...>` w kodzie produkcyjnym.
- `sealed` wyniku use case'u — `sealed` tylko na `Source` i `Observation`.
- `IllegalArgumentException` łapanego gdziekolwiek na ścieżce wiadomości — rzucany tylko przy
  parsowaniu zmiennych środowiskowych w `Main`/`KafkaLoop`.
- ArchUnit w żadnym pomie ani teście.
- Klasy `RequestedBy` z README.
- Portu poza `offboarding-domain` — `SagaStore` jest jedynym portem serwisu.
- Kontrolera HTTP — poza `/health`, `/alive`, `/metrics` nic nie wchodzi po HTTP, więc pomost w
  stylu security (kontroler w infrastrukturze) **nie ma gdzie powstać**.

---
## 6. `shared/email` + `shared/password` — biblioteki, w których żyje mechanizm pomostu

**Sprawdzone ręcznie, i to jest korekta checklisty TEGO pliku:** z modułów wypisanych wyżej przy
punkcie 6 **trzy nie istnieją**. `email/email-security`, `password/password-security-config` i
`password/password-security-system` (oraz `hash-algorithms/hash-algorithm-contract`) nie mają
`pom.xml`, nie są w `<modules>` i zawierają tylko ignorowane `target/`. `email/pom.xml` ma trzy
moduły (`email-domain`, `email-config`, `email-usecase`), `password/pom.xml` cztery
(`password-domain`, `password-config`, `password-usecase`, `hash-algorithms`). Potwierdzone też:
mechanizm `Outcome` nie mieszka w żadnej z tych bibliotek, lecz w artefakcie `constraint` w
`shared/libs`. Oba poma-rodzice uruchamiają `maven-dependency-plugin:analyze-only` z
`failOnWarning=true`.

### Mechanizm `Supplier<VO>` + `Outcome` od środka

Maszyneria siedzi w trzeciej bibliotece: artefakt `constraint` w `shared/libs` — pięć klas, **zero
testów** (brak `libs/src/test`). `Outcome` jest `sealed`: `Outcome.java:7`
(`public sealed interface Outcome<T>`) z czterema rekordami `Allowed`, `AllowedWithWarning`,
`Rejected`, `RejectedDueToInvariantBreakage` (`:35-38`) i trzema domyślnymi metodami na `switch` z
wzorcami. `Constraints<T>.validate(Supplier<T>)` (`Constraints.java:30-56`) robi dwie rzeczy:
najpierw

```java
try { candidate = candidateSupplier.get(); }
catch (Exception e) { return new Outcome.RejectedDueToInvariantBreakage<>(
        Collections.singletonList(e.getMessage())); }
```

— łapie **każdy** wyjątek z konstruktora VO i wkłada do wyniku jego **zdanie**, nie kod; potem
przepuszcza gotową wartość przez listę `ErrorConstraint<T>` (każdy niespełniony daje `code()` →
`Rejected`) i `WarningConstraint<T>` (→ `AllowedWithWarning`). Kody błędów polityki to stałe `CODE`
w każdym `_XConstraint` (`_RfcFormatConstraint.java:19`, `_BlockedDomainConstraint.java:14`,
`_MinLengthConstraint.java:12`); kody niezmienników adresu to siedem stałych w
`InvalidEmailException.java:19-31` (`EMAIL_BLANK`, `LOCAL_PART_CONSECUTIVE_DOTS`,
`DOMAIN_MISSING_DOT`…), rzucanych z `Email.of`, `LocalPart.of` i `DomainPart.of`.

**Ścieżka adresu.** `SecurityController.java:106`
`register.execute(() -> Email.of(email), () -> PlaintextPassword.of(password))` →
`Register.java:26-29` przekazuje `Supplier<Email>` do `_EmailVerdict.judge` →
`_EmailVerdict.java:14-19` buduje `CanRegister` builderem → `CanRegister.java:29-31`
`return _EmailCandidate.evaluate(constraints, email);` → `_EmailCandidate.java:25-33`:

```java
Email email;
try { email = candidate.get(); }
catch (InvalidEmailException refused) {
    return new Outcome.RejectedDueToInvariantBreakage<>(List.of(refused.code()));
}
return constraints.validate(() -> email);
```

To jest **cała** realizacja założenia właściciela: supplier odpala konstruktor VO dopiero w
bibliotece use case'u, wyjątek z kodem staje się wariantem `sealed` wyniku z tym kodem, a do
generycznego `Constraints.validate` trafia już zbudowana wartość w lambdzie, która nie może rzucić.
Javadoc `_EmailCandidate.java:15-19` sam tłumaczy, czemu tłumaczenie jest tutaj: „this is the only
place that knows both vocabularies: the generic machinery stays generic, and `email-domain` keeps
its zero dependencies" — i to się broni.

**Ścieżka hasła nie ma tego kroku.** `_PasswordVerdict.java:17` → `CreatePasswordHash.java:30-33`
`Outcome<PlaintextPassword> result = constraints.validate(password); return result.map(hashAlgorithm::hash);`.
`PlaintextPassword.of` rzuca gołe `IllegalArgumentException("Password must not be blank")`
(`PlaintextPassword.java:19-21`), więc puste hasło ląduje w
`RejectedDueToInvariantBreakage(["Password must not be blank"])` — **zdanie obok kodów**, dokładnie w
sytuacji, którą javadoc `InvalidEmailException.java:6-11` opisuje jako naprawioną („half of it
readable only by a person, half only by a client"). Naprawiono ją tylko dla adresu (`00b1593`).

**Werdykt o generyczności: maszyneria jest generyczna, tłumaczenie jest przyszyte.**
`Constraint<T>`/`Constraints<T>`/`Outcome<T>` nie znają ani adresu, ani hasła. Ale w artefakcie
`constraint` nie ma żadnego interfejsu „wyjątek z kodem", więc zamiana wyjątku na kod to ręczna,
pakietowo-prywatna klasa `_EmailCandidate` związana z konkretnym `InvalidEmailException` — dla hasła
nikt jej nie napisał. Każde kolejne VO potrzebuje własnego wyjątku z `code()` i własnego
`_XCandidate`. **Pierwszy konsument i tak spłaszcza rozróżnienie:** `EmailErrorCodes.of(outcome)` →
`outcome.errorCodes()` (`EmailErrorCodes.java:16-18`), a `RegistrationAttempt.java:25-28` sprawdza
tylko `accepted().isEmpty()` — `Rejected` i `RejectedDueToInvariantBreakage` są nierozróżnialne już
w `security-system`.

### Werdykt per moduł

| moduł | zgodność | jednym zdaniem |
|---|---|---|
| email-domain | zgodny | Pom bez jednej zależności, cztery finalne VO z fabrykami `of` rzucającymi `InvalidEmailException` z kodem, zero portów, zero frameworka; jedyna rysa to `LocalPart.normalize(String domain)` z `// todo should be DomainPart` (`:50`). |
| email-config | odstępstwa | Trzy rekordy `Set<DomainPart>` z gardą i `CanRegisterConfig` z `null` jako „regułą nieobecną"; rekord sam implementuje port `EmailConfigPort` z `config/port`, którego nikt inny nie implementuje; zależność na `email-domain` w scope `provided`. |
| email-usecase | odstępstwa | `CanRegister` i `_EmailCandidate` to wzorcowa realizacja pomostu; ale port `MxRecordPort` siedzi w usecase (`email.external`), nie w domain, i **nie ma implementacji nigdzie**, a `CanResetPassword` nie ma konsumenta poza własnymi testami. |
| email-security | niezgodny | **Nie moduł, lecz widmo:** skasowany w `6d6031b` (2026-09-02), nieobecny w `<modules>`, na dysku zostały ignorowane `target/` i `.allure/` z 2026-04-28. |
| password-domain | zgodny | Dwa VO i port `HashAlgorithmPort` w domain (tak jak repozytoria w założeniu), zero zależności, `toString` zredagowany; ale `PlaintextPassword.of` rzuca prozę bez kodu, a `HashedPassword` ma publiczny konstruktor bez żadnego niezmiennika. |
| password-config | zgodny | Pięć rekordów `implements ConfigValue<T>` z `KEY`, `DEFAULT` i gardą; **`ConfigValueLawTest` czyta źródła pakietu i wymusza statyki, których kompilator wymusić nie może**; żadnej maszyny stanowej. |
| password-usecase | odstępstwa | `CreatePasswordHash.create(Supplier)` → `Outcome.map(hash)` i `PasswordPolicy.constraints()` są czyste; brak `_PasswordCandidate` oznacza, że niezmienniki hasła wracają jako zdanie, nie kod; port `PasswordPolicyInForce` siedzi w usecase. |
| hash-algorithms | odstępstwa | Port w `password-domain`, kontrakt jako abstrakcyjny test w test-jarze, adapter `Argon2HashAlgorithm` w `argon2` — **poprawny układ**; ale `argon2-config` nie zna `ConfigValue` ani `KEY` (inna konwencja niż `password-config`), a `OptimalNumberOfIterations` z `main()` leży w `src/main`. |
| password-security-config | niezgodny | Widmo: skasowany w `ae7026c` (2026-05-07, „Clear poms and code"); zawartość żyje w `password-config`. |
| password-security-system | niezgodny | Widmo jak wyżej; zastałe klasy `CreatePasswordHash$PasswordHashCreation$Created.class` pokazują, że przed `Outcome` use case miał własny `sealed` wynik. |

### Odstępstwa

1. **Trzy z dziesięciu „modułów" z listy przeglądu nie istnieją.** `email/email-security/`,
   `password/password-security-config/`, `password/password-security-system/` oraz
   `hash-algorithms/hash-algorithm-contract/` to katalogi z samym `target/`, ignorowane przez
   `.gitignore`, nieobecne w pomach-rodzicach. Usunięte z gita: `email-security` w `6d6031b`
   (2026-09-02), pozostałe w `ae7026c` (2026-05-07). **Ten sam objaw co README serwisów, tylko że
   tutaj dotknął mojej własnej listy modułów** — spis warstw w posiadłości jest o refaktor do tyłu
   nawet wtedy, gdy się go robi z `ls`.

2. **Słowo „system" w bibliotekach to nie czwarte znaczenie — to trzecie, i jedyne miejsce, gdzie je
   WYCOFANO.** W `password-security-system` i `email-security-system` (stan sprzed 2026-09-02)
   leżały `CreatePasswordHash`, `PasswordPolicy`, `CanRegister` i constrainty — czyli wszystkie use
   case'y biblioteki, co odpowiada znaczeniu z offboarding. Commit `32e5735` („warstwa system
   zostaje ochrzczona usecase") i `9ed45e3` („The spine is flat and drops the word security: domain,
   config, usecase") przemianowały je na `*-usecase`. **Biblioteki są jedynym miejscem posiadłości,
   które z tego słowa zrezygnowało**; `microservice-security` nadal ma `security-system`.

3. **Tłumaczenie wyjątek→kod istnieje tylko dla adresu.** `_EmailCandidate.java:25-33` kontra
   `CreatePasswordHash.java:30-33`; `PlaintextPassword.java:19-21`. **Żaden test w `email`,
   `password`, `libs` ani `microservice-security` nie pokrywa wariantu
   `RejectedDueToInvariantBreakage`** — jedyny test kodów niezmienników to `LocalPartTest.java:66-75`
   na poziomie VO. Czyli najważniejszy element wzorca nie ma testu na swoim wariancie wyniku.

4. **`Constraints.validate` łapie `Exception`, nie wyjątek domenowy.** `Constraints.java:35`
   `catch (Exception e)` — NPE czy błąd programisty w supplierze też stanie się „odmową z powodu
   złamanego niezmiennika" z komunikatem (dla NPE: `null`). `_EmailCandidate` łapie wyłącznie
   `InvalidEmailException` i resztę puszcza dalej; **dwie ścieżki, dwie semantyki tego samego
   wariantu `Outcome`**.

5. **Format adresu jest sprawdzany w dwóch warstwach dwoma słownikami.** Niezmienniki strukturalne w
   domain (kody `InvalidEmailException`), regex RFC w usecase jako zawsze obecny
   `_RfcFormatConstraint` (`CanRegister.java:37-39`). Komentarz `LocalPart.java:22-24` przyznaje, że
   regex downstream nie łapie `a..b` i że „did, right up to a created account" — domena dostała
   regułę, bo polityka ją przepuściła.

6. **Porty w trzech różnych warstwach.** `HashAlgorithmPort` w `password-domain` (zgodnie z
   założeniem), `MxRecordPort` w `email-usecase/…/email/external`, `PasswordPolicyInForce` w
   `password-usecase`, `EmailConfigPort` w `email-config/port`. `MxRecordPort` **nie ma żadnej
   implementacji w posiadłości**; `EmailConfigPort` implementuje wyłącznie rekord `CanRegisterConfig`,
   czyli port z jedną implementacją będącą jego własną wartością.

7. **Martwy kod publiczny:** `CanResetPassword.java:14` — publiczny konstruktor na liście
   constraintów, bez buildera, bez konsumenta poza własnym testem; `OptimalNumberOfIterations.main`
   (`:12-22`) — narzędzie deweloperskie w `src/main` artefaktu produkcyjnego.

8. **Dwie konwencje konfiguracji w jednej bibliotece.** `password-config` — rekordy
   `implements ConfigValue<T>` z `KEY`/`DEFAULT`/`holding()`, pilnowane prawem
   `ConfigValueLawTest.java:35-58`; `argon2-config` — rekordy `int` z `MIN`/`MAX`/`DEFAULT` jako
   `int`, builder na prymitywach, bez klucza i bez `ConfigValue`. `password/todo.md:34-35` uzasadnia:
   „argon2 NIE potrzebuje drabinki (parametry hashowania zmienia się przez rehash, nie na żywo)" —
   broni braku drabinki, nie broni braku klucza.

9. **Javadoc o jeden refaktor do tyłu także w `libs`:** `Constraints.java:13-14` mówi o metodzie
   „`#decide(Object)`", która od `966efb0` nazywa się `validate(Supplier)`.

10. **`AbstractEmail.equals` zrównuje typy.** `AbstractEmail.java:13-18` porównuje przez
    `instanceof AbstractEmail` i `value()`, więc `Email` i `NormalizedEmail` o tym samym tekście są
    `equals` — **dwa typy, które istnieją po to, by ich nie mylić** (jeden do pokazywania, drugi do
    deduplikacji), są nierozróżnialne w `Set`/`Map`.

11. **Granice są pilnowane lepiej niż w serwisach — ale przez Mavena, nie przez test.** Oba
    poma-rodzice (`email/pom.xml:94-96`, `password/pom.xml:100-102`) uruchamiają
    `maven-dependency-plugin:analyze-only` z `failOnWarning=true` w fazie `verify` —
    zadeklarowana-nieużyta lub użyta-niezadeklarowana zależność wywala build. **Po lekturze
    wszystkich 11 pomów nie ma ani jednej martwej krawędzi**, w przeciwieństwie do czterech z pięciu
    serwisów. Jedyny test „nad pakietem" to `ConfigValueLawTest`; ArchUnit nie ma, a
    `libs/constraint` nie ma żadnego testu. (Build nie był uruchamiany — audyt czytający.)

### Czy serwisy treści mogłyby przyjąć ten mechanizm

Technicznie tak, i taniej, niż sugeruje jego rzadkość. `Constraints<T>` i `Outcome<T>` są w pełni
generyczne i siedzą w osobnym artefakcie `constraint`, od którego **żaden z czterech serwisów
portalu dziś nie zależy** (jedyny konsument w portalu to `account-closure-2/pom.xml`) — pierwszy
krok to jedna krawędź w pomie.

Ścieżka minimalna to wariant hasła: use case przyjmuje `Supplier<VO>`, woła
`constraints.validate(supplier)` i oddaje `Outcome<T>`. Kosztem jest to, że
`IllegalArgumentException` z `MemeMetadata`, `Comment` czy `SavedItem` (wszystkie trzy rzucają
prozę) wróci jako zdanie w `RejectedDueToInvariantBreakage`, nie jako kod.

Ścieżka pełna (wariant adresu) wymaga per serwis dwóch rzeczy: wyjątku domenowego z `code()` jak
`InvalidEmailException`, oraz ręcznej klasy `_XCandidate` — bo w `constraint` **nie ma interfejsu
„wyjątek z kodem"**, który pozwoliłby `Constraints` zrobić to samemu. To jedyna nie-generyczna
rzecz w całym mechanizmie i dziś jest pakietowo-prywatna w `email.policy`.

Przeszkody po stronie serwisów są dwie, obie z ustaleń punktów 2-4 tego przeglądu. W memes **nie ma
typu na id mema**, więc nie ma czego dostarczyć w `Supplier<MemeId>`. W memes i collections VO buduje
filtr przed kontrolerem, więc supplier musiałby owijać gotową wartość (wtedy nic nie łapie) albo
filtr musiałby odłożyć konstrukcję. **W comments mechanizm trafia dokładnie w problem:** tam
kontroler duplikuje niezmienniki encji i nikt nie łapie wyjątku z konstruktora, a
`Supplier<Comment>` plus `Constraints.validate` zastępuje ten duplikat jednym miejscem łapania.

Ostatni warunek to konsument wyniku: `sealed Outcome` ma sens tylko, jeśli ktoś robi `switch` po
wariantach — a nawet `security-system` spłaszcza go w `errorCodes()` już w `RegistrationAttempt`.
Brak `Outcome` jako zależności jest więc przeszkodą najmniejszą; brak typów na id i brak wyjątków z
kodem — rzeczywistymi.

### Czego NIE znalazłem

- ArchUnit ani żadnego testu pilnującego warstw w `email`, `password`, `libs`, `test-starter`.
- **Żadnego testu dla `libs/constraint`** — katalog `libs/src/test` nie istnieje.
- Testu wariantu `RejectedDueToInvariantBreakage` w którejkolwiek bibliotece ani w
  `microservice-security`.
- Implementacji `MxRecordPort` i konsumenta `CanResetPassword` poza `email`.
- Implementacji `EmailConfigPort` innej niż sam rekord `CanRegisterConfig`.
- README w `email/` ani `password/` (jedyna dokumentacja to `password/todo.md` i
  `argon2-config/TODO.md`); ani `PropertiesConfigSource`, o którym mówi ten drugi.
- **Stanowej maszyny udającej konfigurację** (odpowiednika `RateLimit`) — wszystkie typy w
  `email-config`, `password-config` i `argon2-config` są niemutowalnymi rekordami.
- Importu frameworka w którymkolwiek module poza `de.mkammerer.argon2` w adapterze `argon2`.
- `src/main` ani `pom.xml` w `email-security`, `password-security-config`,
  `password-security-system`, `hash-algorithm-contract`.

---
## ANEKS 2026-10-02: co zostało z tego raportu po adwersaryjnej weryfikacji

128 agentów. Każde z 58 odstępstw przeszło przez **dwóch niezależnych oponentów**: jeden sprawdzał
wyłącznie fakty (otwierał każdy cytowany plik i linię, z mandatem obalenia), drugi atakował sam
wniosek (czy to w ogóle odstępstwo od założenia właściciela i czy uzasadnienie w javadocu na miejscu
się broni). Osiem agentów zrobiło **przekrojowe sweepy po całej posiadłości**, po jednym pytaniu
każdy, jednym przejściem — czyli to, co pierwszy przebieg sumował z sześciu lokalnych grepów.
Trzech krytyków szukało luk. Wszystkie tezy nośne tego aneksu sprawdzone ręcznie, ponad agentami.

**Wynik w jednym zdaniu: ten plik nie jest listą odstępstw od założenia właściciela.** Jest listą
58 uwag o jakości, z których 24 mają błędne cytaty albo liczby, pięć twierdzeń przekrojowych jest
nieprawdziwych, a nośna teza „założenie jest zrealizowane w jednym miejscu" jest wypowiedziana nad
ćwiercią kodu, której nikt nie otworzył.

| | |
|---|---|
| odstępstw zważonych | 58, utraconych 0 |
| fakty prawdziwe w całości | 34 |
| fakty częściowo fałszywe | 23 |
| fakty fałszywe | 1 |
| wniosek „to odstępstwo od założenia" obalony | 57 |
| przeszło oba sita i jest istotne | **0** |

### Zastrzeżenie metodologiczne, które jest moje, nie agentów

Refuterom kazałem domyślać się **na korzyść obalenia**. Wynik 57 na 58 nie jest więc neutralnym
pomiarem i nie wolno go czytać jako „raport jest bezwartościowy". Ale **rozkład powodów** już jest
pomiarem, bo powód trzeba było wskazać w kodzie: **48 z 57 obaleń mówi jedno i to samo — raport
dołożył własne kryterium.** Sprawdziłem osobno cztery odstępstwa leżące najmocniej na samym
założeniu (1.3, 2.1, 2.7, 3.5) i argument broni się w każdym. Jedno obalenie jest słabe i tak je
oznaczam: **1.1** obala „wzorzec odbiega od wzorca" częściowo przez definicję, bo skoro właściciel
wskazał security jako dobry przykład, to security nie może od siebie odbiegać.

### Co właściwie zmierzył pierwszy przebieg

Założenie ma dwie sprawdzalne połowy. Pierwsza: **repozytoria siedzą w domain.** Jest spełniona
wszędzie — repozytoria encji domenowych są w `*-domain` w każdym serwisie. Raport rozszerzył
„repozytoria" na „wszystkie porty" i wynik tego rozszerzenia zaraportował jako odstępstwo od słów
właściciela. Co gorsza, **wzorzec wskazany przez właściciela sam to kryterium łamie i ma na to
zapisane uzasadnienie**: `security-system` trzyma port `CodeHasher` z javadokiem „A port so the
crypto stays in the infrastructure layer, out of the domain and system layers". Port techniczny
używany przez jeden moduł mieszka u właściciela w tym module. Odstępstwa 1.3, 2.7, 3.5 i 6.6 mierzą
więc kryterium, którego nie postawiono.

Druga połowa: **application bierze prymitywy, buduje klasy domenowe, odpala use case.** Tu raport
dołożył „**każde** VO ma powstawać w application, wzorcem `Supplier<VO>`, a wynik ma być `sealed`".
Tego też nie postawiono. `AddComment.execute(String memeId, UserId author, String text)` buduje
`new Comment(...)` z prymitywów i dopiero potem zapisuje — czyli **spełnia założenie**, a raport
nazwał to niezgodnością, bo nie buduje także tożsamości. Odstępstwa 2.2, 3.3, 4.1 i 5.3 padają na
tym samym.

### Najcięższy błąd: ćwierć kodu, której nikt nie otworzył

Zakres wyciąłem jedną komendą na starcie i nikt go nie sprawdził. Krytyk zakresu policzył:
**19 repozytoriów, około 327 z 1334 plików `.java`, nie było ani celem, ani na liście „poza
zakresem"** — m.in. `portal/portal-libs`, `portal/portal-specs`, `shared/config`,
`shared/transactional-outbox`, `shared/account-closure`, `system-time`. Teza „w całej posiadłości"
jest wypowiedziana nad 75% kodu. W posiadłości jest plik, który sam wymienia 30 repozytoriów
(`shared/estate/*.repos`) i nikt do niego nie zajrzał.

**I w wyciętym kawałku leży dosłownie druga połowa zdania właściciela.** Sprawdzone ręcznie:

- `formula/formula-simulator/src/main/java/com/jrobertgardzinski/formula/` ma pakiety
  **`domain`, `config`, `system`, `application`, `infrastructure`** — cztery warstwy, tylko jako
  pakiety jednego modułu Mavena, nie jako moduły.
- `application/GameService.java:34` **grupuje siedem use case'ów w jeden serwis**
  (`RunResearch`, `ChangeEra`, `Homologate`, `SignDriver`, `ReleaseDriver`, `AdvanceSeason`,
  `AwardRaceResult`), a jego javadoc w linii 30 wprost mówi: *„It used to cite security's
  `SecurityService` as the precedent for this shape"*.
- Repozytoria są w `domain`: `DriverRepository`, `GameRepository`. W `system/` jest dziewięć klas.

Właściciel powiedział „zgrupowanie use case'ów w serwisy". Raport odpowiedział, że tę rolę **„pełni
nic"** i że po skasowaniu `SecurityService` wzorzec nie ma nigdzie kontynuacji. Kontynuacja
istnieje, zna swoje pochodzenie, działa na ścieżce produkcyjnej — i została wycięta z zakresu, bo
ma jeden moduł Mavena zamiast czterech. **To unieważnia tezę nośną całego raportu.**

Drugi pominięty przypadek, też sprawdzony ręcznie: `shared/microservice-email`. `entity/Mail.java:11`
to `record Mail(@NotBlank @Email String to, ...)` — **niezmienniki domeny są adnotacjami
frameworka** (`jakarta.validation.constraints`), a `pom.xml` tego serwisu **nie zawiera ani jednej
wzmianki o `email-domain`**: serwis wysyłający maile nie używa biblioteki, w której według sekcji 6
„żyje mechanizm pomostu". To najtwardsza odpowiedź na założenie w całej posiadłości i nie ma jej w
raporcie.

### Pięć twierdzeń przekrojowych, które sweepy poprawiły

Każde z nich było w pierwszym przebiegu sumą sześciu lokalnych grepów. Teraz ma jedno źródło.
Liczby z jednego przejścia po 1334 plikach i 76–79 pomach; pięć korekt sprawdziłem sam.

1. **Porty, 90 wierszy.** 33 w `*-domain`, **12 w `system` i wszystkie w samym `security-system`** —
   cztery moduły `*-system` w portalu mają **dokładnie zero portów**. 5 w `application`, 2 w
   `usecase`. Przy okazji: zdanie z sekcji 1 „żadnego interfejsu `*Repository` poza `security-domain`
   z wyjątkiem `SettingsRepository`" jest **nieprawdziwe** — w `security-infrastructure` jest
   **15 interfejsów `*JdbcRepository extends CrudRepository`** (sprawdzone: `find … | xargs grep -l`
   daje 15).
2. **Martwe krawędzie: 23 w 15 modułach, nie „cztery z pięciu serwisów".** Martwą krawędź ma
   **pięć z pięciu** serwisów. Rozkład: comments 5, collections 5, offboarding 4, portal-specs 4,
   memes 3, security 1. Zjawisko jest systemowe, a nie dwu-trzyprzypadkowe, i teza o `email` oraz
   `password` jako jedynych czystych **nie stoi**: `microservice-security` ma identyczną
   konfigurację `analyze-only` z `failOnWarning`.
3. **`Supplier<VO>`: teza „zero poza security" jest fałszywa co do litery.** Pięć z czternastu
   wystąpień grupy „odroczenie konstrukcji VO" leży poza `microservice-security`:
   `libs/Constraints.java:30`, `_EmailCandidate.java:25`, `CanRegister.java:29`,
   `CanResetPassword.java:22`, `CreatePasswordHash.java:30`. Intencja („żaden serwis portalu")
   trzyma się w stu procentach — ale rdzeń mechanizmu leży **warstwę niżej**, w `shared/libs`, nie
   obok security.
4. **`sealed` wynik use case'u poza security: JEST, trzy razy.** To korekta sześciu poprzednich
   przejść, które odpowiadały „nie". Sprawdzone ręcznie: `CanRegister.evaluate` zwraca
   `Outcome<Email>`, a `Outcome` jest `public sealed interface`. Tak samo `CanResetPassword.evaluate`
   i `CreatePasswordHash.create`. Dodatkowo **produkcyjny portal oddaje zapieczętowane wyniki na
   drucie Kafki**: `CommentsDeletionParticipant` i `CollectionsDeletionParticipant` zwracają
   `DeletionOutcome` (sealed, `portal-libs/meme-deletion`). W całej posiadłości jest 24 deklaracji
   `sealed`; w produkcie gry zero. Przy okazji odstępstwo **3.9 ma konwencje na krzyż**: to
   `DeleteComment` ma rekord, a `HideComment` goły enum, nie odwrotnie — i zdanie „w security
   wszystkie use case'y zwracają `sealed`" jest nieprawdziwe (`SetSetting`, `SetUserRoles`,
   `SourceThrottle` zwracają rekordy nad enumami).
5. **Strażnicy struktury: 41 mechanizmów, nie trzy.** ArchUnit faktycznie nie ma go nigdzie — ale
   **jego brak jest zapisaną decyzją, nie przeoczeniem**: słowo „ArchUnit" występuje w javadocu
   sześciu plików testowych (`MemeReadFilterTest`, `CommentReadFilterTest`, `ItemReadFilterTest`,
   trzy `RetiredAddressKeyTest`) i za każdym razem tłumaczy, dlaczego go nie użyto („a bytecode-level
   rule cannot see inside a string constant… would buy a green test and no guarantee"). Sprawdzone
   ręcznie: **22 pliki testowe czytają drzewo źródeł**, z czego 12 poza własnym modułem. Teza
   „kierunek zależności trzymają wyłącznie pomy" była po prostu nieprawdziwa.

Dwa sweepy dorzuciły rzeczy, których nikt nie szukał: **typów z sufiksem `Id`/`Ref` jest w całej
posiadłości dokładnie trzy** (`UserId`, `ItemRef`, `RejectedAuthenticationId`), a `collections`
**ma** typ domenowy na id itemu (`ItemRef`) — więc punkt 3 syntezy jest sprzeczny z własną sekcją 4.
I: **16 katalogów-widm oraz 20 nieprawdziwych zdań w dokumentacji**, w tym `system-time`, który ma
`<parent>` `com.jrobertgardzinski:ddd-sample:1.0-SNAPSHOT` **nieistniejący ani w posiadłości, ani w
`~/.m2`** — moduł jest niebudowalny i nie występuje w żadnym manifeście (sprawdzone ręcznie).

### Co z 58 odstępstw naprawdę zostało

**Fakty fałszywe, jedno.** Odstępstwo **6.3** — i jest to teza, którą sam wyróżniłem właścicielowi
w podsumowaniu. Twierdziło: „żaden test w `email`, `password`, `libs` ani `microservice-security` nie
pokrywa wariantu `RejectedDueToInvariantBreakage`". **Nieprawda.** Wariant jest pokryty
behawioralnie: `microservice-security/specs/register.feature:25-26,30` ma przykłady z adresem
`invalid` i `a..b@gmail.com`, które przechodzą przez `RegisterSteps.java:46`
(`register.execute(() -> Email.of(email), …)`) i dalej przez `_EmailCandidate.java:29-30`, a kody
są asertowane. Prawdziwe zdanie brzmi: **nie ma testu, który nazywa ten wariant wprost** — brak
testu jednostkowego w `libs` (nie ma `src/test`) i w `email-usecase`; jedyne pokrycie jest
pośrednie, przez scenariusz rejestracji.

**Fakty częściowo fałszywe, 23.** Rdzeń się trzyma, sypią się numery linii, liczby i zbyt mocne
słowa. To jest odpowiedź na pytanie, które sam zadałem przed tym przebiegiem: jaki jest poziom
błędu w cytatach, których nie sprawdziłem. **Około 41%.**

**Istotne po obu sitach, dwa — i oba o bibliotekach, nie o podziale na warstwy:** 6.3 (powyżej) i
6.4 (`Constraints.validate` łapie `Exception`, więc NPE w supplierze staje się „odmową z powodu
złamanego niezmiennika" z komunikatem `null`). Przy 6.4 refuter ma rację co do adresu zarzutu:
`Constraints` leży w `shared/libs`, czyli w generycznym kernelu, i **nie może** złapać
`InvalidEmailException` bez zaciągnięcia słownictwa produktu — co łamałoby regułę kernela zapisaną
w `shared/CLAUDE.md`. Zarzut zostaje jako ryzyko działania, nie jako błąd warstwowania.

### Luki, które zostały po tym aneksie

Krytycy wskazali pięć pytań, których nie zadał ani pierwszy przebieg, ani ten:

- **Graf zależności jako graf.** Ani raz nie zapytano, czy którykolwiek moduł dolny zależy od
  górnego. To jedyne twarde „tak" na pierwszą połowę założenia, jakie da się wystawić, i nikt go nie
  wystawił.
- **Drugi, równoległy graf: test-jary i scope `test`/`provided`.** Ponad trzydzieści krawędzi.
  Granica pilnowana w scope `compile` w scope `test` nie obowiązuje, a fejki z test-jarów domen są
  faktycznym publicznym API portów — i to one spinają cztery domeny w jeden classpath.
- **Kto testuje use case'y i z którego modułu.** Policzone: `security-infrastructure` 105 plików
  testowych przeciw `security-system` 30; `memes-infrastructure` 81 przeciw `memes-application` 8 i
  `memes-system` 2. Jeśli application ma być pomostem, to use case'y pod nim muszą dać się odpalić
  bez frameworka — a masa testowa siedzi w infrastrukturze.
- **Granica transakcji.** Cztery serwisy, cztery różne mechanizmy, nigdy nie porównane. Kto trzyma
  granicę transakcji, ten jest pomostem, niezależnie od tego, jak się moduł nazywa.
- **Kanał błędu między warstwami.** W całej posiadłości są tylko dwa własne typy wyjątków w dolnych
  warstwach. „Pomost buduje klasy domenowe" to w praktyce zdanie o tym, kto łapie porażkę budowy, a
  mapy tego nie ma.

Do tego: **nie uruchomiono żadnego builda**, więc liczba martwych krawędzi jest wynikiem czytania
pomów, a nie wykonania — mimo że trzy repozytoria mają już wpięty `analyze-only` z
`failOnWarning=true`, który dałby odpowiedź wiążącą. I nie zdefiniowano, czym jest „martwa
krawędź": grep importów po `src/main` nie widzi zależności runtime, procesorów adnotacji ani
`ServiceLoader`a.

### Czego ten aneks nie zrobił

Nie zmienił ani jednego pliku kodu. Nie uruchomił buildów. Nie poprawił sekcji 1–6 w miejscu —
błędne cytaty i liczby **zostają tam, gdzie były**, a ten aneks jest ich erratą; kto czyta sekcje
1–6, musi czytać je razem z nim. Nie przeskanował 19 pominiętych repozytoriów, tylko nazwał je i
sprawdził dwa najcięższe przypadki (`formula-simulator`, `microservice-email`). Niesprawdzalnych
werdyktów: zero. Żadnych propozycji refaktoru — to nadal lista ustaleń pod decyzje, tylko wreszcie
wiadomo, które z nich się trzymają.
