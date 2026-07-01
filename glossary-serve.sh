#!/usr/bin/env bash
set -euo pipefail

# Regenerate the UL glossary and serve the interactive HTML site locally.
# Usage: ./glossary-serve.sh [port]   (default 8090)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SITE_DIR="$SCRIPT_DIR/docs/glossary/site"
PORT="${1:-8090}"

echo "Generuję glosariusz UL..."
python3 "$SCRIPT_DIR/build_glossary.py"

if [ ! -f "$SITE_DIR/index.html" ]; then
  echo "Brak $SITE_DIR/index.html."
  exit 1
fi

echo ""
echo "Glosariusz UL: http://localhost:$PORT"
echo "Ctrl+C aby zatrzymać."
python3 -m http.server "$PORT" --directory "$SITE_DIR"
