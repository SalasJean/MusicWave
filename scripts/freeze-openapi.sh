#!/usr/bin/env bash
set -euo pipefail

base_url="${MUSICWAVE_API_URL:-http://localhost:8080}"
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
target="$project_root/docs/openapi/musicwave-api-v1.json"
temporary="$(mktemp)"
trap 'rm -f "$temporary"' EXIT

curl --fail --silent --show-error "$base_url/v3/api-docs" | jq --sort-keys . > "$temporary"
mv "$temporary" "$target"
trap - EXIT
echo "Frozen OpenAPI contract: $target"
