#!/usr/bin/env bash
set -euo pipefail
NS=arbor

echo "→ waiting for all Deployments Ready"
kubectl -n "$NS" wait deploy --all --for=condition=Available --timeout=300s

echo "→ generating a burst of traffic"
kubectl -n "$NS" port-forward svc/api-gateway 8080:8080 >/dev/null &
PFG=$!
trap "kill $PFG 2>/dev/null || true" EXIT
sleep 5
k6 run --vus 2 --duration 10s scripts/load.js

echo "→ querying Jaeger for traces"
kubectl -n "$NS" port-forward svc/jaeger 16686:16686 >/dev/null &
PFJ=$!
trap "kill $PFG $PFJ 2>/dev/null || true" EXIT
sleep 5

services=(api-gateway customers-service vets-service visits-service notifications-service)
for s in "${services[@]}"; do
  count=$(curl -fsS "http://localhost:16686/api/traces?service=${s}&limit=1" | jq '.data | length')
  if [ "$count" -lt 1 ]; then
    echo "✗ no traces for $s"; exit 1
  fi
  echo "✓ traces found for $s"
done
echo "✓ smoke OK"
