# Plan wdrożeniowy — zarządzanie kontenerami (spisany 2026-07-07)

Rekomendacja z przeglądu: **Compose na dev, VPS+Compose na pierwszy publiczny serwer,
k3s dopiero na kamieniu milowym „publiczna piramida dywizji"**. Podman odpuszczony.
Poniżej opcje z plusami/minusami i wyzwalaczami przejścia.

## Etap 0 — DZIŚ: docker compose (bez zmian)

Stan: ~20+ kontenerów w jednym pliku, `depends_on` + healthchecki, launchery per świat
(`memes-up.sh` / `formula-up.sh`), `infra-up.sh` jako włącznik wszystkiego, smoke E2E,
observability (Prometheus/Grafana/cAdvisor/node-exporter, 10/10 targetów).

**Plusy:** zero podatku operacyjnego; jeden plik = jedna prawda; szybka pętla dev;
smoke dowodzi całości; skala idealna dla Compose.
**Minusy:** brak rolling deploys, brak sekretów lepszych niż env, jednohostowość.
**Werdykt:** właściwe narzędzie na tę fazę — nie ruszać.

## Podman — ODRZUCONY (na teraz)

**Plusy:** rootless (mniejsza powierzchnia ataku), daemonless, drop-in CLI.
**Minusy (u nas konkretne):** serwery grupowe i sandbox botów jadą po **sokecie Dockera**
(`--bot-docker`, kontenery-rodzeństwo, tłumaczenie ścieżek — kompromis opisany w README
race-sim) — pod rootless wymaga przeróbki; cAdvisor/compose bywają kapryśne; przełączka
kosztuje robotę, nie dając funkcji, których brakuje.
**Wyzwalacz rewizji:** wymóg rootless od hostingu/compliance albo porzucenie soketa
Dockera w sandboxie botów.

## Etap 1 — PIERWSZY PUBLICZNY SERWER: VPS + Compose + systemd + Traefik/Caddy

Kształt: jeden VPS; ten sam `docker-compose.yml` + nakładka prod (porty za reverse proxy,
`GRAFANA_ANON=false` + hasło — env już przygotowane, sekrety w `.env` poza repo); Traefik
albo Caddy z przodu (TLS z Let's Encrypt, routing per subdomena: gra/galeria/grafana);
systemd unit na autostart; backup wolumenów Postgresa (cron + `pg_dump`); ligi = serwery
grupowe przez istniejący `provision.sh` na kolejnych VPS-ach.

**Plusy:** jeden wieczór roboty; zero nowych technologii do nauki; awaria = jeden host
do debugowania; koszt stały minimalny (jeden VPS); istniejący model prowizjonowania lig
działa bez zmian.
**Minusy:** deploy = restart (krótka przerwa); brak samoleczenia poza `restart: always`;
skalowanie = ręczne dokładanie VPS-ów; sekrety nadal w env.
**Wyzwalacz wejścia:** decyzja o pokazaniu gry światu (domena + VPS).
**Do przygotowania wtedy:** katalog `deploy/` (compose.prod.yml + traefik + systemd unit
+ skrypt backupu).

## Etap 2 — TRAKCJA / PIRAMIDA DYWIZJI: k3s (lekki Kubernetes)

Wyzwalacz: publiczna piramida + rejestr→provision (backlog paddocka) — moment, w którym
„liga" staje się jednostką WDROŻENIA (namespace/Deployment per liga), a `provision.sh`
wymienia się na wywołanie API klastra. k3s: pojedynczy binarek, działa na jednym VPS-ie
i rośnie do wielu node'ów, manifesty = zwykły k8s.

**Plusy:** rolling deploys bez przerw; samoleczenie i limity zasobów per liga; Ingress
zamiast ręcznego Traefika; sekrety jako obiekty; observability podmienialne na
kube-prometheus-stack; **wartość portfolio** (katalog `deploy/k8s/` z manifestami/Helmem);
obrazy wchodzą bez zmian.
**Minusy:** realny podatek nauki i operacji (etcd/certyfikaty/upgrade'y — w k3s mniejszy,
ale niezerowy); debugowanie trudniejsze niż `docker compose logs`; sandbox botów po
sokecie wymaga przemyślenia (Kaniko/DinD/gVisor — osobna decyzja); dla solo-deva to czas
zabrany treści gry, więc wchodzić DOPIERO przy realnej potrzebie.

## Pełny k8s (EKS/GKE/samodzielny) — ODRZUCONY do odwołania

**Plusy:** standard branżowy, nieograniczona skala.
**Minusy:** koszt (control plane / własna operacja), złożoność nieproporcjonalna do
jednoosobowego projektu; wszystko, co daje, k3s daje taniej na naszej skali.
**Wyzwalacz rewizji:** zespół > 1 osoby albo skala wielu regionów.

## Sekwencja decyzji (TL;DR)

1. Dziś: nic nie zmieniać (Compose).
2. Publikacja: VPS + Compose + systemd + Traefik → katalog `deploy/`.
3. Piramida: k3s, liga jako jednostka wdrożenia; manifesty do portfolio.
