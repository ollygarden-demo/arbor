#!/usr/bin/env bash
set -euo pipefail

# Generates OpenAPI yaml from each running service.
# Requires the stack to be up: `make up`.

declare -A SVC=(
  [customers]=customers-service:8081
  [vets]=vets-service:8083
  [visits]=visits-service:8082
)

mkdir -p api/openapi

for name in "${!SVC[@]}"; do
  host="${SVC[$name]}"
  echo "→ ${name} (${host})"
  kubectl -n arbor port-forward "svc/${host%%:*}" "${host##*:}:${host##*:}" >/dev/null &
  PF=$!
  trap "kill $PF 2>/dev/null || true" EXIT
  sleep 3
  curl -fsS "http://localhost:${host##*:}/v3/api-docs.yaml" \
    > "api/openapi/${name}.yaml"
  kill $PF
  trap - EXIT
done
echo "✓ wrote api/openapi/*.yaml"
