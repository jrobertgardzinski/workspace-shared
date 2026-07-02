#!/bin/bash
# Build the service jars on the host, then start the whole stack (see docker-compose.yml).
set -euo pipefail
cd "$(dirname "$0")"

./mvnw -q -pl microservice-security/security-infrastructure,microservice-email,microservice-memes/memes-infrastructure \
    -am package -DskipTests

docker compose up --build -d "$@"
echo
echo "security  -> http://localhost:8080    email -> http://localhost:8082"
echo "memes     -> http://localhost:8083    mail inbox (Mailpit) -> http://localhost:8025"
echo "Smoke test: ./infra-smoke.sh"
