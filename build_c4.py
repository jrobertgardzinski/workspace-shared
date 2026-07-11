#!/usr/bin/env python3
"""Generate C4-style architecture diagrams (Mermaid) for the workspace.

Hand-drawn architecture diagrams drift the moment the system changes, so this script derives
them from the two machine-readable sources of truth the workspace already maintains:

- docker-compose.yml — the runtime topology: which containers exist, who calls whom over HTTP
  (service-to-service env URLs), who owns which datastore, who is attached to Kafka;
- the committed Pact files (<consumer>/pacts*/​*.json) — the contract-pinned interactions, which
  carry what compose cannot: the DIRECTION and semantics of the asynchronous flows (who produces
  which event, who consumes it) plus the verified HTTP contracts.

Output: docs/c4-architecture.md (Mermaid renders natively on GitHub).

Curated knowledge (kept small, marked in the output): browser-facing entry points, human labels
for env keys, the topic behind each pact message, and known events not yet pinned by a pact.

Usage: python3 build_c4.py
"""

import json
import re
from glob import glob
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "docs" / "c4-architecture.md"

# ---------------------------------------------------------------- curated knowledge

FLAVOURS = {
    "security": "Micronaut",
    "email": "Quarkus",
    "memes": "Spring Boot",
    "comments": "Spring Boot",
    "paddock": "Javalin",
    "user-collections": "Helidon SE",
    "offboarding": "Helidon SE (process manager)",
    "formula": "JDK HttpServer",
    "collections-ui": "React Native Web + nginx",
    "idp": "Python (stub OIDC)",
    "image-encoder": "Python + Pillow",
    "sms": "Python (stub)",
    "push": "Python (stub)",
    "race-sim": "Python (stdlib)",
}

# Human intent behind a service-to-service env URL (fallback: the env key itself).
EDGE_LABELS = {
    "SECURITY_URL": "token check",
    "MEMES_URL": "meme lookup",
    "IMAGE_ENCODER_URL": "PNG→WebP",
    "RACE_SIM_URL": "run race (sealed)",
    "EMAIL_URL": "mail (best-effort)",
    "SMS_URL": "SMS (best-effort)",
    "PUSH_URL": "push (best-effort)",
    "SECURITY_SMS_URL": "MFA codes",
    "QUARKUS_MAILER_HOST": "SMTP",
    "MEMES_S3_ENDPOINT": "image bytes (S3)",
}
OIDC_KEY = re.compile(r"OAUTH_PROVIDERS_.*_(TOKEN|USERINFO)_URL$")

# Browser-facing entry points (not derivable: UIs bake their API URLs at build time).
BROWSER_EDGES = [
    ("memes", "gallery UI"),
    ("collections-ui", "favourites UI"),
    ("paddock", "paddock PWA"),
    ("formula", "race viewer (SSE)"),
    ("security", "API + OAuth callback"),
    ("user-collections", "favourites API (CORS)"),
    ("idp", "stub sign-in form"),
    ("mailpit", "dev inbox"),
]

# Topic behind each pact message description; message pacts state producer (=pact provider)
# and consumer, which compose alone cannot tell.
def topic_for(description: str, producer: str) -> str:
    if "mail request" in description:
        return "mail-requests"
    if "deletion requested fact" in description:
        return "security-events"
    if "portal content purged" in description or "portal purge failed" in description:
        return "offboarding-events"
    if "purge user content command" in description:
        return "content-commands"
    if "purged confirmation" in description:
        return {
            "microservice-memes": "memes-events",
            "microservice-comments": "comments-events",
            "microservice-user-collections": "usercollections-events",
        }[producer]
    raise ValueError(f"unknown message description: {description!r}")

# Async flows that exist in code but are not pinned by a pact (yet) — surfaced, not hidden.
EXTRA_ASYNC = [
    ("memes", "comments", "memes-events", "MEME_DELETED"),
]

# TWO PRODUCTS, not one (the owner's verdict, 2026-07-11): the social PORTAL and the F1 GAME
# share ONLY identity. Containers are grouped accordingly; everything unlisted is the portal
# (incl. the shared notification channels — the portal side is their home, identity reaches
# them across the boundary). render() FAILS if a derived edge ever joins game↔portal directly:
# the diagram would then be exposing product conflation, not documenting architecture.
GAME = {"formula", "race-sim", "formula-postgres"}
IDENTITY = {"security", "idp", "postgres"}


def product_of(name: str) -> str:
    if name in GAME:
        return "game"
    if name in IDENTITY:
        return "identity"
    return "portal"

OBSERVABILITY = {"prometheus", "grafana", "loki", "promtail", "tempo", "cadvisor", "node-exporter"}

# ---------------------------------------------------------------- derivation

def short(name: str) -> str:
    return name.replace("microservice-", "")


def node_id(name: str) -> str:
    return short(name).replace("-", "_")


def load_compose():
    compose = yaml.safe_load((ROOT / "docker-compose.yml").read_text())
    return compose["services"]


def derive_sync_edges(services):
    """(src, dst, label) edges from service-to-service env URLs; datastore edges separately."""
    names = set(services)
    sync, stores, kafka_attached = [], [], set()
    for svc, spec in services.items():
        if svc in OBSERVABILITY:
            continue
        env = spec.get("environment") or {}
        seen_pairs = set()
        for key, val in env.items():
            sval = str(val)
            if key.startswith(("OTEL_", "JAVA_TOOL_OPTIONS")):
                continue  # observability wiring, collapsed out of the container view
            if key == "KAFKA_BOOTSTRAP_SERVERS":
                kafka_attached.add(svc)
                continue
            m = re.search(r"(?:https?|jdbc:postgresql)://([a-z0-9-]+):\d+", sval)
            target = None
            if m and m.group(1) in names:
                target = m.group(1)
            elif key == "QUARKUS_MAILER_HOST" and sval in names:
                target = sval
            elif key == "DB_HOST" and sval in names:
                target = sval
            if not target or target == svc:
                continue
            label = EDGE_LABELS.get(key) or ("OIDC code exchange" if OIDC_KEY.search(key) else key)
            kind = "store" if target.endswith("postgres") or target in ("postgres", "minio") else "sync"
            if kind == "store":
                stores.append((svc, target, label))
            elif (svc, target, label) not in seen_pairs:
                seen_pairs.add((svc, target, label))
                sync.append((svc, target, label))
    # mailpit is a dev stand-in for real SMTP — keep it with the stores/edges it serves
    return sync, stores, sorted(kafka_attached)


def load_pacts():
    contracts = []
    for f in sorted(glob(str(ROOT / "*" / "pacts*" / "*.json"))):
        d = json.loads(Path(f).read_text())
        kind = "message" if "messages" in d else "http"
        descriptions = [m["description"] for m in d.get("messages", [])] + [
            f"{i['request']['method']} {i['request']['path']}" for i in d.get("interactions", [])
        ]
        contracts.append({
            "consumer": d["consumer"]["name"],
            "provider": d["provider"]["name"],
            "kind": kind,
            "descriptions": descriptions,
            "file": str(Path(f).relative_to(ROOT)),
        })
    return contracts


def derive_async_edges(contracts):
    """(producer, topic, consumer, n_shapes, pact) — direction comes from message pacts."""
    edges = {}
    for c in contracts:
        if c["kind"] != "message":
            continue
        producer, consumer = c["provider"], c["consumer"]
        for desc in c["descriptions"]:
            key = (short(producer), topic_for(desc, producer), short(consumer))
            edges.setdefault(key, {"n": 0, "pact": True})["n"] += 1
    for producer, consumer, topic, label in EXTRA_ASYNC:
        edges[(producer, topic, consumer)] = {"n": 1, "pact": False, "label": label}
    return edges


# ---------------------------------------------------------------- rendering

def render(services, sync, stores, kafka_attached, contracts, async_edges):
    lines = []
    w = lines.append
    w("# Architektura — diagramy C4 (generowane)")
    w("")
    w("> **NIE EDYTUJ RĘCZNIE.** Wygenerowane przez `./build_c4.py` z dwóch źródeł prawdy:")
    w("> `docker-compose.yml` (topologia runtime: krawędzie HTTP, magazyny, kto siedzi na Kafce)")
    w("> i commitowanych paktów `*/pacts*/*.json` (kierunek i semantyka zdarzeń, kontrakty HTTP).")
    w("> Zmienił się stack → odpal `python3 build_c4.py` i diagramy nadążą same.")
    w("")

    # ---- C1
    # The owner's verdict (2026-07-11): the workspace ships TWO products, not one — the
    # social portal and the F1 game are SEPARATE beings that only share identity (and this
    # dev compose). The compose edges agree: formula talks to race-sim, security and its
    # own Postgres — never to memes/comments/Kafka. "paddock" is the PORTAL's social hub
    # (servers/people/events), F1-flavoured in name only.
    w("## C1 — kontekst systemu")
    w("")
    w("**Dwa produkty, nie jeden** (werdykt właściciela 2026-07-11): portal społecznościowy"
      " i gra F1 to osobne byty. Dzielą wyłącznie TOŻSAMOŚĆ (wspólne konto + MFA) i ten"
      " dev-compose; gra nie ma ani jednej krawędzi do memów/komentarzy/Kafki, a produkcyjnie"
      " wyjeżdża osobno (hosting/ per liga).")
    w("")
    w("```mermaid")
    w("flowchart LR")
    w('  member(["👤 Użytkownik<br/>(gość / member / moderator / admin)"])')
    w('  subgraph portfolio ["Ten workspace — dwa produkty na wspólnych kontach"]')
    w('    portal["PORTAL społecznościowy:<br/>galeria memów, komentarze, ulubione,<br/>hub paddock (serwery/ludzie/wydarzenia)"]')
    w('    game["GRA F1 (osobny byt):<br/>menedżer + autorytatywna symulacja,<br/>ligi prywatne / serwery grupowe"]')
    w('    idp["Wspólna tożsamość:<br/>konta + MFA<br/>(microservice-security)"]')
    w("  end")
    w('  oauth["Zewnętrzni dostawcy OIDC<br/>(Google/GitHub… — w dev: stub idp)"]')
    w('  smtp["Realny serwer SMTP<br/>(w dev: Mailpit)"]')
    w("  member -->|przeglądarka / PWA| portal")
    w("  member -->|przeglądarka / PWA / apka mobilna| game")
    w("  portal -->|jeden token| idp")
    w("  game -->|jeden token| idp")
    w("  idp -->|logowanie federacyjne| oauth")
    w("  idp -->|maile transakcyjne kont| smtp")
    w("```")
    w("")

    # ---- C2 sync
    w("## C2 — kontenery: krawędzie synchroniczne (HTTP) i magazyny")
    w("")
    w("Krawędzie serwis→serwis wyprowadzone ze zmiennych środowiskowych w `docker-compose.yml`;")
    w("etykieta = intencja wywołania. **Granica produktów jest inwariantem generatora**: jedyne")
    w("krawędzie wychodzące z GRY prowadzą do wspólnej tożsamości — gdyby kiedyś powstała krawędź")
    w("gra↔portal, `build_c4.py` przerwie z błędem zamiast ją narysować. Obserwowalność")
    w("(Prometheus/Grafana/Loki/Promtail/Tempo) celowo zwinięta — widzi wszystkie kontenery,")
    w("na diagramie byłaby spaghetti.")
    w("")
    # the invariant check: a derived edge joining the game directly to the portal means the
    # products got conflated in compose — fail loudly instead of drawing it
    for src, dst, label in sync + stores:
        if {product_of(src), product_of(dst)} == {"game", "portal"}:
            raise SystemExit(
                f"PRODUCT BOUNDARY VIOLATED: {src} -> {dst} ({label}) joins the F1 game "
                f"to the portal — the two products may share identity only (CLAUDE.md)")
    w("```mermaid")
    w("flowchart LR")
    w('  browser(["👤 Przeglądarka"])')
    app_services = [s for s in services
                    if s not in OBSERVABILITY and "postgres" not in s
                    and s not in ("kafka", "mailpit", "minio")]
    store_targets = sorted({t for _, t, _ in stores})
    # stores render INSIDE their product group next to their owner (one big stores cluster
    # drags every database away from its service and the layout degenerates)
    all_nodes = sorted(app_services) + store_targets + ["mailpit"]
    groups = [
        ("portal", "PORTAL społecznościowy"),
        ("identity", "Wspólna TOŻSAMOŚĆ (jedyny łącznik produktów)"),
        ("game", "GRA F1 — osobny produkt"),
    ]
    for gid, gtitle in groups:
        w(f'  subgraph {gid} ["{gtitle}"]')
        for n in all_nodes:
            if product_of(n) != gid:
                continue
            if n in store_targets:
                w(f'    {node_id(n)}[("{n}")]')
            elif n == "mailpit":
                w('    mailpit["mailpit (dev SMTP)"]')
            else:
                flavour = FLAVOURS.get(n, "")
                label = f"{n}<br/><i>{flavour}</i>" if flavour else n
                w(f'    {node_id(n)}["{label}"]')
        w("  end")
    for target, label in BROWSER_EDGES:
        if target in services:
            w(f'  browser -->|"{label}"| {node_id(target)}')
    for src, dst, label in sync:
        w(f'  {node_id(src)} -->|"{label}"| {node_id(dst)}')
    for src, dst, label in stores:
        w(f'  {node_id(src)} --> {node_id(dst)}')
    w("```")
    w("")
    w("Uwagi nie do wyprowadzenia z env-ów (kuratorowane w skrypcie): krawędzie przeglądarki —")
    w("UI wypiekają adresy API w buildzie; `race-sim` nie ma portu na hosta (rozmawia z nim tylko")
    w("formula); collections-ui woła security i user-collections **cross-origin** (CORS).")
    w("Kanały powiadomień (email/sms/push) mieszkają po stronie portalu; tożsamość sięga do nich")
    w("przez granicę (kody MFA, maile kont) — patrz ADR 0005.")
    w("")

    # ---- C2 async
    w("## C2 — kontenery: szyna zdarzeń (Kafka)")
    w("")
    w("Kierunki przepływów pochodzą z **paktów message** (producer = provider paktu) — tego")
    w("`docker-compose.yml` nie wie (env mówi tylko „siedzi na Kafce”). 📜 = krawędź przypięta")
    w("kontraktem, ×N = liczba kształtów wiadomości w pakcie.")
    w("")
    w("```mermaid")
    w("flowchart LR")
    w('  kafka{{"Apache Kafka — event backbone"}}')
    # a service can sit on both sides of the backbone (security produces commands AND consumes
    # confirmations) — one node per side keeps the flow strictly left→right and the labels apart
    producers = sorted({p for (p, _, _) in async_edges})
    consumers = sorted({c for (_, _, c) in async_edges})
    for n in producers:
        w(f'  p_{node_id(n)}["{n}"]')
    for n in consumers:
        w(f'  c_{node_id(n)}["{n}"]')
    produced = set()
    for (producer, topic, consumer), meta in sorted(async_edges.items()):
        mark = "📜" if meta["pact"] else "(bez paktu)"
        label = meta.get("label") or f'×{meta["n"]}'
        if (producer, topic) not in produced:
            produced.add((producer, topic))
            w(f'  p_{node_id(producer)} -.->|"{topic}"| kafka')
        w(f'  kafka -.->|"{topic} {label} {mark}"| c_{node_id(consumer)}')
    w("```")
    w("")
    w("(Serwis może stać po obu stronach szyny — offboarding konsumuje fakt i potwierdzenia,")
    w("a produkuje komendy i werdykty — dlatego występuje po lewej i po prawej.)")
    w("")
    game_on_kafka = [s for s in kafka_attached if product_of(short(s)) == "game"]
    if game_on_kafka:
        raise SystemExit(
            f"PRODUCT BOUNDARY VIOLATED: {game_on_kafka} (the F1 game) attached to Kafka — "
            f"the backbone belongs to the portal+identity (CLAUDE.md)")
    attached = ", ".join(short(s) for s in kafka_attached)
    w(f"Na Kafce siedzą (env `KAFKA_BOOTSTRAP_SERVERS`): {attached}. **Gra F1 nie ma tu ani")
    w("jednej krawędzi** — szyna zdarzeń należy do portalu i tożsamości (osobne produkty;")
    w("generator pilnuje tego twardo).")
    w("")

    # ---- contract coverage table
    w("## Pokrycie kontraktami (Pact)")
    w("")
    w("| Konsument | Producent/Provider | Rodzaj | Interakcje | Plik paktu |")
    w("|---|---|---|---|---|")
    for c in contracts:
        uniq = []
        for d in c["descriptions"]:
            n = c["descriptions"].count(d)
            entry = f"{d} (×{n})" if n > 1 else d
            if entry not in uniq:
                uniq.append(entry)
        descs = "; ".join(uniq)
        w(f"| {short(c['consumer'])} | {short(c['provider'])} | {c['kind']} | {descs} | `{c['file']}` |")
    w("")
    w("Luki widoczne z tej tabeli (stan wygenerowania): paddock (fan-out email/sms/push),")
    w("formula↔race-sim i memes→image-encoder nie mają paktów — krawędzie na diagramach pochodzą")
    w("wtedy wyłącznie z compose; kaskada `MEME_DELETED` jest oznaczona „bez paktu”.")
    w("")
    w("---")
    w("*Wygenerowano skryptem `build_c4.py`. C1 i wpisy „kuratorowane” są zaszyte w skrypcie —*")
    w("*zmieniasz je tam, nie tutaj.*")
    return "\n".join(lines) + "\n"


def main():
    services = load_compose()
    sync, stores, kafka_attached = derive_sync_edges(services)
    contracts = load_pacts()
    async_edges = derive_async_edges(contracts)
    OUT.write_text(render(services, sync, stores, kafka_attached, contracts, async_edges))
    print(f"wrote {OUT.relative_to(ROOT)}: {len(sync)} sync edges, {len(stores)} store edges, "
          f"{len(async_edges)} async edges, {len(contracts)} pacts")


if __name__ == "__main__":
    main()
