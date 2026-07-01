# Ubiquitous-Language glossary

_Generated from `domain`/`config`/`security-system` code by `build_glossary.py` — do not edit by hand._

66 nouns · 8 verbs · 1 feature files tagged.

## Nouns (types)

### Email
*CAPS:* `EMAIL`  ·  *source:* `email/email-domain/src/main/java/com/jrobertgardzinski/email/domain/Email.java`

An email address, composed of a {@link LocalPart} and a {@link DomainPart}.

*Tagged in:* `microservice-security/specs/register.feature`

### User
*CAPS:* `USER`  ·  *source:* `microservice-security/security-domain/src/main/java/com/jrobertgardzinski/security/domain/entity/User.java`

A registered participant in the system.

*Tagged in:* `microservice-security/specs/register.feature`

## Verbs (operations)

### Register · `Register`
*CAPS:* `REGISTER`  ·  *source:* `microservice-security/security-system/src/main/java/com/jrobertgardzinski/security/system/registration/Register.java`

_(no Javadoc yet)_

*Tagged in:* `microservice-security/specs/register.feature`

## Features → tags

- `microservice-security/specs/register.feature` — `Email` `Register` `User`
