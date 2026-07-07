#!/bin/bash
# The FORMULA world, end to end: the manager game (:8084, race control UI at /), the Python race
# module (:8090 — also the manual drive at /school/drive-ui), durable game state (its Postgres),
# plus what signing in truly needs: security (:8080), the verification mail chain (email ->
# Mailpit :8025), the stub IdP (:8091) and the observability pair (Grafana :3000 / Prometheus
# :9090). Databases and Kafka ride in via depends_on. The memes world stays down — memes-up.sh.
set -euo pipefail
cd "$(dirname "$0")"

./mvnw -q -pl microservice-security/security-infrastructure,microservice-email,formula-simulator \
    -am package -DskipTests

docker compose up --build -d \
    formula race-sim security email idp \
    prometheus grafana cadvisor node-exporter "$@"

echo
echo "formula   -> http://localhost:8084    race-sim/manual drive -> http://localhost:8090/school/drive-ui"
echo "security  -> http://localhost:8080    mail inbox (Mailpit) -> http://localhost:8025"
echo "grafana   -> http://localhost:3000    prometheus -> http://localhost:9090"
echo "Full-stack smoke (needs the memes world too): ./infra-smoke.sh"
