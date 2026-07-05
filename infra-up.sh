#!/bin/bash
# Build the service jars on the host, then start the whole stack (see docker-compose.yml).
set -euo pipefail
cd "$(dirname "$0")"

# every JVM service in the compose file needs its jar built on the host (Dockerfiles are
# runtime-only); sms/push/image/idp are Python and need nothing
./mvnw -q -pl microservice-security/security-infrastructure,microservice-email,microservice-memes/memes-infrastructure,microservice-comments,microservice-paddock \
    -am package -DskipTests

docker compose up --build -d "$@"
echo
echo "security  -> http://localhost:8080    email -> http://localhost:8082"
echo "memes     -> http://localhost:8083    mail inbox (Mailpit) -> http://localhost:8025"
echo "Smoke test: ./infra-smoke.sh"
