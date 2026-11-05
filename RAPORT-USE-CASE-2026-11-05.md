# Raport: kasacja `libs` i ujednolicenie use case'ów security (2026-11-05)

Stan na koniec dnia: wszystko zielone, **nic nie zacommitowane**. Gałąź `RegisterRebuild` w
repozytoriach kernela, `main` w portalu, `dev-env-ide` w formule.

## 1. Co było do zrobienia

Dwa polecenia właściciela z tego dnia:

1. „Wyjebałem właśnie libs / constraints. Napraw kod."
2. „Przerób usecase'y w security na podobieństwo Register. Na pierwszy ogień ChangePassword." —
   doprecyzowane później: „security ma z 20 usecaseów. Trzeba je ujednolicić względem Register
   i ChangePassword."

## 2. Po `libs` (artefakt `constraint`)

`Outcome` i `Constraints` nie mają już ani jednego użytkownika w posiadłości.

| Gdzie | Co się zmieniło |
|---|---|
| `envelope` | własny rekord `Envelope.Result<T>(value, errors, warnings)` zamiast `Outcome`; odmowa to kod w `errors()`, za duża polityka to kod w `warnings()` |
| `portal/microservice-offboarding` | `EventsRouter` (jedyny konsument `envelope`) czyta akcesory rekordu |
| `microservice-security` | `RequireRole.check` zwraca `List<RoleError>` (nowy enum w `security-domain`), `_HasRoleConstraint` skasowany, `RoleGuard` czyta listę; drut bez zmian (`403 NOT_AN_ADMIN`) |
| pomy | zależność `constraint` wycięta z `envelope`, `email`, `password` i czterech pomów security |
| `security-application` | usunięte dwie nieużywane zależności testowe (`password-config`, `config`), na których bramka `dependency:analyze` wywracała `verify` |
| workspace `shared` | `libs` wycięte z agregatora, `.gitignore`, `estate/shared.repos`, README, CLAUDE.md, `estate.sh` (32 repozytoria zamiast 33) |
| CI | checkout i instalacja `libs` usunięte z workflowów: `shared`, `portal` (dwa pliki), `formula`, `password`, `microservice-security`, `microservice-offboarding` |

Nie ruszane: dokumenty datowane (`docs/`, `PRZEGLAD-*`, `microservice-security/Documentation.md`).

Słoik `constraint` został wyjęty z `~/.m2`, żeby nic nie oparło się na nim po cichu. Pełny build
kernela i `offboarding` przeszły bez niego.

## 3. Reguła wyniku (zatwierdzona: „Miejsce zgoda. Rodzaj też zgoda.")

**Miejsce.** `Result` zawsze jest zagnieżdżony w klasie. Osobnych plików `XResult` już nie ma.

**Rodzaj.** Zależy od roli klasy:

| Rola | Kształt | Kto czyta | Przykłady |
|---|---|---|---|
| sędzia | rekord `Result(wartość, errors, …)` | use case, zwykłym `if` na `errors().isEmpty()` | `AdmitEmail`, `CreatePasswordHash` |
| use case | sealed `Result` z nazwanymi wariantami, **w tym nazwanym sukcesem** | kontroler, `switch` po wariantach | `Register`, `ChangePassword` i reszta |

Co zostało przeniesione do klas (dziesięć plików skasowanych):
`Register`, `ResetPassword`, `RequestEmailChange`, `ConfirmEmailChange`, `Authentication`,
`ContinueAuthentication`, `Authorize` (było `AuthorizationResult`), `FederatedSignIn`,
`RefreshSession`, `VerifyEmail`. `StepUp` i `EnrolFactor` już tak miały.

Co zmieniło rodzaj (rekord z enumem `Status` na sealed):

- `SetUserRoles.Result`: `Updated(roles)`, `NoSuchUser()`, `WouldLeaveNoAdmin(roles)`
- `SetSetting.Result`: `Accepted(value)`, `Refused(reason)`, `UnknownKey(reason)`

## 4. Reguły wnętrza, zdjęte z `Register` i `ChangePassword`

1. Kolejność w klasie: pola, konstruktor, `Result`, `execute`.
2. Konstruktor: parametr na linię, wcięcie 20 spacji (gdy parametrów jest więcej niż jeden).
3. Pusta linia po sygnaturze `execute` i między krokami.
4. `if (`, warunki po jednym na linię z operatorem na końcu, `) {`.
5. Każda odmowa to wczesny `return new Result.X(...)`; każdy inny powód w osobnym `if`.
6. `Optional` jako zmienna lokalna z `isEmpty()` / `get()`; bez łańcuchów `map` / `filter` /
   `orElseGet` i bez lambd sterujących przepływem.
7. Argumenty będące wywołaniami łamane po jednym na linię.
8. Wynik sędziego w zmiennej nazwanej po sędzi (`createPasswordHashResult`).
9. Komentarz „dlaczego" nad instrukcją, nie na jej końcu.

Wzorzec na przykładzie `ChangePassword`:

```java
public class ChangePassword {

    private final UserRepository userRepository;
    private final HashAlgorithmPort hashAlgorithm;
    private final CreatePasswordHash createPasswordHash;
    private final SessionRepository sessions;

    public ChangePassword(
                    UserRepository userRepository,
                    HashAlgorithmPort hashAlgorithm,
                    CreatePasswordHash createPasswordHash,
                    SessionRepository sessions) {
        …
    }

    public sealed interface Result {

        record Changed() implements Result {}

        record UserNotFound() implements Result {}

        record WrongPassword() implements Result {}

        record Rejected(List<PasswordError> passwordErrors, PasswordPolicy policy) implements Result {}
    }

    public Result execute(
                    Email email,
                    Supplier<PlaintextPassword> currentPasswordCandidate,
                    Supplier<PlaintextPassword> newPasswordCandidate) {

        Optional<User> user = userRepository.findBy(email);

        if (
                user.isEmpty()
        ) {
            return new Result.UserNotFound();
        }

        if (
                !hashAlgorithm.verify(
                        user.get().passwordHash(),
                        currentPasswordCandidate.get())
        ) {
            return new Result.WrongPassword();
        }

        CreatePasswordHash.Result createPasswordHashResult = createPasswordHash.create(newPasswordCandidate);

        if (
                !createPasswordHashResult.errors().isEmpty()
        ) {
            return new Result.Rejected(
                    createPasswordHashResult.errors(),
                    createPasswordHashResult.policy());
        }

        userRepository.updatePassword(
                email,
                createPasswordHashResult.hashedPassword());

        // the old password's sessions do not survive it
        sessions.revokeAllSessions(email);

        return new Result.Changed();
    }
}
```

Ten sam ruch na łańcuchu `Optional` (`Authorize`, przed i po):

```java
// przed
return sessionRepository.findByAccessToken(accessToken)
        .filter(grant -> !grant.expiration().hasExpired(clock))
        .<AuthorizationResult>map(grant -> new AuthorizationResult.Authorized(grant.email()))
        .orElseGet(AuthorizationResult.Unauthorized::new);

// po
Optional<AccessGrant> accessGrant = sessionRepository.findByAccessToken(accessToken);

if (
        accessGrant.isEmpty() ||
        accessGrant.get().expiration().hasExpired(clock)
) {
    return new Result.Unauthorized();
}

return new Result.Authorized(
        accessGrant.get().email());
```

## 5. Które use case'y

| Use case | Co zrobione |
|---|---|
| `Register` | wzorzec; tylko `RegisterResult` przeniesiony do klasy |
| `ChangePassword` | wzorzec; pełne przepisanie (patrz punkt 6) |
| `ResetPassword` | wnętrze, `Rejected(passwordErrors, policy)` zamiast `WeakPassword()` |
| `RequestEmailChange` | wnętrze; `Email` nadal konstruowany na brzegu (HTTP-10) |
| `ConfirmEmailChange` | łańcuch `Optional` z lambdą i `try` w środku rozpisany na wczesne zwroty |
| `Authentication` | zagnieżdżony `switch` z wzorcami zastąpiony wczesnymi zwrotami |
| `ContinueAuthentication` | wnętrze |
| `Authorize` | łańcuch `Optional` na `if` |
| `VerifyEmail` | łańcuch `Optional` na `if` |
| `RefreshSession` | lambda z pięcioma gałęziami rozpisana na wczesne zwroty |
| `FederatedSignIn` | wnętrze, typy z pełną nazwą pakietu zamienione na importy |
| `StepUp` | wnętrze, `Result` przeniesiony pod konstruktor |
| `EnrolFactor` | wnętrze, `Result` przeniesiony pod konstruktor |
| `SetUserRoles` | wnętrze, sealed `Result` |
| `SetSetting` | wnętrze, sealed `Result` |
| `RequestEmailVerification` | wnętrze |
| `RequestPasswordReset` | wnętrze |
| `Logout` | `ifPresent` z lambdą na `if` |
| `StartAccountDeletion` | konstruktor, odstępy |
| `DeleteAccount` | konstruktor, odstępy |
| `GenerateRecoveryCodes` | konstruktor, odstępy |
| `RequireRole` | `List<RoleError>` (wymuszone kasacją `libs`) |
| `ListActiveSessions`, `RevokeAllSessions`, `DisplayNames` | bez zmian, jednolinijkowce |

Zachowanie nie zmieniło się nigdzie poza punktem 6. Istniejących asercji testów nie ruszałem,
poza przepisaniem ich na nowe nazwy typów.

## 6. Zmiany na drucie

| Endpoint | Sytuacja | Było | Jest |
|---|---|---|---|
| `POST /account/password` | nowe hasło łamie politykę | `400 {"status":"WEAK_PASSWORD"}` | `422 {"passwordErrors":[{"MIN_LENGTH_NOT_MET":5}, …]}` |
| `POST /account/password` | brak konta albo złe obecne hasło | `400 {"status":"WRONG_CURRENT_PASSWORD"}` | `400 {"status":"FAILED_AUTHENTICATION_ATTEMPT"}` |
| `POST /reset-password` | nowe hasło łamie politykę | `400 {"status":"WEAK_PASSWORD"}` | `422 {"passwordErrors":[…]}` |

`passwordErrors` ma kształt znany z `/register` (`SecurityController.passwordErrors`): kod reguły
i parametr obowiązujący dla tej próby.

Use case `ChangePassword` mówi wprost, co zaszło (`UserNotFound`, `WrongPassword`), a kontroler
zlewa oba w jedno ramię `switch`, żeby drut nie zdradzał, która połowa zawiodła.

Do tego:

- `security-ui` pokazuje złamane reguły tą samą listą co rejestracja, przy zmianie i przy resecie;
- `change-password.feature` ma nową regułę, `reset-password.feature` nową regułę z dwoma
  przykładami (drugi dowodzi, że token nie przepada na odrzuconym haśle);
- glue HTTP i glue przeglądarkowe dostały krok „the new password is flagged as invalid".

Jedynym konsumentem obu endpointów w posiadłości jest `security-ui`.

## 7. Świadome odstępstwa od reguł

1. **`Authentication`** czyta dwa zdarzenia przez `instanceof` w `if`
   (`instanceof Blocked blocked`, `!(… instanceof Valid valid)`). Kroki pakietowe
   `_BruteForceGuard` i `_VerifyCredentials` zwracają sealed `BruteForceProtectionEvent` i
   `AuthenticationEvent` z `security-domain`; pozbycie się `instanceof` wymaga zmiany tych typów.
   Wszystko, co nie jest `Valid`, kończy się odmową i wpisem do liczników.
2. **Strumienie** zostały tam, gdzie przekształcają dane, a nie sterują przepływem:
   `GenerateRecoveryCodes`, `DisplayNames`, `EnrolFactor#candidate`, ostatnie wyrażenie
   `SetUserRoles`.
3. **Kroki pakietowe `_*`** w `authentication` są nietknięte, bo to nie use case'y.

## 8. Weryfikacja

| Co | Wynik |
|---|---|
| pełny build kernela (`./mvnw clean install`) bez słoika `constraint` | BUILD SUCCESS, 810 testów (35 pominiętych, Docker był wtedy wyłączony) |
| security po wszystkich zmianach, z Dockerem | 19 + 42 + 112 + 36 + 341 = 550 testów, zero pominiętych |
| `portal/microservice-offboarding`, z Dockerem | 19 + 12 + 66 + 102 = 199 testów, zero pominiętych |
| `security-ui`: typecheck i vitest | bez błędów, 26 testów |
| `security-ui`: e2e w przeglądarce na końcowym kodzie | 42 scenariusze, 276 kroków |
| `portal/check-workflow-checkouts.sh` | workflowy pokrywają wszystkie 17 modułów kernela |

Pełnego buildu kernela nie powtarzałem po ostatnich zmianach, bo dotyczyły wyłącznie
`microservice-security`, a ten przeszedł w całości.

Docker Desktop był wyłączony. Żeby puścić testy na Postgresie (wyścigi `Authentication`
i `RefreshSession`, adaptery JDBC), podniosłem go i po testach zgasiłem.

## 9. Otwarte decyzje właściciela

1. ~~`ResetPassword.Result.InvalidToken` dla trzech różnych odmów~~ — rozstrzygnięte tego
   samego dnia, patrz punkt 11.
2. **Nazwa wspólnej odpowiedzi** przy zmianie hasła: `FAILED_AUTHENTICATION_ATTEMPT` (wybrane)
   kontra `UserNotFoundOrPasswordDoesNotMatch`. Zmiana to jeden napis w kontrolerze i jeden
   w glue HTTP.
3. ~~`instanceof` w `Authentication`~~ — zdjęty, patrz punkt 13.
4. **Repo `jrobertgardzinski/libs` na GitHubie**: kasacja albo archiwum; workflowy już go nie
   checkoutują.

## 10. Stan repozytoriów

| Repozytorium | Gałąź | Zmienione pliki |
|---|---|---|
| `shared` (workspace) | `RegisterRebuild` | 8 (+ ten raport) |
| `shared/envelope` | `RegisterRebuild` | 4 |
| `shared/email` | `RegisterRebuild` | 1 |
| `shared/password` | `RegisterRebuild` | 2 |
| `shared/microservice-security` | `RegisterRebuild` | 83 |
| `portal` (workspace) | `main` | 2 |
| `portal/microservice-offboarding` | `main` | 2 |
| `formula` (workspace) | `dev-env-ide` | 1 |

Szczegóły i dosłowne werdykty są w `shared/todo.md` (kasacja `libs`) oraz
`microservice-security/todo.md` (sekcja „Use case'y na podobieństwo `Register`").

## 11. Dopisek: runda po rozmowie o tym, co zostało

Werdykty właściciela: „1 ok, 2 brzmi jak kandydat do nowego małego usecase na miarę admit
e-mail, 3 widzisz sens w ujednoliceniu? 4 podobnie jak 2. 5 poprawić".

**Zrobione (1): use case mówi wprost, brzeg ukrywa.** Każda odmowa ma własny wariant, a kontroler
zlewa te, których drut nie może rozróżniać, w jedno ramię `switch`. Drut bez zmian.

| Use case | Było | Jest |
|---|---|---|
| `ResetPassword` | 3× `InvalidToken` | `TokenNotFound`, `TokenExpired`, `UserNotFound` |
| `ConfirmEmailChange` | 3× `InvalidToken` | `TokenNotFound`, `TokenExpired`, `AccountClosing` |
| `VerifyEmail` | 2× `Rejected` | `TokenNotFound`, `TokenExpired` |
| `RequestPasswordReset` | `void`, ciche wyjście | `LinkSent`, `UserNotFound` |
| `RequestEmailVerification` | `void`, ciche wyjście | `LinkSent`, `AlreadyVerified` |

Nowy `TokenRefusalsLookAlikeHttpTest` pilnuje, że przeterminowany link resetu i link weryfikacji
odpowiadają dokładnie jak nieznany.

**Zrobione (5): drobiazgi.**

- `FederatedSignIn.Result.Refused(String reason)` rozbity na `EmailNotVouched` i `AccountClosing`;
  fragment URL bez zmian.
- Pola repozytoriów nazwane po typie we wszystkich klasach `security-system`
  (`userRepository`, `sessionRepository`, `enrolledFactorRepository`, …).
- Jeden zapis „za stary link": `MailedLinkExpiration` w `security-domain`, a w trzech use
  case'ach `pending.expiration(tokenTtl).hasExpired(clock)`.

**Weryfikacja tej rundy:** security z Dockerem 19 + 42 + 112 + 36 + 343 = 552 testy, zero
pominiętych. Przeglądarkowego e2e nie powtarzałem, bo drut się nie zmienił.

## 12. Dopisek: małe use case'y i rozbicie klas

Werdykt właściciela: „Wychodzi na to, że plan zaakceptowany. Koduj!"

**`VerifyPassword`** (`security-system`, pakiet `password`) — jeden sędzia zamiast trzech
rozjechanych miejsc:

```java
public class VerifyPassword {

    public record Result(User user, List<CredentialsError> errors) {}   // USER_NOT_FOUND, WRONG_PASSWORD

    public Result verify(Email email, Supplier<PlaintextPassword> passwordCandidate)
    public Result verify(Email email, PlaintextPassword plaintextPassword)
}
```

- Zawsze liczy jeden hash, także dla nieistniejącego konta, więc czas nie zdradza, czy konto jest.
- `_VerifyCredentials` i jego test skasowane; `Authentication`, `ChangePassword` i `StepUp`
  dostają jeden wspólny bean.
- `ChangePassword` stracił `HashAlgorithmPort`: ma repozytoria i dwa use case'y, jak `Register`.

**`OpenMfaChain`** (`security-system`, pakiet `mfa`) — jedno miejsce otwierania łańcucha:

```java
public class OpenMfaChain {

    public record Result(String ticket, FactorType nextFactor, String challengeData) {}

    public Result open(Email email, List<EnrolledFactor> factors, Source source)
}
```

- `Source` jest wymagany, więc `FederatedSignIn.execute` dostał go od `OauthController`.
- Złe kody drugiego czynnika po logowaniu przez dostawcę trafiają teraz do liczników brute force.
  Dowodzi tego `OpenMfaChainTest`; testu HTTP przez pełny taniec OAuth nie ma.

**Rozbicie klas z dwoma wejściami:**

| Było | Jest |
|---|---|
| `StepUp.start` / `StepUp.submitFactor` | `StepUp.execute` / `ContinueStepUp.execute` |
| `EnrolFactor.start` / `EnrolFactor.confirm` | `EnrolFactor.execute` / `ConfirmEnrolment.execute` |

Każda klasa ma `Result` tylko z wariantami, które mogą z niej przyjść; z kontrolerów zniknęły
martwe ramiona `switch`. Drut bez zmian.

**Zmiany zachowania:**

- zmiana hasła i step-up odpowiadają na nieistniejące konto w tym samym czasie co na istniejące;
- konto w trakcie usuwania jest w `Authentication` osobnym `if` i sprawdzane po zapisanej
  pisowni adresu (wcześniej po wpisanej, a magazyn porównuje dokładny napis).

**Weryfikacja tej rundy:** security z Dockerem 19 + 42 + 116 + 36 + 343 = 556 testów, zero
pominiętych; przeglądarkowe e2e 42 scenariusze, 276 kroków.

## 13. Dopisek: dokończenie

Polecenie właściciela: „Dokończ".

**`PresentProof`** (`security-system`, pakiet `mfa`) — trzeci mały use case: jeden protokół
„dowód przeciw biletowi" dla logowania i step-upu.

```java
public class PresentProof {

    public record Result(PendingAuthentication chain, List<ProofError> errors, boolean chainCompleted) {}

    public Result present(String ticket, String proof)   // INVALID_TICKET, WRONG_PROOF, TOO_MANY_ATTEMPTS
}
```

- Wąski port `ChainTickets` (łańcuch pod biletem): `PendingAuthenticationStore` go rozszerza,
  `StepUpStore` oddaje widok `chains()`.
- `ContinueAuthentication` i `ContinueStepUp` zostały cienkie: mapują błędy na swoje warianty
  i robią tylko to, co ich własne (wpis do liczników i sesja; elewacja).
- `PresentProofTest`: nieznany bilet, przeterminowany, zły kod, ostatnia próba, dobry kod
  kończący łańcuch, dobry kod z kolejnym czynnikiem.

**Ostatni `instanceof`:** `_BruteForceGuard.execute` zwraca `Optional<AuthenticationBlock>`
(blokada w mocy albo nic). W całym `security-system` nie ma już ani jednego `instanceof`;
jedyny `switch` to mapowanie roli na kod błędu w `RequireRole`.

**Skasowane martwe typy domenowe:** `AuthenticationEvent`, `BruteForceProtectionEvent` (cały
pakiet `domain.event`) i `Credentials`. Todo z `AuthenticationEvent` o powiadomieniach
bezpieczeństwa jest przepisane do `microservice-security/todo.md`; rozróżnienie „brak konta /
złe hasło", o które w nim chodziło, żyje teraz w `CredentialsError`.

**Niuanse zachowania:**

- wpis złego kodu do liczników brute force następuje po zużyciu próby (było przed);
- podmiana łańcucha step-upu idzie przez atomowe `update`, więc bilet zamknięty w międzyczasie
  nie jest wskrzeszany.

**Weryfikacja końcowa, z Dockerem:** security 19 + 42 + 122 + 36 + 343 = 562 testy; pełny build
kernela 823 testy; zero pominiętych. Przeglądarkowe e2e: 42 scenariusze, 276 kroków.

## 14. Dopisek: resztki

Po pytaniu „Zostało coś?" przeszukałem trzy workspace'y pod kątem martwych odwołań i miejsc,
których reguły nie objęły.

**Kroki pakietowe `_*` w `authentication`** przeszły przez te same reguły co use case'y, więc
odstępstwo z punktu o wnętrzach już nie obowiązuje: `_BruteForceGuard`, `_AccountStillSignsIn`,
`_GenerateSession`, `_UpdateBruteForceRecords`. Dwa jednolinijkowe zostały bez zmian
(`_CleanBruteForceRecords`, `_RequireVerifiedEmail`). Zachowanie bez zmian.

```java
Optional<AuthenticationBlock> standingBlock = authenticationBlockRepository.findBy(source);

if (
        standingBlock.isPresent() &&
        standingBlock.get().isStillActive(clock)
) {
    return standingBlock;
}
```

**Martwe `_VerifyCredentials` w żywych dokumentach:** `microservice-security/Readme.md`,
`portal/PODRECZNIK.md` (dwa miejsca, w tym opis odrzucania konta w trakcie usuwania),
`portal/PODRECZNIK-ETAP-2.md`; komentarz w `portal/microservice-memes/memes-ui/src/api.ts` mówi
teraz `RefreshSession.Result`. Dokumenty datowane (`BLEDY-*`, `PRZEGLAD-*`, `PLAN-*`, `docs/`)
zostały, jak były: mówią, co było prawdą w dniu spisania.

**Weryfikacja, z Dockerem:** security 19 + 42 + 122 + 36 + 343, zero pominiętych.

**Nadal otwarte**, wszystko po stronie właściciela: nazwy nowych typów (`VerifyPassword`,
`CredentialsError`, `OpenMfaChain`, `PresentProof`, `ChainTickets`, `ProofError`,
`ContinueStepUp`, `ConfirmEnrolment`, `MailedLinkExpiration`), nazwa statusu na drucie
(punkt 9.2), repo `libs` na GitHubie (punkt 9.4), brak testu HTTP na liczniki brute force po
logowaniu przez dostawcę i commit w dziewięciu repozytoriach.

## 15. Werdykty końcowe i commity

Robert, 2026-11-05: „Commituj. Nazwy ok. Niech będzie FAILED_AUTHENTICATION_ATTEMPT. Repo do
wyjebania. Testy http narazie w dupie. Wyjeb katalogi."

- **Nazwy nowych typów** zatwierdzone.
- **Status na drucie** zostaje `FAILED_AUTHENTICATION_ATTEMPT` (punkt 9.2 zamknięty).
- **Test HTTP** na liczniki brute force po logowaniu przez dostawcę: odłożony.
- **Katalogi** `shared/author-directory/` i `shared/purge-rule/` skasowane z dysku. Były to
  osierocone klony po przeprowadzce do `portal/portal-libs`: czyste, bez niewypchniętych
  commitów, historia w całości w `portal-libs`.
- **Repo `libs` na GitHubie**: werdykt to kasacja, ale dopiero po zmergowaniu tej roboty.
  `origin/main` w siedmiu repozytoriach (`shared`, `password`, `email`, `microservice-security`,
  `portal`, `microservice-offboarding`, `formula`) wciąż buduje się z `libs`: workflowy CI je
  checkoutują, a publiczne `Readme.md` każe je sklonować.
- **Commity**: wszystko zacommitowane lokalnie, nic nie wypchnięte. Kernel i `shared` na
  `RegisterRebuild`; w `portal`, `microservice-offboarding` i `microservice-memes` założona
  gałąź `RegisterRebuild` (stały na `main`, a ich zmiany zależą od `RegisterRebuild` kernela);
  `formula` na `dev-env-ide`. Tabela z punktu 10 opisuje stan sprzed commitów.
