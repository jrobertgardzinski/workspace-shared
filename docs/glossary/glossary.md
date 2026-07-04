# Ubiquitous-Language glossary

_Generated from the domain, config and *-system layers by `build_glossary.py` — do not edit by hand._

175 classes · 19 feature files tagged.

## Domain (89)

- **AbstractEmail** — Common abstraction for all email address representations. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/AbstractEmail.java`
- **AbstractToken** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AbstractToken.java`
- **AbstractTokenExpiration** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/AbstractTokenExpiration.java`
- **AbstractTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AbstractTokenValidityInHours.java`
- **AccessGrant** — What a valid access token grants: whose session it is and when it expires. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AccessGrant.java`
- **AccessToken** — Short-lived access token issued after successful authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/AccessToken.java` · _used in_ authorize, logout, revoke-all-sessions
- **AccessTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AccessTokenValidityInHours.java`
- **AccountDeletionSaga** — Outbound port that starts the cross-service part of closing an account: other services purge the user's content, and their confirmation (or its absence) decides whether the deletion completes or rolls back. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/port/AccountDeletionSaga.java`
- **ActiveSession** — A user's active session as shown when listing sessions: which lineage it belongs to and when its refresh token expires. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/ActiveSession.java`
- **AuthenticationBlock** — A temporary suspension of authentication attempts from a given IpAddress. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/AuthenticationBlock.java`
- **AuthenticationBlockRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/AuthenticationBlockRepository.java`
- **AuthenticationEvent** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/event/AuthenticationEvent.java`
- **AuthenticationRequest** — A user's attempt to authenticate and gain access to the system. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/AuthenticationRequest.java`
- **AuthorizationDataRepository** — Stores the sessions issued to users, keyed by their refresh token — the credential a client presents to refresh. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/AuthorizationDataRepository.java`
- **AuthorizationTokenExpiration** — The point in time at which an AccessToken expires. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/AuthorizationTokenExpiration.java`
- **BruteForceProtectionEvent** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/event/BruteForceProtectionEvent.java`
- **CarSpec** — A car's aerodynamic concept: the tuned parameters (downforce, drag, and whatever else an era's rulebook opens up). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/CarSpec.java`
- **Championship** — The two title fights across a season: points per driver and per constructor. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/entity/Championship.java`
- **Comment** — A comment posted on a meme: its id, the meme it belongs to, the author and the text. `microservice-comments/src/main/java/com/jrobertgardzinski/comments/domain/Comment.java` · _used in_ comment-thread
- **Contract** — A driver's deal with a team: a salary (a season expense), how many seasons remain, and their role. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Contract.java`
- **Credentials** — Proof of identity presented by a user during authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/Credentials.java` · _used in_ authenticate
- **DeletedAccount** — The identity shown where an author's account is gone — the content survives, the name does not. `microservice-comments/src/main/java/com/jrobertgardzinski/comments/domain/DeletedAccount.java`
- **DeletedAccount** — The identity shown where an author's account is gone — the content survives, the name does not. `microservice-memes/memes-domain/src/main/java/com/jrobertgardzinski/memes/domain/DeletedAccount.java`
- **DomainPart** — The mail server portion of an Email, following the '@' symbol. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/DomainPart.java`
- **Driver** — An autonomous driver: an identity plus the DriverAttributes the simulation acts on. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/Driver.java`
- **DriverAttributes** — What makes a driver an autonomous character rather than a lap-time constant (the Jagged Alliance 2 idea: mercenaries have stats and personality, and the game emerges from them). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/DriverAttributes.java`
- **DriverRepository** — Port for storing the driver roster. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/repository/DriverRepository.java`
- **Email** — An email address, composed of a LocalPart and a DomainPart. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/Email.java` · _used in_ change-email, register, verify-email
- **EmailAlreadyTakenException** — Thrown by UserRepository#save when persistence rejects a user because the (normalized) email is already taken — the storage-level uniqueness guarantee, which closes the check-then-act race that a prior existsBy check alone cannot. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailAlreadyTakenException.java`
- **EmailChange** — A requested email change awaiting confirmation: from the user's current address to the new one. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/EmailChange.java`
- **EmailChangeRepository** — Tracks pending email changes. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailChangeRepository.java`
- **EmailVerificationNotifier** — Outbound port that delivers a verification link — carrying the single-use token — to a user's e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/port/EmailVerificationNotifier.java`
- **EmailVerificationRepository** — Tracks pending e-mail verifications and their outcome. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/EmailVerificationRepository.java`
- **Era** — The active rulebook the whole field races under (e.g. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Era.java`
- **EraChange** — The result of moving to the next era: the new rulebook in force and the car reshaped to it. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/EraChange.java`
- **FailuresCount** — Consecutive failed authentication count for a given IpAddress. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/FailuresCount.java`
- **GameRepository** — Durability for the domain aggregates, one thing at a time: a team by its manager key, a driver, a contract by driver id, the shared season and championship. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/repository/GameRepository.java`
- **GameWorld** — The durable game state rehydrated on startup: the teams (by manager key), the driver contracts (by driver id), where the season stands, the championship so far, the drivers on the books and their developable personnel state (by driver id). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/repository/GameWorld.java`
- **HashAlgorithmPort** — _(no Javadoc yet)_ `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/HashAlgorithmPort.java`
- **HashedPassword** — A password transformed by a hashing algorithm, safe for storage. `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/HashedPassword.java`
- **IpAddress** — Network address from which a request originates. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/IpAddress.java`
- **LifecycleSimulation** — The personnel lifecycle simulation as the domain needs it: turn the whole pool of people over a season boundary — age them, retire the old, roll injuries and (only under a server's permadeath setting) deaths, and generate juniors so the grid never empties over the decades an era timeline spans. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/port/LifecycleSimulation.java`
- **LocalPart** — The recipient-specific portion of an Email, preceding the '@' symbol. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/LocalPart.java`
- **Market** — The driver labour market — JA2's A.I.M. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/service/Market.java`
- **Meme** — A stored meme: an identifier, who uploaded it, the (browser-friendly) image format, and the image bytes. `microservice-memes/memes-domain/src/main/java/com/jrobertgardzinski/memes/domain/Meme.java` · _used in_ tag-meme, upload-meme, vote-meme
- **Money** — Team money, in credits (~millions). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Money.java`
- **NormalizedEmail** — An Email with provider-specific normalization applied, used for deduplication. `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/NormalizedEmail.java`
- **Observability** — How much rivals can see of one car-development parameter. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Observability.java`
- **PasswordResetNotifier** — Outbound port that delivers a password-reset link — carrying the single-use token — to a user's e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/port/PasswordResetNotifier.java`
- **PasswordResetRepository** — Tracks pending password resets. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/PasswordResetRepository.java`
- **PasswordResetToken** — Single-use token e-mailed to a user so they can set a new password after forgetting the old one. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/PasswordResetToken.java` · _used in_ reset-password
- **Personnel** — A person's developable state, alongside their durable attributes (which are the BASE). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/entity/Personnel.java`
- **PersonStatus** — Where a person is in their working life. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/PersonStatus.java`
- **PlaintextPassword** — A password as entered by the user, before hashing. `password/password-domain/src/main/java/com/jrobertgardzinski/password/domain/PlaintextPassword.java`
- **PurgeChoices** — The leaver's choice of what happens to their content elsewhere, made in the deletion wizard and carried through the saga. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/PurgeChoices.java`
- **RaceResult** — The classification of a finished race, best first. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/RaceResult.java`
- **RaceSimulation** — The external race/engineering simulation, as the domain needs it — in domain terms, not JSON. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/port/RaceSimulation.java`
- **RaceSpec** — What race to run — for the walking skeleton just the number of laps. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/RaceSpec.java`
- **RaceStanding** — One classified driver in a race result: final position (1 = winner), who, and the total race time in milliseconds. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/RaceStanding.java`
- **RankedMeme** — A meme with its current vote score, for ranking ("hot"). `microservice-memes/memes-domain/src/main/java/com/jrobertgardzinski/memes/domain/RankedMeme.java` · _used in_ vote-meme
- **Ranking** — One row of a championship table: a competitor (a driver or a team) and their points. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Ranking.java`
- **RefreshToken** — Long-lived refresh token used to obtain a new access token without re-authentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/RefreshToken.java` · _used in_ logout, reuse-detection, revoke-all-sessions
- **RefreshTokenExpiration** — Expiration of a refresh token. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/expiration/RefreshTokenExpiration.java`
- **RefreshTokenValidityInHours** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RefreshTokenValidityInHours.java`
- **Regulations** — The active era's rules as the domain cares about them: how much a team may spend on development this season (the cost cap — the anti-pay-to-win rule) and how race positions score. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Regulations.java`
- **RejectedAuthentication** — A recorded instance of a rejected authentication attempt. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/RejectedAuthentication.java`
- **RejectedAuthenticationDetails** — Details of a RejectedAuthentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RejectedAuthenticationDetails.java`
- **RejectedAuthenticationId** — Identity of a RejectedAuthentication. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/RejectedAuthenticationId.java`
- **RejectedAuthenticationRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/RejectedAuthenticationRepository.java`
- **Reveal** — How much of a car's spec the rest of the field can see, parameter by parameter. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Reveal.java`
- **Role** — A driver's standing within the team. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/Role.java` · _used in_ roles
- **Role** — What a user is allowed to be across the whole system (flat RBAC): every signed-in user is a #USER; a #MODERATOR may act on other people's content (hide, remove, flag); an #ADMIN additionally administers the system, including granting these very roles. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/Role.java`
- **SaveResult** — Result of UserRepository#save. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/SaveResult.java`
- **Season** — Where the championship is in time: the season number, the round on the calendar, and the era in force. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/entity/Season.java`
- **SessionFamily** — Identifies a session lineage: the original session created at authentication and every session it is rotated into on refresh share one family. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionFamily.java` · _used in_ reuse-detection
- **SessionRefreshRequest** — A request to renew a session without re-authenticating. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionRefreshRequest.java`
- **SessionStatus** — Lifecycle of a stored session. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionStatus.java`
- **SessionTokens** — An active session represented by a pair of tokens. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/SessionTokens.java`
- **SessionTokensConfig** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/SessionTokensConfig.java`
- **SimulationEngine** — Port for whatever actually races the drivers. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/port/SimulationEngine.java`
- **StoredSession** — The stored essentials of a session, found by its refresh token: who it belongs to, when its refresh token expires, which lineage it belongs to (SessionFamily) and whether it is still active or already rotated. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/StoredSession.java`
- **Tag** — A meme tag: the folksonomy's atom. `microservice-memes/memes-tags/src/main/java/com/jrobertgardzinski/memes/tags/Tag.java` · _used in_ tag-meme
- **Team** — A constructor — one manager's team. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/entity/Team.java`
- **TrainingSimulation** — The personnel-development simulation as the domain needs it: advance one person a training week and get their new developable state back. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/port/TrainingSimulation.java`
- **TunnelLevel** — How hard a wind-tunnel / R&D programme is pushed. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/domain/vo/TunnelLevel.java`
- **User** — A registered participant in the system, with the Roles they hold. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/User.java` · _used in_ authenticate, authorize, change-email, change-password, delete-account, list-sessions, logout, refresh-session, register, reset-password, reuse-detection, revoke-all-sessions, roles, verify-email
- **UserRegistration** — A prospective user's request to join the system, with the password already hashed. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/UserRegistration.java`
- **UserRepository** — _(no Javadoc yet)_ `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/repository/UserRepository.java`
- **VerificationToken** — Single-use token e-mailed to a user to prove they own their e-mail address. `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/vo/token/VerificationToken.java` · _used in_ verify-email

## Config (39)

- **BlockedDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/BlockedDomains.java`
- **BruteForceConfig** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/BruteForceConfig.java`
- **CampaignConfig** — The server operator's campaign settings — per-server configuration like com.jrobertgardzinski.formula.config.mortality.MortalityConfig: which rulebook the campaign opens under, how many seasons it runs (0 = endless), and which managers may run a team here (empty roster = open server). `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/campaign/CampaignConfig.java`
- **CanRegisterConfig** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/CanRegisterConfig.java`
- **CompanyDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/CompanyDomains.java`
- **ConfigKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/ConfigKey.java`
- **DisposableDomains** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/DisposableDomains.java`
- **EconomyConfig** — The sport's money policy — a server-tunable configuration, the same idea as the brute-force config in security. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/EconomyConfig.java`
- **EmailConfigPort** — _(no Javadoc yet)_ `email/email-security/email-security-config/src/main/java/com/jrobertgardzinski/email/config/port/EmailConfigPort.java`
- **FailureWindowMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/FailureWindowMinutes.java`
- **HardcodedConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/hardcoded/HardcodedConfigSource.java`
- **HardcodedKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/HardcodedKey.java`
- **ImageLimits** — Configuration for image optimisation: the largest dimension (width or height, in pixels) a stored meme may have. `microservice-memes/memes-config/src/main/java/com/jrobertgardzinski/memes/config/ImageLimits.java`
- **MaxBlockMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MaxBlockMinutes.java`
- **MaxFailures** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MaxFailures.java`
- **MinBlockMinutes** — _(no Javadoc yet)_ `microservice-security/security-config/src/main/java/com/jrobertgardzinski/security/config/bruteforce/vo/MinBlockMinutes.java`
- **MinLength** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/MinLength.java`
- **MortalityConfig** — The server operator's mortality setting: whether on- and off-track deaths are real (permadeath) or downgrade to career-ending injuries. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/mortality/MortalityConfig.java`
- **PropertiesConfigPort** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/properties/PropertiesConfigPort.java`
- **PropertiesConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/properties/PropertiesConfigSource.java`
- **PropertiesKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/PropertiesKey.java`
- **PurgeRule** — What happens to a leaver's comment (this service's axis of the account-deletion saga): delete it; keep it with the author anonymised; or decide by popularity. `microservice-comments/src/main/java/com/jrobertgardzinski/comments/config/PurgeRule.java`
- **PurgeRule** — What happens to a piece of a leaver's content. `microservice-memes/memes-config/src/main/java/com/jrobertgardzinski/memes/config/PurgeRule.java`
- **RateLimit** — A fixed per-key, per-minute window — server policy against comment spam. `microservice-comments/src/main/java/com/jrobertgardzinski/comments/config/RateLimit.java`
- **RateLimit** — A fixed per-key, per-minute window — server policy against abuse. `microservice-memes/memes-config/src/main/java/com/jrobertgardzinski/memes/config/RateLimit.java`
- **RepairUnitCost** — A single tunable of the economy policy. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/vo/RepairUnitCost.java`
- **RepositoryConfigPort** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/repository/RepositoryConfigPort.java`
- **RepositoryConfigSource** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/source/repository/RepositoryConfigSource.java`
- **RepositoryKey** — _(no Javadoc yet)_ `config/src/main/java/com/jrobertgardzinski/config/domain/RepositoryKey.java`
- **RequiresDigit** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresDigit.java`
- **RequiresLowercase** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresLowercase.java`
- **RequiresUppercase** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/RequiresUppercase.java`
- **ResearchBaseCost** — A single tunable of the economy policy. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/vo/ResearchBaseCost.java`
- **SeveredPartCost** — A single tunable of the economy policy. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/vo/SeveredPartCost.java`
- **SpecialChars** — _(no Javadoc yet)_ `password/password-security/password-security-config/src/main/java/com/jrobertgardzinski/password/security/config/SpecialChars.java`
- **Sponsorship** — A single tunable of the economy policy. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/vo/Sponsorship.java`
- **StartingBudget** — A single tunable of the economy policy. `formula-simulator/src/main/java/com/jrobertgardzinski/formula/config/economy/vo/StartingBudget.java`
- **TagLimits** — Server policy on tagging: how many tags one meme may carry (folksonomy, not keyword spam). `microservice-memes/memes-config/src/main/java/com/jrobertgardzinski/memes/config/TagLimits.java`
- **ThumbnailSize** — Configuration for meme thumbnails: the largest dimension (px) a generated thumbnail may have. `microservice-memes/memes-config/src/main/java/com/jrobertgardzinski/memes/config/ThumbnailSize.java`

## System (38)

- **Authentication** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/authentication/Authentication.java` · _used in_ authenticate, change-email, change-password, delete-account, list-sessions, reset-password
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
- **DeleteAccount** — Closes a user's account (GDPR right to be forgotten): revokes every session and deletes the user, so the account can no longer authenticate and its access tokens stop authorizing. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/DeleteAccount.java`
- **EmailErrorCodes** — The email error codes of a registration attempt — a type deliberately distinct from PasswordErrorCodes, so the two channels can never be swapped when a RegisterResult.Rejected is built. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/EmailErrorCodes.java`
- **ListActiveSessions** — Lists a user's currently active sessions, so they can see where they are signed in and choose to revoke them. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/ListActiveSessions.java` · _used in_ list-sessions
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
- **Result** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/roles/SetUserRoles.java`
- **RevokeAllSessions** — Logs a user out everywhere: revokes every session the user holds, across all lineages, so no refresh token can be refreshed and no access token authorizes any longer. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/session/RevokeAllSessions.java` · _used in_ revoke-all-sessions
- **SetUserRoles** — Grant or revoke a user's roles — the admin action behind flat RBAC. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/roles/SetUserRoles.java` · _used in_ roles
- **StartAccountDeletion** — Opens the account-closure saga (GDPR right to be forgotten): the account locks at once — every session revoked, sign-in refused — and the user's content elsewhere is asked to be purged. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/account/StartAccountDeletion.java` · _used in_ delete-account
- **Status** — _(no Javadoc yet)_ `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/roles/SetUserRoles.java`
- **VerifyEmail** — Completes e-mail verification: a matching, unused token marks the address verified; an unknown or already-used token is rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/verification/VerifyEmail.java` · _used in_ verify-email
- **VerifyEmailResult** — Outcome of VerifyEmail: the address was verified, or the token was rejected. `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/verification/VerifyEmailResult.java`

## Boundary (6)

- **ApiKeyFilter** — Boundary guard: only trusted callers presenting the shared secret in the X-Api-Key header may send mail. `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/ApiKeyFilter.java`
- **DlqResource** — The re-drive: an operator lists what died on the dead-letter topic and pushes one event back through the NORMAL delivery path (same consumer, same retries — and the same parking if the world is still broken). `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/DlqResource.java`
- **MailRequestsConsumer** — The asynchronous boundary (BCE): mail requests arriving on the mail-requests Kafka topic — published by microservice-security's transactional outbox. `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/MailRequestsConsumer.java`
- **MailResource** — Boundary (BCE): the REST entry point other services send mail commands to. `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/MailResource.java`
- **ParkedMails** — The operator's view of the dead-letter topic: parked events are read back into a bounded in-memory ledger so GET /mails/dlq can show them and a re-drive can resubmit one through the normal delivery path. `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/ParkedMails.java`
- **RateLimitFilter** — Boundary guard number two, AFTER the API key: a fixed per-minute window over the whole REST boundary, so one runaway caller cannot flood the SMTP server through us (the Kafka boundary has its own pacing — the topic is the buffer). `microservice-email/src/main/java/com/jrobertgardzinski/mail/boundary/RateLimitFilter.java`

## Control (1)

- **MailDispatcher** — Control (BCE): turns entities into actual sends via the ReactiveMailer. `microservice-email/src/main/java/com/jrobertgardzinski/mail/control/MailDispatcher.java`

## Entity (2)

- **LinkMail** — Entity (BCE): a mail that carries a single action link to a recipient (used by the verification and password-reset templated sends). `microservice-email/src/main/java/com/jrobertgardzinski/mail/entity/LinkMail.java`
- **Mail** — Entity (BCE): the message to send — recipient, subject and plain-text body. `microservice-email/src/main/java/com/jrobertgardzinski/mail/entity/Mail.java` · _used in_ send-mail

## Features → terms

- `microservice-comments/src/test/resources/features/comment-thread.feature` — `Comment`
- `microservice-email/src/test/resources/features/send-mail.feature` — `Mail`
- `microservice-memes/memes-infrastructure/src/test/resources/features/tag-meme.feature` — `Meme` `Tag`
- `microservice-memes/memes-infrastructure/src/test/resources/features/upload-meme.feature` — `Meme`
- `microservice-memes/memes-infrastructure/src/test/resources/features/vote-meme.feature` — `Meme` `RankedMeme`
- `microservice-security/security-infrastructure/src/test/resources/roles.feature` — `Role` `SetUserRoles` `User`
- `microservice-security/specs/authenticate.feature` — `Authentication` `Credentials` `User`
- `microservice-security/specs/authorize.feature` — `AccessToken` `User`
- `microservice-security/specs/change-email.feature` — `Authentication` `ConfirmEmailChange` `Email` `RequestEmailChange` `User`
- `microservice-security/specs/change-password.feature` — `Authentication` `ChangePassword` `User`
- `microservice-security/specs/delete-account.feature` — `Authentication` `Register` `StartAccountDeletion` `User`
- `microservice-security/specs/list-sessions.feature` — `Authentication` `ListActiveSessions` `User`
- `microservice-security/specs/logout.feature` — `AccessToken` `Logout` `RefreshSession` `RefreshToken` `User`
- `microservice-security/specs/refresh-session.feature` — `RefreshSession` `User`
- `microservice-security/specs/register.feature` — `Email` `Register` `User`
- `microservice-security/specs/reset-password.feature` — `Authentication` `PasswordResetToken` `ResetPassword` `User`
- `microservice-security/specs/reuse-detection.feature` — `RefreshSession` `RefreshToken` `SessionFamily` `User`
- `microservice-security/specs/revoke-all-sessions.feature` — `AccessToken` `RefreshSession` `RefreshToken` `RevokeAllSessions` `User`
- `microservice-security/specs/verify-email.feature` — `Email` `User` `VerificationToken` `VerifyEmail`
