#!/usr/bin/env bash
set -euo pipefail

CLUSTER=arbor
REGISTRY_PORT=5001
REGISTRY_NAME=kind-registry
NS=arbor

# Start a local registry so kind nodes can pull our locally-built images.
if [ -z "$(docker ps -q -f name=^${REGISTRY_NAME}$)" ]; then
  docker run -d --restart=always -p "127.0.0.1:${REGISTRY_PORT}:5000" \
    --name "${REGISTRY_NAME}" registry:2
fi

# Create cluster if missing.
if ! kind get clusters | grep -qx "${CLUSTER}"; then
  kind create cluster --config deploy/kind/cluster.yaml
  docker network connect kind "${REGISTRY_NAME}" || true
fi

# Build and push images to the local registry.
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
  --set imageRegistry="localhost:${REGISTRY_PORT}/ollygarden-demo" \
  -f deploy/helm/arbor/values-local.yaml \
  --wait --timeout 5m

echo "✓ arbor is up. See: kubectl -n ${NS} get pods"
