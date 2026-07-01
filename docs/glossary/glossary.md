# Ubiquitous-Language glossary

_Generated from the domain, config and *-system layers by `build_glossary.py` — do not edit by hand._

107 classes · 12 feature files tagged.

## Domain (50)

- **AbstractEmail** — Common abstraction for all email address representations. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/AbstractEmail.java`
- **AbstractToken** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AbstractToken.java`
- **AbstractTokenExpiration** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/AbstractTokenExpiration.java`
- **AbstractTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AbstractTokenValidityInHours.java`
- **AccessGrant** — What a valid access token grants: whose session it is and when it expires. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AccessGrant.java`
- **AccessToken** — Short-lived access token issued after successful authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AccessToken.java` · _used in_ authorize, logout, revoke-all-sessions
- **AccessTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AccessTokenValidityInHours.java`
- **AuthenticationBlock** — A temporary suspension of authentication attempts from a given IpAddress. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/AuthenticationBlock.java`
- **AuthenticationBlockRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/AuthenticationBlockRepository.java`
- **AuthenticationEvent** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/event/AuthenticationEvent.java`
- **AuthenticationRequest** — A user's attempt to authenticate and gain access to the system. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AuthenticationRequest.java`
- **AuthorizationDataRepository** — Stores the sessions issued to users, keyed by their refresh token — the credential a client presents to refresh. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/AuthorizationDataRepository.java`
- **AuthorizationTokenExpiration** — The point in time at which an AccessToken expires. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/AuthorizationTokenExpiration.java`
- **BruteForceProtectionEvent** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/event/BruteForceProtectionEvent.java`
- **Credentials** — Proof of identity presented by a user during authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/Credentials.java` · _used in_ authenticate
- **DomainPart** — The mail server portion of an Email, following the '@' symbol. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/DomainPart.java`
- **Email** — An email address, composed of a LocalPart and a DomainPart. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/Email.java` · _used in_ change-email, register, verify-email
- **EmailAlreadyTakenException** — Thrown by UserRepository#save when persistence rejects a user because the (normalized) email is already taken — the storage-level uniqueness guarantee, which closes the check-then-act race that a prior existsBy check alone cannot. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailAlreadyTakenException.java`
- **EmailChange** — A requested email change awaiting confirmation: from the user's current address to the new one. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/EmailChange.java`
- **EmailChangeRepository** — Tracks pending email changes. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailChangeRepository.java`
- **EmailVerificationNotifier** — Outbound port that delivers a verification link — carrying the single-use token — to a user's e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/port/EmailVerificationNotifier.java`
- **EmailVerificationRepository** — Tracks pending e-mail verifications and their outcome. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailVerificationRepository.java`
- **FailuresCount** — Consecutive failed authentication count for a given IpAddress. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/FailuresCount.java`
- **HashAlgorithmPort** — _(no Javadoc yet)_ `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/HashAlgorithmPort.java`
- **HashedPassword** — A password transformed by a hashing algorithm, safe for storage. `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/HashedPassword.java`
- **IpAddress** — Network address from which a request originates. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/IpAddress.java`
- **LocalPart** — The recipient-specific portion of an Email, preceding the '@' symbol. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/LocalPart.java`
- **NormalizedEmail** — An Email with provider-specific normalization applied, used for deduplication. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/NormalizedEmail.java`
- **PasswordResetNotifier** — Outbound port that delivers a password-reset link — carrying the single-use token — to a user's e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/port/PasswordResetNotifier.java`
- **PasswordResetRepository** — Tracks pending password resets. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/PasswordResetRepository.java`
- **PasswordResetToken** — Single-use token e-mailed to a user so they can set a new password after forgetting the old one. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/PasswordResetToken.java` · _used in_ reset-password
- **PlaintextPassword** — A password as entered by the user, before hashing. `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/PlaintextPassword.java`
- **RefreshToken** — Long-lived refresh token used to obtain a new access token without re-authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/RefreshToken.java` · _used in_ logout, reuse-detection, revoke-all-sessions
- **RefreshTokenExpiration** — Expiration of a refresh token. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/RefreshTokenExpiration.java`
- **RefreshTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RefreshTokenValidityInHours.java`
- **RejectedAuthentication** — A recorded instance of a rejected authentication attempt. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/RejectedAuthentication.java`
- **RejectedAuthenticationDetails** — Details of a RejectedAuthentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RejectedAuthenticationDetails.java`
- **RejectedAuthenticationId** — Identity of a RejectedAuthentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RejectedAuthenticationId.java`
- **RejectedAuthenticationRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/RejectedAuthenticationRepository.java`
- **SaveResult** — Result of UserRepository#save. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/SaveResult.java`
- **SessionFamily** — Identifies a session lineage: the original session created at authentication and every session it is rotated into on refresh share one family. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionFamily.java` · _used in_ reuse-detection
- **SessionRefreshRequest** — A request to renew a session without re-authenticating. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionRefreshRequest.java`
- **SessionStatus** — Lifecycle of a stored session. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionStatus.java`
- **SessionTokens** — An active session represented by a pair of tokens. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/SessionTokens.java`
- **SessionTokensConfig** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionTokensConfig.java`
- **StoredSession** — The stored essentials of a session, found by its refresh token: who it belongs to, when its refresh token expires, which lineage it belongs to (SessionFamily) and whether it is still active or already rotated. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/StoredSession.java`
- **User** — A registered participant in the system. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/User.java` · _used in_ authenticate, authorize, change-email, change-password, delete-account, logout, refresh-session, register, reset-password, reuse-detection, revoke-all-sessions, verify-email
- **UserRegistration** — A prospective user's request to join the system, with the password already hashed. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/UserRegistration.java`
- **UserRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/UserRepository.java`
- **VerificationToken** — Single-use token e-mailed to a user to prove they own their e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/VerificationToken.java` · _used in_ verify-email

## Config (24)

- **BlockedDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/BlockedDomains.java`
- **BruteForceConfig** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/BruteForceConfig.java`
- **CanRegisterConfig** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/CanRegisterConfig.java`
- **CompanyDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/CompanyDomains.java`
- **ConfigKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/ConfigKey.java`
- **DisposableDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/DisposableDomains.java`
- **EmailConfigPort** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/port/EmailConfigPort.java`
- **FailureWindowMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/FailureWindowMinutes.java`
- **HardcodedConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/hardcoded/HardcodedConfigSource.java`
- **HardcodedKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/HardcodedKey.java`
- **MaxBlockMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MaxBlockMinutes.java`
- **MaxFailures** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MaxFailures.java`
- **MinBlockMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MinBlockMinutes.java`
- **MinLength** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/MinLength.java`
- **PropertiesConfigPort** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/properties/PropertiesConfigPort.java`
- **PropertiesConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/properties/PropertiesConfigSource.java`
- **PropertiesKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/PropertiesKey.java`
- **RepositoryConfigPort** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/repository/RepositoryConfigPort.java`
- **RepositoryConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/repository/RepositoryConfigSource.java`
- **RepositoryKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/RepositoryKey.java`
- **RequiresDigit** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresDigit.java`
- **RequiresLowercase** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresLowercase.java`
- **RequiresUppercase** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresUppercase.java`
- **SpecialChars** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/SpecialChars.java`

## System (33)

- **Authentication** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/Authentication.java` · _used in_ authenticate, change-email, change-password, delete-account, reset-password
- **AuthenticationFactory** — Public assembly seam for Authentication. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/AuthenticationFactory.java`
- **AuthenticationResult** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/AuthenticationResult.java`
- **AuthorizationResult** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authorization/AuthorizationResult.java`
- **Authorize** — Authorizes a request by its access token: the token names a session, which must exist and not be expired. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authorization/Authorize.java`
- **BlockDurationPolicy** — Decides how long a brute-force block lasts, in minutes. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/BlockDurationPolicy.java`
- **CanRegister** — _(no Javadoc yet)_ `email/email-security/email-security-system/src/main/java/com/jrobertgardzinski/email/policy/CanRegister.java`
- **CanResetPassword** — _(no Javadoc yet)_ `email/email-security/email-security-system/src/main/java/com/jrobertgardzinski/email/policy/CanResetPassword.java`
- **ChangePassword** — Changes a signed-in user's password: the current password must match, and the new password must meet the policy. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/ChangePassword.java` · _used in_ change-password
- **ChangePasswordResult** — Outcome of ChangePassword: the password was changed, the current password was wrong, or the new password did not meet the policy. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/ChangePasswordResult.java`
- **ConfirmEmailChange** — Completes an email change: a matching, unused token applies the pending change (moving the user to the new address); an unknown or already-used token is rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/ConfirmEmailChange.java` · _used in_ change-email
- **ConfirmEmailChangeResult** — Outcome of ConfirmEmailChange: the address was changed, or the token was rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/ConfirmEmailChangeResult.java`
- **CreatePasswordHash** — _(no Javadoc yet)_ `password/password-security/password-security-system/src/main/java/com/jrobertgardzinski/password/policy/CreatePasswordHash.java`
- **DeleteAccount** — Closes a user's account (GDPR right to be forgotten): revokes every session and deletes the user, so the account can no longer authenticate and its access tokens stop authorizing. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/DeleteAccount.java` · _used in_ delete-account
- **EmailErrorCodes** — The email error codes of a registration attempt — a type deliberately distinct from PasswordErrorCodes, so the two channels can never be swapped when a RegisterResult.Rejected is built. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/EmailErrorCodes.java`
- **Logout** — Ends a session: the refresh token names the session, and removing it invalidates the whole session at once — its refresh token can no longer be refreshed and its access token (whose hash lived on the same record) no longer authorizes. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/Logout.java` · _used in_ logout
- **MxRecordPort** — _(no Javadoc yet)_ `email/email-security/email-security-system/src/main/java/com/jrobertgardzinski/email/external/MxRecordPort.java`
- **PasswordErrorCodes** — The password error codes of a registration attempt — a type deliberately distinct from EmailErrorCodes, so the two channels can never be swapped when a RegisterResult.Rejected is built. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/PasswordErrorCodes.java`
- **PasswordPolicy** — _(no Javadoc yet)_ `password/password-security/password-security-system/src/main/java/com/jrobertgardzinski/password/policy/PasswordPolicy.java`
- **RandomBlockDurationPolicy** — Production BlockDurationPolicy: a random duration within the configured [minBlockMinutes, maxBlockMinutes] range, so block lengths are not predictable. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/RandomBlockDurationPolicy.java`
- **RefreshSession** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/RefreshSession.java` · _used in_ logout, refresh-session, reuse-detection, revoke-all-sessions
- **RefreshSessionResult** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/RefreshSessionResult.java`
- **Register** — Registers a new user from an email and a plaintext password: the email must be allowed to register and not already taken, and the password is hashed before the user is stored. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/Register.java` · _used in_ delete-account, register
- **RegisterResult** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/RegisterResult.java`
- **RequestEmailChange** — Starts an email change for a signed-in user: refuses if the new address is already taken, otherwise mints a verification token, remembers the pending change, and e-mails the link to the new address (ownership must be proven before the change takes effect). `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/RequestEmailChange.java` · _used in_ change-email
- **RequestEmailChangeResult** — Outcome of RequestEmailChange: a verification link was sent to the new address, or that address is already taken. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/RequestEmailChangeResult.java`
- **RequestEmailVerification** — Starts e-mail verification: mints a single-use token, remembers it against the address, and e-mails the verification link. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/verification/RequestEmailVerification.java`
- **RequestPasswordReset** — Starts a password reset: mints a single-use token, remembers it against the address, and e-mails the reset link. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/passwordreset/RequestPasswordReset.java`
- **ResetPassword** — Completes a password reset: the new password must meet the policy, and the token must match a pending reset. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/passwordreset/ResetPassword.java` · _used in_ reset-password
- **ResetPasswordResult** — Outcome of ResetPassword: the password was reset, the token was rejected, or the new password did not meet the policy. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/passwordreset/ResetPasswordResult.java`
- **RevokeAllSessions** — Logs a user out everywhere: revokes every session the user holds, across all lineages, so no refresh token can be refreshed and no access token authorizes any longer. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/RevokeAllSessions.java` · _used in_ revoke-all-sessions
- **VerifyEmail** — Completes e-mail verification: a matching, unused token marks the address verified; an unknown or already-used token is rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/verification/VerifyEmail.java` · _used in_ verify-email
- **VerifyEmailResult** — Outcome of VerifyEmail: the address was verified, or the token was rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/verification/VerifyEmailResult.java`

## Features → terms

- `microservice-security/specs/authenticate.feature` — `Authentication` `Credentials` `User`
- `microservice-security/specs/authorize.feature` — `AccessToken` `User`
- `microservice-security/specs/change-email.feature` — `Authentication` `ConfirmEmailChange` `Email` `RequestEmailChange` `User`
- `microservice-security/specs/change-password.feature` — `Authentication` `ChangePassword` `User`
- `microservice-security/specs/delete-account.feature` — `Authentication` `DeleteAccount` `Register` `User`
- `microservice-security/specs/logout.feature` — `AccessToken` `Logout` `RefreshSession` `RefreshToken` `User`
- `microservice-security/specs/refresh-session.feature` — `RefreshSession` `User`
- `microservice-security/specs/register.feature` — `Email` `Register` `User`
- `microservice-security/specs/reset-password.feature` — `Authentication` `PasswordResetToken` `ResetPassword` `User`
- `microservice-security/specs/reuse-detection.feature` — `RefreshSession` `RefreshToken` `SessionFamily` `User`
- `microservice-security/specs/revoke-all-sessions.feature` — `AccessToken` `RefreshSession` `RefreshToken` `RevokeAllSessions` `User`
- `microservice-security/specs/verify-email.feature` — `Email` `User` `VerificationToken` `VerifyEmail`
