#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_PORT=8081
PIDS=()

cleanup() {
  echo ""
  echo "Zatrzymuję serwery..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  exit 0
}
trap cleanup SIGINT SIGTERM

mapfile -t RESULTS_DIRS < <(find "$SCRIPT_DIR" -type d -name "allure-results" | sort)

if [ ${#RESULTS_DIRS[@]} -eq 0 ]; then
  echo "Nie znaleziono żadnych katalogów allure-results."
  exit 1
fi

echo "Generuję raporty Allure..."
echo ""

port=$BASE_PORT
for results_dir in "${RESULTS_DIRS[@]}"; do
  module=$(echo "$results_dir" | sed "s|$SCRIPT_DIR/||" | sed "s|/target/allure-results||" | sed "s|/allure-results||")
  report_dir="$(dirname "$results_dir")/allure-report"

  allure generate --clean -o "$report_dir" "$results_dir" --single-file 2>/dev/null

  python3 -m http.server "$port" --directory "$report_dir" &>/dev/null &
  pid=$!
  PIDS+=("$pid")

  echo "  http://localhost:$port  →  $module"
  ((port++))
done

echo ""
echo "Ctrl+C aby zatrzymać wszystkie serwery."
wait
