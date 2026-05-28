#!/usr/bin/env bash
set -euo pipefail

CLUSTER=arbor
REGISTRY_NAME=arbor-registry
REGISTRY_PORT=5001
NS=arbor

# Create cluster if missing (k3d also creates the registry per cluster.yaml).
if ! k3d cluster list | awk 'NR>1 {print $1}' | grep -qx "${CLUSTER}"; then
  k3d cluster create --config deploy/k3d/cluster.yaml
fi

# Build and push service images to the k3d-managed registry.
for d in services/*/; do
  s=$(basename "$d")
  img="localhost:${REGISTRY_PORT}/ollygarden-demo/${s}:main"
  docker build -t "$img" "$d"
  docker push "$img"
done

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

# Apply the OllyGarden API key secret if the env var is set.
if [ -n "${OLLYGARDEN_API_KEY:-}" ]; then
  kubectl -n "$NS" create secret generic ollygarden-api-key \
    --from-literal=api-key="${OLLYGARDEN_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
fi

helm dependency update deploy/helm/arbor
helm upgrade --install arbor deploy/helm/arbor \
  --namespace "$NS" \
  --set imageRegistry="k3d-${REGISTRY_NAME}:${REGISTRY_PORT}/ollygarden-demo" \
  -f deploy/helm/arbor/values-local.yaml \
  --wait --timeout 5m

echo "✓ arbor is up. See: kubectl -n ${NS} get pods"
