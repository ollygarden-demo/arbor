.PHONY: help up down load smoke build lint openapi clean
CLUSTER ?= arbor
CHART   ?= deploy/helm/arbor
RELEASE ?= arbor
NS      ?= arbor

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-12s %s\n", $$1, $$2}'

up: ## Bootstrap kind cluster + install chart
	./scripts/bootstrap.sh

down: ## Tear down kind cluster
	./scripts/teardown.sh

load: ## Run k6 load generator against the gateway
	./scripts/load.sh

smoke: ## Smoke test the running stack
	./scripts/smoke.sh

build: ## Build all service images
	@for d in services/*/; do (cd $$d && ./mvnw -q -DskipTests package); done

lint: ## Lint helm chart
	helm lint $(CHART)

openapi: ## Regenerate committed OpenAPI specs
	./scripts/gen-openapi.sh

clean:
	@for d in services/*/; do (cd $$d && ./mvnw -q clean); done
