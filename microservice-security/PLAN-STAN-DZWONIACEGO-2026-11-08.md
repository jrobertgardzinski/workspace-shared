# Plan: stan dzwoniącego w typie wejściowym use case'u (2026-11-08)

Plik samowystarczalny: nowa sesja ma z niego zrobić całość bez dopytywania. Decyzje Roberta są
zapisane jako decyzje; miejsca, gdzie Robert ma jeszcze wybrać, są oznaczone **DO DECYZJI** i mają
rekomendację, którą należy przyjąć, jeśli nie powie inaczej.

## 1. Problem

`VerifyPassword` jest czytany w trzech use case'ach: `Authentication`, `ChangePassword`, `StepUp`.
Wszystkie trzy dostają `Email`. Ale:

- w `Authentication` adres wpisał **obcy** w formularzu. Nie wolno mu zdradzić, czy konto istnieje,
  więc `UserNotFound` i `WrongPassword` zlewają się w jedno `Rejected` (i zapisuje się próbę do
  liczników brute-force);
- w `ChangePassword` i `StepUp` adres pochodzi **z tokena** uwierzytelnionej osoby (kontroler woła
  `Caller.of(request)`). Tu `UserNotFound` nie jest odmową dla użytkownika: to sygnał, że **konto
  zniknęło spod żywej sesji** (skasowane w trakcie, por. `_AccountStillSignsIn` w
  `ContinueAuthentication`). Konto federacyjne ma wiersz w `UserRepository` (`FederatedSignIn`
  robi `userRepository.save`), więc ono tu nie trafia — dostaje `WrongPassword`.

Ten sam typ `Email`, inne zaufanie, i tylko czytelnik kontrolera wie, które jest które. Use case
z sygnatury tego nie widzi, a `ChangePassword.Result.UserNotFound` wygląda jak kopia wzoru
z `Authentication`, choć znaczy co innego. Na drucie różnicy dziś nie ma:
`ChangePasswordController` odpowiada na oba `400 FAILED_AUTHENTICATION_ATTEMPT`;
`StepUp` zlewa oba w `Result.WrongPassword` → `401 WRONG_PASSWORD`.

Słowami Roberta: use case powinien znać stan systemu. Stan w `ChangePassword`: użytkownik
uwierzytelniony, autoryzujemy z tokena. Stan w `Authentication`: obcy próbuje się uwierzytelnić.
Repozytoria trzymają stan **systemu**; stan **rozmowy** (kto dzwoni) ma nieść typ wejścia.

## 2. Decyzja

Stan dzwoniącego wsadzamy w typ, tym samym ruchem co 2026-11-07 ze statusami sędziów (sealed
zamiast enum, żeby kompilator pilnował). **Bez** nowej abstrakcji nad `VerifyPassword`: żadnego
handlera, Observera, metody kontynuacji. Sędzia zostaje jeden, `CredentialsStatus` zostaje jeden,
`switch` zostaje w każdym use casie osobno (dekret „przepływ w jednej metodzie", patrz §6).

## 3. Co zmienić — faza 1 (rdzeń, jeden commit)

### 3.1 Typ `Caller` w domenie

Nowy rekord w `security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/`:

- `record Caller(Email email)` — „osoba, którą nazywa żywy, przyjęty token". Javadoc ma
  powiedzieć wprost: to nie adres wpisany w formularz; use case, który go przyjmuje, rozmawia
  z kimś już uwierzytelnionym, więc brak konta pod tym adresem znaczy „konto zniknęło spod sesji",
  nie „literówka".
- Jedno pojęcie, jeden termin: **`Caller`**. Nie `AuthenticatedUser`, nie `Principal`, nie
  `Subject` (ten termin już zajęty przez `LockoutSubject`).

Kolizja nazw: istnieje statyczny pomocnik `com.jrobertgardzinski.Caller` w
`security-infrastructure` (`Caller.of(HttpRequest)` → `Email`, `Caller.bearerToken(request)`).
Kontrolery leżą w tym samym pakiecie, więc import domenowego `Caller` przesłoniłby pomocnika.
Zmienić pomocnika na **`RequestCaller`** z metodą `of(HttpRequest)` zwracającą domenowy `Caller`
(i `bearerToken` bez zmian). Przejechać wszystkie 11 miejsc wywołania (`grep -rn 'Caller.of('`),
w tym `RoleGuard`. Tam, gdzie kontroler potrzebuje tylko adresu (np. `SessionsController`,
`MeController`), pisze `RequestCaller.of(request).email()` — w fazie 1 nie zmieniamy sygnatur
tamtych use case'ów.

### 3.2 `ChangePassword`

- `execute(Caller caller, Supplier<PlaintextPassword> current, Supplier<PlaintextPassword> next)`.
- `verifyPassword.verify(caller.email(), currentPasswordCandidate)` — sędzia bez zmian.
- W `Result`: `UserNotFound` → **`AccountGone`** („konto zniknęło spod sesji"). Javadoc: żywy
  token, a w `UserRepository` nie ma konta; jedyna droga tutaj to kasacja konta w trakcie sesji.
- Ramię: `case CredentialsStatus.UserNotFound _ -> { return new Result.AccountGone(); }`.
  `WrongPassword` i `Passed` bez zmian.
- `ChangePasswordController`: `RequestCaller.of(request)` zamiast `Caller.of(request)`;
  `AccountGone` dostaje własną odpowiedź (§3.4), `WrongPassword` zostaje
  `400 FAILED_AUTHENTICATION_ATTEMPT`.

### 3.3 `StepUp`

- `execute(Caller caller, StepUpAction action, String accessToken, Supplier<PlaintextPassword> passwordCandidate)`.
- Wszystkie użycia `email` w środku → `caller.email()` (lub lokalna `Email email = caller.email();`
  na początku, jeśli czytelniej; jedna zmienna, nie mieszać).
- W `Result` dochodzi **`AccountGone()`**. Ramię:
  `case CredentialsStatus.UserNotFound _ -> { return new Result.AccountGone(); }`,
  `case CredentialsStatus.WrongPassword _ -> { return new Result.WrongPassword(); }`,
  `case CredentialsStatus.Passed _ -> {}`. Komentarz nad `switch`-em o niepustym haśle zostaje.
- `StepUpController.start`: `RequestCaller.of(request)`; `respond(...)` dostaje ramię dla
  `AccountGone` (§3.4). `subjectOf(request)` przy throttle używa `.email().value()`.

### 3.4 Drut dla `AccountGone` — **DO DECYZJI**, rekomendacja:

`401 {"status": "ACCOUNT_GONE"}` w obu kontrolerach. Uzasadnienie: sesja nie ma już czego
zmieniać ani czego podnosić; 401 każe UI wylogować, a kod mówi dlaczego. Alternatywa 410 jest
semantycznie ładna, ale UI traktuje 401 jako „sesja nieważna" i to jest zachowanie, którego tu
chcemy. Sprawdzić w `security-ui/src/App.tsx`, jak obsługiwane jest 401 z `/me/password`
i `/step-up` (grep `WRONG_PASSWORD`, `FAILED_AUTHENTICATION_ATTEMPT`, obsługa 401) i czy nowy
kod nie wymaga zmiany w UI; jeśli UI na 401 wylogowuje niezależnie od `status`, nic nie ruszać.
Portal nie zna tych kodów (grep pusty 2026-11-08).

### 3.5 `Authentication` — bez zmian

Obcy, adres z formularza, `AuthenticationRequest.email()`. Zostaje `Email`, zostaje zlanie
`UserNotFound`+`WrongPassword` w `Rejected`. Jedyna zmiana: **jedno zdanie komentarza** nad tym
ramieniem: obcy nie dowiaduje się, czy konto istnieje; `ChangePassword` i `StepUp` dostają `Caller`
i tam brak konta znaczy `AccountGone`.

### 3.6 Testy fazy 1

- `security-system`: `ChangePasswordTest` (sygnatura, wariant `AccountGone` zamiast
  `UserNotFound`), testy `StepUp` (grep `new StepUp(` w `src/test`; `StepUpCoverageTest`
  w infrastrukturze czyta źródła, sprawdzić, czy nie liczy sygnatur), `AuthenticationTest`
  bez zmian.
- `security-infrastructure`: testy HTTP `/me/password` i `/step-up` (grep
  `FAILED_AUTHENTICATION_ATTEMPT`, `WRONG_PASSWORD`, `RequestCaller`/`Caller.of`), `RoleGuard`.
  Dodać jeden test HTTP: żywy token, konto skasowane → `401 ACCOUNT_GONE` na `/me/password`
  (sposób: zalogować, skasować wiersz użytkownika w repozytorium in-memory, wywołać).
- Nazwy w `@DisplayName`/`@Label` po polsku lub angielsku tak jak sąsiednie w danym pliku.

## 4. Faza 2 (osobny commit, po zielonej fazie 1) — **DO DECYZJI**, rekomendacja: zrobić

„Jeden termin na jedno pojęcie": skoro `Caller` istnieje, wszystkie use case'y, które dziś biorą
`Email` **dzwoniącego**, mają brać `Caller`. Inaczej połowa mówi `Email`, połowa `Caller`.

Kandydaci (adres pochodzi z `Caller.of(request)`):
`RequestEmailChange.execute(Email currentEmail, Email newEmail)` → `(Caller caller, Email newEmail)`;
`EnrolFactor.execute(Email user, …)` i `ConfirmEnrolment.execute(Email user, …)` → `Caller`;
`RequireRole.check(Email caller, Role)` → `check(Caller, Role)`; use case kasowania konta
(`DeleteAccountController`); kody odzyskiwania, lista sesji, `/me`. **Nie** zmieniać tam, gdzie
`Email` jest **celem**, nie dzwoniącym: `SetUserRoles.execute(Email target, …)`, `RequestPasswordReset`,
`RequestEmailVerification`, `Register`.

Każdy use case osobno: sygnatura, `.email()` w środku, kontroler, testy. Bez zmian w `Result`.

## 5. Kolejność pracy i komendy

Wszystko w `~/git/portfolio/shared` (repo `workspace-shared`, gałąź `main`, push bez pytania).

1. Przeczytać §6 (styl) i ten plik. Nie czytać całego `todo.md` — to śmietnik; sekcja
   „Use case'y na podobieństwo `Register`" (od ok. linii 838) ma kontekst, jeśli potrzebny.
2. Faza 1: §3.1 → §3.2 → §3.3 → §3.4 → §3.5 → §3.6.
3. Kompilacja i testy bez bazy:
   `mvn -q -o -pl microservice-security/security-domain,microservice-security/security-system test`
4. Infrastruktura (Testcontainers przez Podman, Docker na laptopie nie działa):
   `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true mvn -o -pl microservice-security/security-infrastructure test`
   Znane czerwone **tylko na laptopie** (w CI zielone, nie szukać w nich regresji):
   `CorsPreflightTest.an_unknown_origin_is_refused`,
   `TrustedProxyHttpTest.distinct_clients_are_distinct_sources`,
   `AccountDeletionSagaLatchTest` (kontener postgres na Podmanie).
   Wszystko inne ma być zielone.
5. Jeśli ruszone `security-ui`: `npm test` w `microservice-security/security-ui`.
6. Commit (treść po angielsku, jak sąsiednie), stopka:
   ```
   Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
   Claude-Session: <link bieżącej sesji>
   ```
   `git pull --rebase origin main` przed pushem (Dependabot merguje na `main`). Push.
7. CI: `gh run list --limit 5 --json headSha,status,conclusion,workflowName` — patrzeć na
   workflow **CI** (jest też „Dependabot auto-merge", ten ma `skipped`). Czekać do `success`.
8. Faza 2 tak samo, osobny commit.
9. Na końcu dopisać do `microservice-security/todo.md`, w sekcji use case'ów, jeden punkt `[x]`
   z datą i odnośnikiem do tego pliku; w tym pliku na górze dopisać „ZROBIONE <data>, commit <sha>".

## 6. Styl i dekrety (obowiązują, nie dyskutować)

- **Przepływ w jednej metodzie**: use case czyta się od góry do dołu w `execute`. Żadnych
  prywatnych metod kontynuacji, handlerów, Observerów, szablonów. Powtórzenie `switch`-a
  w kilku use case'ach jest świadomą ceną.
- Wynik sędziego w nazwanej zmiennej (`verifyPasswordResult`), czytany **instrukcją** `switch`
  po typie sealed: `case X.Odmowa _ -> { return new Result.Odmowa(); }`, `case X.Passed _ -> {}`,
  przepływ idzie dalej. **Nie** `return switch` w use case'ach (w kontrolerach może być).
- `Result` to zagnieżdżony `sealed interface` z nazwanym sukcesem (`Changed()`, `Elevated()`);
  odmowy to warianty, nie wyjątki.
- `null` nie jest stanem; `Optional` jako zmienna lokalna z `isEmpty()/isPresent()/get()`;
  warunki `if (` łamane po jednym w linii, zamykający `)` w osobnej linii — patrz istniejące
  use case'y.
- Jeden termin na jedno pojęcie; przy zmianie nazwy przejechać żywe pliki, historii nie przepisywać.
- Kernel (`shared/`) to tylko generyczny mechanizm; tu nic z tego nie wychodzi poza
  `microservice-security`.
- Komentarze i javadoc po angielsku, w stylu sąsiednich: mówią **dlaczego**, często z historią
  („used to…").
- Odpowiedzi do Roberta krótkie, bez felietonów. Commit i push bez pytania; pytać tylko przy
  decyzjach **DO DECYZJI**, i to dopiero, gdy się do nich dojdzie, z rekomendacją.

## 7. Czego nie robić

- Nie zmieniać `VerifyPassword` ani `CredentialsStatus`.
- Nie wprowadzać wspólnej klasy „czytającej" status za use case'y.
- Nie ruszać `Authentication` poza jednym komentarzem.
- Nie przerabiać `SessionStatus` ani danych w wariantach (`Passed(User)`) — osobne, odłożone
  punkty w `todo.md`.
- Nie czyścić `todo.md` przy okazji.
