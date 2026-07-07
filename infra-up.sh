#!/bin/bash
# Build the service jars on the host, then start the whole stack (see docker-compose.yml).
set -euo pipefail
cd "$(dirname "$0")"

# the OpenTelemetry Java agent (traces -> Tempo) is attached to the JVM services via a mounted
# volume, not baked into the images — fetch it once (gitignored, ~21 MB)
OTEL_AGENT=observability/otel/opentelemetry-javaagent.jar
if [ ! -f "$OTEL_AGENT" ]; then
    echo "fetching the OpenTelemetry Java agent..."
    mkdir -p observability/otel
    curl -sfL -o "$OTEL_AGENT" \
        "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.29.0/opentelemetry-javaagent.jar" \
        || { echo "WARNING: could not fetch the OTel agent; JVM services will start without tracing"; rm -f "$OTEL_AGENT"; }
fi

# every JVM service in the compose file needs its jar built on the host (Dockerfiles are
# runtime-only); sms/push/image/idp are Python and need nothing
./mvnw -q -pl microservice-security/security-infrastructure,microservice-email,microservice-memes/memes-infrastructure,microservice-comments,microservice-paddock \
    -am package -DskipTests

docker compose up --build -d "$@"
echo
echo "security  -> http://localhost:8080    email -> http://localhost:8082"
echo "memes     -> http://localhost:8083    mail inbox (Mailpit) -> http://localhost:8025"
echo "grafana   -> http://localhost:3000    prometheus -> http://localhost:9090"
echo "Smoke test: ./infra-smoke.sh"
