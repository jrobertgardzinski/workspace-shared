# Ubiquitous-Language glossary

_Generated from `domain`/`config`/`security-system` code by `build_glossary.py` — do not edit by hand._

66 nouns · 8 verbs · 6 feature files tagged.

## Nouns (types)

### AccessToken
*CAPS:* `ACCESS TOKEN`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AccessToken.java`

Short-lived access token issued after successful authentication.

*Cucumber:* `microservice-security/specs/authorize.feature`, `microservice-security/specs/logout.feature`
*Allure:* 2 test(s) in microservice-security/security-config

### Credentials
*CAPS:* `CREDENTIALS`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/Credentials.java`

Proof of identity presented by a user during authentication.

*Cucumber:* `microservice-security/specs/authenticate.feature`

### Email
*CAPS:* `EMAIL`  ·  *source:* `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/Email.java`

An email address, composed of a LocalPart and a DomainPart.

*Cucumber:* `microservice-security/specs/register.feature`
*Allure:* 4 test(s) in email/email-domain

### RefreshToken
*CAPS:* `REFRESH TOKEN`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/RefreshToken.java`

Long-lived refresh token used to obtain a new access token without re-authentication.

*Cucumber:* `microservice-security/specs/logout.feature`, `microservice-security/specs/reuse-detection.feature`
*Allure:* 2 test(s) in microservice-security/security-config

### SessionFamily
*CAPS:* `SESSION FAMILY`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionFamily.java`

Identifies a session lineage: the original session created at authentication and every session it is rotated into on refresh share one family.

*Cucumber:* `microservice-security/specs/reuse-detection.feature`

### User
*CAPS:* `USER`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/User.java`

A registered participant in the system.

*Cucumber:* `microservice-security/specs/authenticate.feature`, `microservice-security/specs/authorize.feature`, `microservice-security/specs/logout.feature`, `microservice-security/specs/refresh-session.feature`, `microservice-security/specs/register.feature`, `microservice-security/specs/reuse-detection.feature`

## Verbs (operations)

### Authentication · `Authentication`
*CAPS:* `AUTHENTICATION`  ·  *source:* `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/Authentication.java`

_(no Javadoc yet)_

*Cucumber:* `microservice-security/specs/authenticate.feature`
*Allure:* 3 test(s) in microservice-security/security-system

### Logout · `Logout`
*CAPS:* `LOGOUT`  ·  *source:* `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/Logout.java`

Ends a session: the refresh token names the session, and removing it invalidates the whole session at once — its refresh token can no longer be refreshed and its access token (whose hash lived on the same record) no longer authorizes.

*Cucumber:* `microservice-security/specs/logout.feature`
*Allure:* 2 test(s) in microservice-security/security-system

### RefreshSession · `RefreshSession`
*CAPS:* `REFRESH SESSION`  ·  *source:* `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/RefreshSession.java`

_(no Javadoc yet)_

*Cucumber:* `microservice-security/specs/logout.feature`, `microservice-security/specs/refresh-session.feature`, `microservice-security/specs/reuse-detection.feature`
*Allure:* 4 test(s) in microservice-security/security-system

### Register · `Register`
*CAPS:* `REGISTER`  ·  *source:* `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/Register.java`

Registers a new user from an email and a plaintext password: the email must be allowed to register and not already taken, and the password is hashed before the user is stored.

*Cucumber:* `microservice-security/specs/register.feature`
*Allure:* 3 test(s) in microservice-security/security-system

## Features → tags

- `microservice-security/specs/authenticate.feature` — `Authentication` `Credentials` `User`
- `microservice-security/specs/authorize.feature` — `AccessToken` `User`
- `microservice-security/specs/logout.feature` — `AccessToken` `Logout` `RefreshSession` `RefreshToken` `User`
- `microservice-security/specs/refresh-session.feature` — `RefreshSession` `User`
- `microservice-security/specs/register.feature` — `Email` `Register` `User`
- `microservice-security/specs/reuse-detection.feature` — `RefreshSession` `RefreshToken` `SessionFamily` `User`
