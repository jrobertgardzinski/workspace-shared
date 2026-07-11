# Architektura — diagramy C4 (generowane)

> **NIE EDYTUJ RĘCZNIE.** Wygenerowane przez `./build_c4.py` z dwóch źródeł prawdy:
> `docker-compose.yml` (topologia runtime: krawędzie HTTP, magazyny, kto siedzi na Kafce)
> i commitowanych paktów `*/pacts*/*.json` (kierunek i semantyka zdarzeń, kontrakty HTTP).
> Zmienił się stack → odpal `python3 build_c4.py` i diagramy nadążą same.

## C1 — kontekst systemu

**Dwa produkty, nie jeden** (werdykt właściciela 2026-07-11): portal społecznościowy i gra F1 to osobne byty. Dzielą wyłącznie TOŻSAMOŚĆ (wspólne konto + MFA) i ten dev-compose; gra nie ma ani jednej krawędzi do memów/komentarzy/Kafki, a produkcyjnie wyjeżdża osobno (hosting/ per liga).

```mermaid
flowchart LR
  member(["👤 Użytkownik<br/>(gość / member / moderator / admin)"])
  subgraph portfolio ["Ten workspace — dwa produkty na wspólnych kontach"]
    portal["PORTAL społecznościowy:<br/>galeria memów, komentarze, ulubione,<br/>hub paddock (serwery/ludzie/wydarzenia)"]
    game["GRA F1 (osobny byt):<br/>menedżer + autorytatywna symulacja,<br/>ligi prywatne / serwery grupowe"]
    idp["Wspólna tożsamość:<br/>konta + MFA<br/>(microservice-security)"]
  end
  oauth["Zewnętrzni dostawcy OIDC<br/>(Google/GitHub… — w dev: stub idp)"]
  smtp["Realny serwer SMTP<br/>(w dev: Mailpit)"]
  member -->|przeglądarka / PWA| portal
  member -->|przeglądarka / PWA / apka mobilna| game
  portal -->|jeden token| idp
  game -->|jeden token| idp
  idp -->|logowanie federacyjne| oauth
  idp -->|maile transakcyjne kont| smtp
```

## C2 — kontenery: krawędzie synchroniczne (HTTP) i magazyny

Krawędzie serwis→serwis wyprowadzone ze zmiennych środowiskowych w `docker-compose.yml`;
etykieta = intencja wywołania. **Granica produktów jest inwariantem generatora**: jedyne
krawędzie wychodzące z GRY prowadzą do wspólnej tożsamości — gdyby kiedyś powstała krawędź
gra↔portal, `build_c4.py` przerwie z błędem zamiast ją narysować. Obserwowalność
(Prometheus/Grafana/Loki/Promtail/Tempo) celowo zwinięta — widzi wszystkie kontenery,
na diagramie byłaby spaghetti.

```mermaid
flowchart LR
  browser(["👤 Przeglądarka"])
  subgraph portal ["PORTAL społecznościowy"]
    collections_ui["collections-ui<br/><i>React Native Web + nginx</i>"]
    comments["comments<br/><i>Spring Boot</i>"]
    email["email<br/><i>Quarkus</i>"]
    image_encoder["image-encoder<br/><i>Python + Pillow</i>"]
    memes["memes<br/><i>Spring Boot</i>"]
    offboarding["offboarding<br/><i>Helidon SE (process manager)</i>"]
    paddock["paddock<br/><i>Javalin</i>"]
    push["push<br/><i>Python (stub)</i>"]
    sms["sms<br/><i>Python (stub)</i>"]
    user_collections["user-collections<br/><i>Helidon SE</i>"]
    collections_postgres[("collections-postgres")]
    comments_postgres[("comments-postgres")]
    memes_postgres[("memes-postgres")]
    minio[("minio")]
    offboarding_postgres[("offboarding-postgres")]
    paddock_postgres[("paddock-postgres")]
    mailpit["mailpit (dev SMTP)"]
  end
  subgraph identity ["Wspólna TOŻSAMOŚĆ (jedyny łącznik produktów)"]
    idp["idp<br/><i>Python (stub OIDC)</i>"]
    security["security<br/><i>Micronaut</i>"]
    postgres[("postgres")]
  end
  subgraph game ["GRA F1 — osobny produkt"]
    formula["formula<br/><i>JDK HttpServer</i>"]
    race_sim["race-sim<br/><i>Python (stdlib)</i>"]
    formula_postgres[("formula-postgres")]
  end
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
  offboarding --> offboarding_postgres
  formula --> formula_postgres
```

Uwagi nie do wyprowadzenia z env-ów (kuratorowane w skrypcie): krawędzie przeglądarki —
UI wypiekają adresy API w buildzie; `race-sim` nie ma portu na hosta (rozmawia z nim tylko
formula); collections-ui woła security i user-collections **cross-origin** (CORS).
Kanały powiadomień (email/sms/push) mieszkają po stronie portalu; tożsamość sięga do nich
przez granicę (kody MFA, maile kont) — patrz ADR 0005.

## C2 — kontenery: szyna zdarzeń (Kafka)

Kierunki przepływów pochodzą z **paktów message** (producer = provider paktu) — tego
`docker-compose.yml` nie wie (env mówi tylko „siedzi na Kafce”). 📜 = krawędź przypięta
kontraktem, ×N = liczba kształtów wiadomości w pakcie.

```mermaid
flowchart LR
  kafka{{"Apache Kafka — event backbone"}}
  p_comments["comments"]
  p_memes["memes"]
  p_offboarding["offboarding"]
  p_security["security"]
  p_user_collections["user-collections"]
  c_comments["comments"]
  c_email["email"]
  c_memes["memes"]
  c_offboarding["offboarding"]
  c_security["security"]
  c_user_collections["user-collections"]
  p_comments -.->|"comments-events"| kafka
  kafka -.->|"comments-events ×1 📜"| c_offboarding
  p_memes -.->|"memes-events"| kafka
  kafka -.->|"memes-events MEME_DELETED (bez paktu)"| c_comments
  kafka -.->|"memes-events ×1 📜"| c_offboarding
  p_offboarding -.->|"content-commands"| kafka
  kafka -.->|"content-commands ×2 📜"| c_comments
  kafka -.->|"content-commands ×2 📜"| c_memes
  kafka -.->|"content-commands ×1 📜"| c_user_collections
  p_offboarding -.->|"offboarding-events"| kafka
  kafka -.->|"offboarding-events ×2 📜"| c_security
  p_security -.->|"mail-requests"| kafka
  kafka -.->|"mail-requests ×6 📜"| c_email
  p_security -.->|"security-events"| kafka
  kafka -.->|"security-events ×2 📜"| c_offboarding
  p_user_collections -.->|"usercollections-events"| kafka
  kafka -.->|"usercollections-events ×1 📜"| c_offboarding
```

(Serwis może stać po obu stronach szyny — offboarding konsumuje fakt i potwierdzenia,
a produkuje komendy i werdykty — dlatego występuje po lewej i po prawej.)

Na Kafce siedzą (env `KAFKA_BOOTSTRAP_SERVERS`): comments, email, memes, offboarding, security, user-collections. **Gra F1 nie ma tu ani
jednej krawędzi** — szyna zdarzeń należy do portalu i tożsamości (osobne produkty;
generator pilnuje tego twardo).

## Pokrycie kontraktami (Pact)

| Konsument | Producent/Provider | Rodzaj | Interakcje | Plik paktu |
|---|---|---|---|---|
| comments | offboarding | message | a purge user content command; a purge user content command with an explicit policy | `microservice-comments/pacts/microservice-comments-microservice-offboarding.json` |
| email | security | message | a password reset mail request; a verification mail request; an account deleted mail request; an account deletion failed mail request; an already-registered notice mail request; an auth code mail request | `microservice-email/pacts/microservice-email-microservice-security.json` |
| memes | security | http | GET /me (×2) | `microservice-memes/pacts-http/microservice-memes-microservice-security.json` |
| memes | offboarding | message | a purge user content command; a purge user content command with an explicit policy | `microservice-memes/pacts/microservice-memes-microservice-offboarding.json` |
| offboarding | comments | message | a user content purged confirmation | `microservice-offboarding/pacts/microservice-offboarding-microservice-comments.json` |
| offboarding | memes | message | a user content purged confirmation | `microservice-offboarding/pacts/microservice-offboarding-microservice-memes.json` |
| offboarding | security | message | an account deletion requested fact; an account deletion requested fact with policy choices | `microservice-offboarding/pacts/microservice-offboarding-microservice-security.json` |
| offboarding | user-collections | message | a user content purged confirmation | `microservice-offboarding/pacts/microservice-offboarding-microservice-user-collections.json` |
| security | offboarding | message | a portal content purged announcement; a portal purge failed announcement | `microservice-security/pacts/microservice-security-microservice-offboarding.json` |
| user-collections | offboarding | message | a purge user content command | `microservice-user-collections/pacts/microservice-user-collections-microservice-offboarding.json` |
| offline-jwt | security | http | GET /.well-known/jwks.json | `offline-jwt/pacts/offline-jwt-microservice-security.json` |

Luki widoczne z tej tabeli (stan wygenerowania): paddock (fan-out email/sms/push),
formula↔race-sim i memes→image-encoder nie mają paktów — krawędzie na diagramach pochodzą
wtedy wyłącznie z compose; kaskada `MEME_DELETED` jest oznaczona „bez paktu”.

---
*Wygenerowano skryptem `build_c4.py`. C1 i wpisy „kuratorowane” są zaszyte w skrypcie —*
*zmieniasz je tam, nie tutaj.*
