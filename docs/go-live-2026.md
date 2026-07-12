# Portal go-live — readiness assessment and publication plan (written 2026-07-12)

Goal: make the portal (meme gallery + security + companions) public as a portfolio
showcase for the recruitment campaign of September–October 2026. Complements
[deployment-plan.md](deployment-plan.md) — this is the concretisation of its Stage 1.

**Target market (owner's verdict, 2026-07-12): remote positions ABROAD.** That verdict
reshapes the plan — see "The plan for the remote-abroad target" below. An always-on
public deployment is demoted to an optional variant; the effort goes into what that
funnel actually rewards.

## Readiness verdict

**Functionally ready, operationally almost** — what's missing is exactly the Stage 1
layer (the `deploy/` directory does not exist yet). That is a weekend of work, not
a month. The biggest clash with the "cheap VPS" assumption: the full stack needs
10–14 GB of RAM.

## What is already in place (the strong side)

Product-wise the memes service is in a state many "commercial" MVPs are not:

- registration gated on e-mail verification; per-IP registration throttle;
  anti-enumeration,
- upload rate limit (12/min per user, env `MEMES_UPLOAD_RATE_LIMIT`),
- RBAC + moderation + NSFW flag with gallery blur; EXIF stripped from uploads; dedup,
- the account-deletion saga (a practically ready "right to be forgotten"),
- Postgres + MinIO, healthchecks, E2E smoke, CI, full observability (3 signals).

This is not a demo that falls over at the first bot.

## Pre-publication checklist (Stage 1 made concrete)

1. **Real SMTP — blocker #1, not cosmetics.** Sign-in is gated on e-mail verification
   and mail currently goes to Mailpit — without real delivery nobody can sign in.
   `microservice-email` has a prod profile with STARTTLS; what remains is wiring
   a free tier of Resend/Brevo/Mailgun (or SES) through env.
2. **Secrets and URLs.** Compose carries defaults like `secret`/`supersecret`/
   `local-dev-key` and `localhost` baked into `VERIFY_LINK_BASE`, `RESET_LINK_BASE`,
   `SECURITY_OAUTH_ALLOWED_RETURN_PREFIXES`, CORS. Everything already sits in env
   vars, so the fix is a `compose.prod.yml` overlay + a `.env` outside the repo.
3. **OAuth.** The stub IdP **must not be public** (it signs id_tokens with
   a demo secret — that is a sign-in bypass). Either real Google/GitHub client-ids per
   `microservice-security/docs/oauth-providers.md`, or disable the providers
   (the buttons are dynamic from `GET /oauth/providers`, so they disappear on their own).
4. **TLS + reverse proxy + systemd + backups.** Traefik/Caddy with Let's Encrypt,
   a unit for autostart, a `pg_dump` cron — exactly what the plan budgets as
   "one evening".
5. **Grafana.** `GRAFANA_ANON=false` + a password (env already prepared), or simply
   don't expose it through the proxy at all.
6. **Portal only.** Per the "two products" verdict: a public deployment ships without
   `formula`/`race-sim` (the engine carries secrets, the repo is private).
   `memes-up.sh` already defines that subset; the prod overlay should pin it down.
7. **Registration throttle back to its default** (compose raises it to 100/15min for
   the smoke test). Public image upload = UGC, and the only moderator is the owner —
   start in "registration open but watched" mode; the mechanisms (NSFW, moderator
   deletion) are already there. Plus a one-paragraph privacy policy, since real e-mail
   addresses are collected (GDPR).

## Would a "cheap VPS" be enough?

Not for the full stack. The portal alone is ~8 JVMs + Kafka + 6×Postgres + MinIO +
observability — realistically **10–14 GB of RAM**.

- **Hetzner CAX31 (ARM, 8 vCPU / 16 GB) ~14 €/mo → ~43 € / 3 months** — the favourite.
  Own images are built from source on the machine; temurin/postgres/kafka have ARM
  variants. The safer x86 counterpart: CX42 (~17 €/mo).
- A classic 4 GB "cheap VPS" only works after amputation: no observability
  (Prometheus/Grafana/Loki/Tempo/cAdvisor are a large share of the weight) and
  a trimmed portal — but that loses the best "wow" (Grafana with saga traces is
  excellent interview material).

## Cost estimate for 3 months (rough, as of 2026-07)

| Item | Cost | Notes |
|---|---|---|
| Hetzner CAX31 VPS (8 vCPU ARM / 16 GB) | ~14 €/mo → **~43 € / 3 mo** | full stack incl. observability; hourly billing — you pay for actual uptime |
| Domain | ~3–5 €/yr (.pl on promo) or ~12 €/yr (.com/.dev) | one-off, survives the VPS |
| TLS | 0 | Let's Encrypt via Traefik/Caddy |
| SMTP (Brevo/Resend free tier) | 0 | limits like 300 mails/day — plenty for a demo |
| Hetzner snapshots/backup | ~1–2 €/mo | optional, worth it |

**Total: ~50–60 €** ("one pizza a month").

Variants:

- **Baseline (recommended if hosting at all): CAX31, ~50–60 € total** — as above.
- **Frugal (~25–35 €):** CAX21 (4 vCPU / 8 GB, ~7 €/mo) — needs a trimmed stack
  (e.g. no Tempo/Loki or no paddock); risk of JVMs suffocating.
- **Zero cost: Oracle Cloud Always Free** (4 OCPU / 24 GB ARM) — carries the whole
  stack, but with the reclaim/account-termination risk (details below in the options).
  A sensible order: experiment on Oracle first — if it comes up and survives a week,
  no VPS is needed at all. Mandatory hygiene: backups OUTSIDE Oracle (`pg_dump` cron
  to home) + minimal traffic/health-ping so the account never looks dead
  (idle = reclaim candidate).
- **Safe x86 (+~3 €/mo):** Hetzner CX42 (8 vCPU x86 / 16 GB, ~17 €/mo) — should ARM
  ever cause trouble.

A note on ARM (CAX/Oracle run Ampere CPUs, arm64): the JVM doesn't care (Temurin
JDK 25 has native arm64 builds), own services build from source on the machine, and
the off-the-shelf compose images (postgres, kafka, minio, grafana, prometheus,
mailpit) are multi-arch — `docker compose pull` picks the right variant by itself.
The only real trap: a niche image or native library without an arm64 variant (no
candidates in the current compose; Pillow in the image-encoder has arm64 wheels).
The x86 variant above exists for that eventuality.

## Hosting options (most sensible first)

1. **Oracle Cloud Always Free** — 4 OCPU / 24 GB ARM for free, indefinitely; the only
   free hosting that carries the whole stack. Risk: capricious ARM capacity and known
   cases of idle-account termination — treat it as a bonus, not a foundation.
2. **Demo on demand** — an hourly-billed VPS (Hetzner bills hourly): a prepared
   `deploy/` + snapshot, the stack comes up in ~10 minutes before an interview and
   disappears after. Near-zero cost, zero moderation risk. A great complement, weak as
   a CV link (a link must be alive when a recruiter clicks it on a Tuesday at 10 pm).
3. **A tunnel from the home machine** (cloudflared / Tailscale Funnel) — free
   TLS+domain, but availability = the home computer's uptime. Fine as a transition.
4. **Static evidence as the base layer regardless of hosting**: the public repos
   already exist; add a 2–3 minute video walking one flow (registration → mail →
   upload → account-deletion saga as ONE trace in Tempo) + the glossary/Allure on
   GitHub Pages. Most recruiters never click a live demo anyway, and a video outlives
   the 3 months of hosting.

## The plan for the remote-abroad target (owner's verdict 2026-07-12)

How hiring for remote-abroad actually works: the first filter is the CV/LinkedIn
(often an ATS and a non-technical recruiter), then screening, live coding / system
design, team interviews. In that funnel **a live URL barely works** — a foreign
recruiter might click GitHub, a live demo almost never. What converts, in order of ROI:

0. **Phase 0 — owner onboarding (unchanged, the foundation):** the path =
   [onboarding-guide.md](onboarding-guide.md) (section by section + Annex A with
   interview questions), plus practice: `infra-up.sh` → `infra-smoke.sh`, clicking
   through the gallery, Grafana (dashboards, the saga trace in Tempo), the UL glossary
   and Allure. Exit test: you can narrate the flow registration → mail → upload →
   account deletion without notes. A system-design interview is de facto narrating
   your own stack.
1. **The English-language public layer** — code and Javadoc were already English; the
   workspace docs were Polish. **Done 2026-07-12:** the onboarding guide, this plan,
   the deployment plan and the generated C4 diagrams are English; per-repo READMEs
   must carry the whole story for a foreign reader.
2. **A 2–3 minute video linked from the README** — gets more views than a live site,
   works at 3 a.m. in another timezone, needs no UGC moderation. Record it only after
   Phase 0 — recording it is itself a fine comprehension test.
3. **The `deploy/` directory as an artifact** (compose.prod.yml + Traefik + systemd
   unit + backup script — Stage 1 of deployment-plan.md): its mere existence is
   interview material ("how would you ship it"), even if it never reaches production.
   Independent of Phase 0 — can be built early and wait on the shelf.
4. **Demo on demand instead of always-on hosting** — interviews abroad are scheduled
   in advance, so a snapshot + an hourly VPS on interview day ("want to click through
   it together? here's one trace across five services") beats a footer link in the CV,
   at a fraction of the cost and risk.

**What is cut for this target:** the standing 3-month deployment. A public meme
gallery without a community is an empty gallery (worse-looking than a seeded demo),
and it takes on UGC moderation and GDPR for something nobody in the funnel will look
at. Always-on hosting defends itself mainly for a Polish/local target — where a CV
link circulates in a smaller market and someone actually clicks — or simply for the
joy of owning a live system, which is a legitimate reason, just not a recruitment one.

## Timeline

The overriding rule (owner, 2026-07-12): **first know the system, then publish**.
Phases are triggered by readiness, not dates — the only hard anchor is the IX–X 2026
campaign:

- **From ~mid-July:** Phase 0 (owner onboarding).
- **Any time:** `deploy/` directory (independent of Phase 0).
- **After Phase 0:** the video; English READMEs polished.
- **If always-on hosting is wanted after all** (local target / own joy): the natural
  window is early September — 3 months (IX–XI) then cover the WHOLE campaign; costed
  above (~50–60 €).
