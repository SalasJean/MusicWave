#!/usr/bin/env bash
set -euo pipefail

base_url="${MUSICWAVE_API_URL:-http://localhost:8080}"
connections="${SSE_CONNECTIONS:-100}"
hold_seconds="${SSE_HOLD_SECONDS:-18}"
results="$(mktemp -d)"
trap 'rm -rf "$results"' EXIT

for index in $(seq 1 "$connections"); do
  curl --silent --no-buffer --max-time "$hold_seconds" --output /dev/null \
    --write-out '%{http_code}' "$base_url/api/v1/events" > "$results/$index" 2>/dev/null &
done

sleep 2
overflow_code="$(curl --silent --max-time 3 --output /dev/null --write-out '%{http_code}' \
  "$base_url/api/v1/events" 2>/dev/null || true)"
wait || true

accepted="$(grep --files-with-matches '^200$' "$results"/* | wc -l)"
echo "requested=$connections accepted=$accepted overflow_status=$overflow_code"
test "$accepted" -eq "$connections"
test "$overflow_code" = "503"
