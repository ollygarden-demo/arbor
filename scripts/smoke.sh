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
kubectl -n "$NS" port-forward svc/arbor-jaeger 16686:16686 >/dev/null &
PFJ=$!
trap "kill $PFG $PFJ 2>/dev/null || true" EXIT
sleep 5

services=(api-gateway customers-service vets-service visits-service notifications-service)
missing=()
for s in "${services[@]}"; do
  count=$(curl -fsS "http://localhost:16686/api/traces?service=${s}&limit=1" | jq '.data | length')
  if [ "$count" -lt 1 ]; then
    missing+=("$s")
  else
    echo "✓ traces found for $s"
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo
  echo "ℹ no OTLP traces yet for: ${missing[*]}"
  echo "  (the baseline ships with Micrometer/Zipkin instrumentation, not OTLP — the"
  echo "   scenario branches add OTel SDK setup / fix propagation / etc.)"
fi
echo "✓ smoke OK (deployments Ready, gateway returns 200, traffic flowed)"
