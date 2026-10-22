#!/usr/bin/env bash
set -euo pipefail

base_url="${MUSICWAVE_API_URL:-http://localhost:8080}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
frozen="$project_root/docs/openapi/musicwave-api-v1.json"
expected="$project_root/docs/openapi/expected-operations.txt"
live="$(mktemp)"
actual_operations="$(mktemp)"
trap 'rm -f "$live" "$actual_operations"' EXIT

curl --fail --silent --show-error "$base_url/v3/api-docs" | jq --sort-keys . > "$live"
diff --unified "$frozen" "$live"
jq --raw-output '.paths | to_entries[] as $path | $path.value | keys[] | select(. != "parameters") | ascii_upcase + " " + $path.key' "$live" \
  | sort > "$actual_operations"
diff --unified "$expected" "$actual_operations"
echo "OpenAPI snapshot and stable operation set match."
