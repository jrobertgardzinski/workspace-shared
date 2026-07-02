#!/bin/bash
# Stop the stack. Add -v to also drop the Postgres volume (fresh database next time).
set -euo pipefail
cd "$(dirname "$0")"
docker compose down "$@"
