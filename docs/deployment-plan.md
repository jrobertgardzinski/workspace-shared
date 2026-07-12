# Deployment plan — container management (written 2026-07-07)

Recommendation from the review: **Compose for dev, VPS+Compose for the first public
server, k3s only at the "public division pyramid" milestone**. Podman passed over.
Below: the options with pros/cons and the triggers for moving between stages.

## Stage 0 — TODAY: docker compose (no changes)

State: ~20+ containers in one file, `depends_on` + healthchecks, per-world launchers
(`memes-up.sh` / `formula-up.sh`), `infra-up.sh` as the everything-switch, E2E smoke,
observability (Prometheus/Grafana/cAdvisor/node-exporter, 10/10 targets).

**Pros:** zero operational tax; one file = one truth; fast dev loop; the smoke test
proves the whole; the scale is ideal for Compose.
**Cons:** no rolling deploys, no secrets better than env, single-host.
**Verdict:** the right tool for this phase — don't touch it.

## Podman — REJECTED (for now)

**Pros:** rootless (smaller attack surface), daemonless, drop-in CLI.
**Cons (concrete for us):** the group servers and the bot sandbox ride the **Docker
socket** (`--bot-docker`, sibling containers, path translation — the trade-off is
described in the race-sim README) — under rootless that needs rework; cAdvisor/compose
can be temperamental; the switch costs work while adding no missing feature.
**Revision trigger:** a rootless requirement from hosting/compliance, or dropping the
Docker socket in the bot sandbox.

## Stage 1 — FIRST PUBLIC SERVER: VPS + Compose + systemd + Traefik/Caddy

Shape: one VPS; the same `docker-compose.yml` + a prod overlay (ports behind a reverse
proxy, `GRAFANA_ANON=false` + password — env already prepared, secrets in `.env`
outside the repo); Traefik or Caddy in front (TLS via Let's Encrypt, routing per
subdomain: game/gallery/grafana); a systemd unit for autostart; Postgres volume
backups (cron + `pg_dump`); leagues = group servers via the existing `provision.sh`
on further VPSes.

**Pros:** one evening of work; zero new technologies to learn; an outage = one host to
debug; minimal fixed cost (one VPS); the existing league-provisioning model works
unchanged.
**Cons:** deploy = restart (short downtime); no self-healing beyond `restart: always`;
scaling = adding VPSes by hand; secrets still in env.
**Entry trigger:** the decision to show the game to the world (domain + VPS).
**To prepare then:** a `deploy/` directory (compose.prod.yml + traefik + systemd unit
+ backup script).

## Stage 2 — TRACTION / THE DIVISION PYRAMID: k3s (lightweight Kubernetes)

Trigger: a public pyramid + registry→provision (the paddock backlog) — the moment
a "league" becomes the unit of DEPLOYMENT (namespace/Deployment per league) and
`provision.sh` is replaced by a cluster API call. k3s: a single binary, runs on one
VPS and grows to many nodes, manifests = plain k8s.

**Pros:** rolling deploys without downtime; self-healing and per-league resource
limits; Ingress instead of hand-rolled Traefik; secrets as objects; observability
swappable for kube-prometheus-stack; **portfolio value** (a `deploy/k8s/` directory
with manifests/Helm); the images go in unchanged.
**Cons:** a real learning and operations tax (etcd/certificates/upgrades — smaller in
k3s, but non-zero); debugging harder than `docker compose logs`; the socket-based bot
sandbox needs rethinking (Kaniko/DinD/gVisor — a separate decision); for a solo dev
this is time taken from game content, so enter ONLY on real need.

## Full k8s (EKS/GKE/self-managed) — REJECTED until further notice

**Pros:** the industry standard, unbounded scale.
**Cons:** cost (control plane / self-operation), complexity disproportionate to
a one-person project; everything it offers, k3s offers cheaper at our scale.
**Revision trigger:** a team > 1 person or multi-region scale.

## Decision sequence (TL;DR)

1. Today: change nothing (Compose).
2. Publication: VPS + Compose + systemd + Traefik → the `deploy/` directory.
3. The pyramid: k3s, a league as the deployment unit; manifests into the portfolio.
