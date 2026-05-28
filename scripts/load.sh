#!/usr/bin/env bash
set -euo pipefail
# Port-forward if not already exposed
if ! curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then
  kubectl -n arbor port-forward svc/api-gateway 8080:8080 &
  PF=$!
  trap "kill $PF" EXIT
  sleep 5
fi
k6 run scripts/load.js
