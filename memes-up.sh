#!/bin/bash
# The MEMES world, end to end: gallery (:8083) + comments (:8085) with sign-in (security :8080),
# verification mail (email -> Mailpit :8025), social login (stub IdP :8091), object storage
# (MinIO) and the observability pair (Grafana :3000 / Prometheus :9090). Databases, Kafka and
# the image encoder ride in via depends_on. The formula world stays down — see formula-up.sh.
set -euo pipefail
cd "$(dirname "$0")"

./mvnw -q -pl microservice-security/security-infrastructure,microservice-email,microservice-memes/memes-infrastructure,microservice-comments \
    -am package -DskipTests

docker compose up --build -d \
    security email memes comments idp \
    prometheus grafana cadvisor node-exporter "$@"

echo
echo "memes     -> http://localhost:8083    comments -> http://localhost:8085"
echo "security  -> http://localhost:8080    mail inbox (Mailpit) -> http://localhost:8025"
echo "grafana   -> http://localhost:3000    prometheus -> http://localhost:9090"
echo "Full-stack smoke (needs the formula world too): ./infra-smoke.sh"
