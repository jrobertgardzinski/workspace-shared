# Architektura — diagramy C4 (generowane)

> **NIE EDYTUJ RĘCZNIE.** Wygenerowane przez `./build_c4.py` z dwóch źródeł prawdy:
> `docker-compose.yml` (topologia runtime: krawędzie HTTP, magazyny, kto siedzi na Kafce)
> i commitowanych paktów `*/pacts*/*.json` (kierunek i semantyka zdarzeń, kontrakty HTTP).
> Zmienił się stack → odpal `python3 build_c4.py` i diagramy nadążą same.

## C1 — kontekst systemu

```mermaid
flowchart LR
  member(["👤 Użytkownik<br/>(gość / member / moderator / admin)"])
  subgraph portfolio ["Portfolio stack (ten workspace)"]
    stack["Serwis społecznościowy wokół gry:<br/>konta+MFA, galeria memów, komentarze,<br/>ulubione, paddock, menedżer F1"]
  end
  oauth["Zewnętrzni dostawcy OIDC<br/>(Google/GitHub… — w dev: stub idp)"]
  smtp["Realny serwer SMTP<br/>(w dev: Mailpit)"]
  member -->|przeglądarka / PWA / apka mobilna| stack
  stack -->|logowanie federacyjne| oauth
  stack -->|maile transakcyjne| smtp
```

## C2 — kontenery: krawędzie synchroniczne (HTTP) i magazyny

Krawędzie serwis→serwis wyprowadzone ze zmiennych środowiskowych w `docker-compose.yml`;
etykieta = intencja wywołania. Obserwowalność (Prometheus/Grafana/Loki/Promtail/Tempo)
celowo zwinięta — widzi wszystkie kontenery, na diagramie byłaby spaghetti.

```mermaid
flowchart LR
  browser(["👤 Przeglądarka"])
  collections_ui["collections-ui<br/><i>React Native Web + nginx</i>"]
  comments["comments<br/><i>Spring Boot</i>"]
  email["email<br/><i>Quarkus</i>"]
  formula["formula<br/><i>JDK HttpServer</i>"]
  idp["idp<br/><i>Python (stub OIDC)</i>"]
  image_encoder["image-encoder<br/><i>Python + Pillow</i>"]
  memes["memes<br/><i>Spring Boot</i>"]
  paddock["paddock<br/><i>Javalin</i>"]
  push["push<br/><i>Python (stub)</i>"]
  race_sim["race-sim<br/><i>Python (stdlib)</i>"]
  security["security<br/><i>Micronaut</i>"]
  sms["sms<br/><i>Python (stub)</i>"]
  user_collections["user-collections<br/><i>Helidon SE</i>"]
  collections_postgres[("collections-postgres")]
  comments_postgres[("comments-postgres")]
  formula_postgres[("formula-postgres")]
  memes_postgres[("memes-postgres")]
  minio[("minio")]
  paddock_postgres[("paddock-postgres")]
  postgres[("postgres")]
  mailpit["mailpit (dev SMTP)"]
  browser -->|"gallery UI"| memes
  browser -->|"favourites UI"| collections_ui
  browser -->|"paddock PWA"| paddock
  browser -->|"race viewer (SSE)"| formula
  browser -->|"API + OAuth callback"| security
  browser -->|"favourites API (CORS)"| user_collections
  browser -->|"stub sign-in form"| idp
  browser -->|"dev inbox"| mailpit
  security -->|"OIDC code exchange"| idp
  security -->|"MFA codes"| sms
  email -->|"SMTP"| mailpit
  memes -->|"token check"| security
  memes -->|"PNG→WebP"| image_encoder
  comments -->|"token check"| security
  comments -->|"meme lookup"| memes
  paddock -->|"token check"| security
  paddock -->|"mail (best-effort)"| email
  paddock -->|"SMS (best-effort)"| sms
  paddock -->|"push (best-effort)"| push
  user_collections -->|"token check"| security
  formula -->|"run race (sealed)"| race_sim
  formula -->|"token check"| security
  security --> postgres
  memes --> memes_postgres
  memes --> minio
  comments --> comments_postgres
  paddock --> paddock_postgres
  user_collections --> collections_postgres
  formula --> formula_postgres
```

Uwagi nie do wyprowadzenia z env-ów (kuratorowane w skrypcie): krawędzie przeglądarki —
UI wypiekają adresy API w buildzie; `race-sim` nie ma portu na hosta (rozmawia z nim tylko
formula); collections-ui woła security i user-collections **cross-origin** (CORS).

## C2 — kontenery: szyna zdarzeń (Kafka)

Kierunki przepływów pochodzą z **paktów message** (producer = provider paktu) — tego
`docker-compose.yml` nie wie (env mówi tylko „siedzi na Kafce”). 📜 = krawędź przypięta
kontraktem, ×N = liczba kształtów wiadomości w pakcie.

```mermaid
flowchart LR
  kafka{{"Apache Kafka — event backbone"}}
  p_comments["comments"]
  p_memes["memes"]
  p_security["security"]
  p_user_collections["user-collections"]
  c_comments["comments"]
  c_email["email"]
  c_memes["memes"]
  c_security["security"]
  c_user_collections["user-collections"]
  p_comments -.->|"comments-events"| kafka
  kafka -.->|"comments-events ×1 📜"| c_security
  p_memes -.->|"memes-events"| kafka
  kafka -.->|"memes-events MEME_DELETED (bez paktu)"| c_comments
  kafka -.->|"memes-events ×1 📜"| c_security
  p_security -.->|"content-commands"| kafka
  kafka -.->|"content-commands ×2 📜"| c_comments
  kafka -.->|"content-commands ×2 📜"| c_memes
  kafka -.->|"content-commands ×1 📜"| c_user_collections
  p_security -.->|"mail-requests"| kafka
  kafka -.->|"mail-requests ×6 📜"| c_email
  p_user_collections -.->|"usercollections-events"| kafka
  kafka -.->|"usercollections-events ×1 📜"| c_security
```

(Serwis może stać po obu stronach szyny — security produkuje komendy i konsumuje
potwierdzenia — dlatego występuje po lewej i po prawej.)

Na Kafce siedzą (env `KAFKA_BOOTSTRAP_SERVERS`): comments, email, memes, security, user-collections.

## Pokrycie kontraktami (Pact)

| Konsument | Producent/Provider | Rodzaj | Interakcje | Plik paktu |
|---|---|---|---|---|
| comments | security | message | a purge user content command; a purge user content command with an explicit policy | `microservice-comments/pacts/microservice-comments-microservice-security.json` |
| email | security | message | a password reset mail request; a verification mail request; an account deleted mail request; an account deletion failed mail request; an already-registered notice mail request; an auth code mail request | `microservice-email/pacts/microservice-email-microservice-security.json` |
| memes | security | http | GET /me (×2) | `microservice-memes/pacts-http/microservice-memes-microservice-security.json` |
| memes | security | message | a purge user content command; a purge user content command with an explicit policy | `microservice-memes/pacts/microservice-memes-microservice-security.json` |
| security | comments | message | a user content purged confirmation | `microservice-security/pacts/microservice-security-microservice-comments.json` |
| security | memes | message | a user content purged confirmation | `microservice-security/pacts/microservice-security-microservice-memes.json` |
| security | user-collections | message | a user content purged confirmation | `microservice-security/pacts/microservice-security-microservice-user-collections.json` |
| user-collections | security | message | a purge user content command | `microservice-user-collections/pacts/microservice-user-collections-microservice-security.json` |
| offline-jwt | security | http | GET /.well-known/jwks.json | `offline-jwt/pacts/offline-jwt-microservice-security.json` |

Luki widoczne z tej tabeli (stan wygenerowania): paddock (fan-out email/sms/push),
formula↔race-sim i memes→image-encoder nie mają paktów — krawędzie na diagramach pochodzą
wtedy wyłącznie z compose; kaskada `MEME_DELETED` jest oznaczona „bez paktu”.

---
*Wygenerowano skryptem `build_c4.py`. C1 i wpisy „kuratorowane” są zaszyte w skrypcie —*
*zmieniasz je tam, nie tutaj.*
