#!/usr/bin/env bash
set -euo pipefail
kind delete cluster --name arbor || true
docker rm -f kind-registry 2>/dev/null || true
