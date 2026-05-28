# Arbor — Rose Demo Mono Repo Design

**Status:** Draft for review
**Date:** 2026-05-28
**Owner:** Juraci Paixão Kröhling
**Repo:** `ollygarden-demo/arbor`

The repo is named **arbor** — a garden structure that supports climbing roses — fitting OllyGarden's plant-themed naming and signalling that the project exists to showcase Rose.

## 1. Goal & Narrative

`arbor` is a mono repo hosting a forked **Spring PetClinic Microservices** application, augmented to match a realistic enterprise Spring Boot architecture (Envoy sidecar, Kafka, OpenAPI), and structured so each Rose capability can be demonstrated as a single `git checkout <branch> && rose run` flow.

The repo has two faces:

- **`main`** — a "realistic enterprise Spring Boot app." Partially instrumented via Micrometer Tracing → OTel bridge (as petclinic ships), but carrying deliberate weaknesses Rose can act on. Runs cleanly on its own and is demoable as the baseline.
- **`scenario/*` branches** — each branch isolates one Rose capability against `main`. The diff between branch and `main` is the "bad state" Rose will fix.

Demo story: *"Here's a typical Spring Boot microservices system. Watch Rose review it, then watch Rose fix it."*

## 2. Architecture (on `main`)

```
                  ┌─────────────┐
                  │   Client    │  (curl / Postman / k6 load script)
                  └──────┬──────┘
                         │ HTTPS, OpenAPI-described
                  ┌──────▼──────┐
                  │ api-gateway │  (Spring Cloud Gateway)
                  └──────┬──────┘
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
┌──────▼──────┐   ┌──────▼──────┐   ┌──────▼──────┐
│  customers  │   │    vets     │   │   visits    │   ← Spring Boot
│  -service   │   │  -service   │   │  -service   │     + springdoc OpenAPI
└──────┬──────┘   └─────────────┘   └──────┬──────┘     + Resilience4j HTTP
       │                                   │             + Micrometer/OTel
       │                                   │ Kafka
       │                                   │ "visit.created"
       │                                   ▼
       │                          ┌──────────────────┐
       │                          │  notifications-  │  ← NEW service
       │                          │  service         │    (purpose-built)
       │                          └──────────────────┘
       │
       └──► discovery-server (Eureka) ◄──── all services register
            config-server (Spring Cloud Config) ◄── all services pull config

Each pod: app container + Envoy sidecar (egress proxy → service-to-service)

Cluster infra:
- OTel Collector (DaemonSet) → forwards to OllyGarden cloud
- Prometheus (scrapes /actuator/prometheus and Envoy stats)
- Kafka (Strimzi or Bitnami chart)
- Eureka, Config server
```

**Key choices:**

- **Base:** Fork of `spring-petclinic/spring-petclinic-microservices` (Apache-2.0). Source vendored into `services/` and evolved in-tree; we do not track upstream.
- **Discovery:** Eureka (petclinic default).
- **Service mesh:** Envoy as a sidecar via a small Helm pattern. Not full Istio — keeps the demo lightweight while still showing "sidecar telemetry merged with app telemetry."
- **Kafka:** A new `notifications-service` consumes a `visit.created` event published by `visits-service`. Provides an async hop for chain and baggage demos.
- **OpenAPI:** springdoc-openapi on every Spring Boot service; specs published at `/v3/api-docs` and committed to `api/openapi/*.yaml` for tooling.
- **Runtime:** kind/k3d cluster + Helm umbrella chart in `deploy/helm/`. `make up` brings the stack online and points OTLP at the OllyGarden cloud.

## 3. Scenarios

All branches fork from `main`. The branch name encodes intent; the diff vs `main` is the "before" state Rose will improve.

| Branch | "Before" state on the branch | What Rose should do |
|---|---|---|
| `scenario/add-otel-from-scratch` | Strip OTel/Micrometer wiring from one service (`customers-service`): no SDK setup, no auto-instrumentation agent, no spans. Other services keep their instrumentation so the chain has a "dark" hop. | Add SDK init, wire OTLP exporter, instrument controllers/clients, restore propagation. |
| `scenario/fix-baggage-propagation` | In `visits-service` Kafka producer, baggage is not propagated onto outgoing Kafka headers. Downstream `notifications-service` loses tenant/user baggage. | Detect the dropped baggage at the Kafka boundary; add a producer interceptor (or fix the existing one) to inject W3C baggage headers. |
| `scenario/fix-semconv-violations` | Custom attribute names sprinkled in: `http_status`, `db_query`, `user_email`, span name `"GET-customer"`. Mixed old/new conventions. | Migrate to current OTel semantic conventions (`http.response.status_code`, `db.query.text`, proper span naming). |
| `scenario/fix-high-cardinality` | A Micrometer counter labeled with `customer.id` and full URL `path` (raw, including IDs). A histogram tagged with `user.email`. | Identify high-cardinality labels, refactor to bucketed/templated values, remove PII-ish labels. |
| `scenario/detect-pii` | Email address logged as a span attribute (`user.email=...`), full request body recorded as event, IBAN/credit-card-shaped string in a log attribute. Crosses HTTP + Kafka so PII propagates through the chain. | Detect PII in attributes/events/logs, redact or remove, propose a PII-safe attribute schema. |

**Design constraints across scenarios:**

- Each branch is minimal — only the changes needed to create the "bad state." Reviewers can `git diff main..scenario/X` to see exactly what's wrong.
- Each branch ships a `SCENARIO.md` at the repo root explaining the "before," the expected Rose action, and a verification checklist.
- A `k6` / curl-based load script in `scripts/load.sh` generates traffic so Rose has real telemetry to observe.

## 4. Repo Layout

```
arbor/
├── README.md                       # demo intro, quickstart, scenario index
├── Makefile                        # make up / down / load / scenario-<name>
├── services/
│   ├── api-gateway/                # forked petclinic, +springdoc
│   ├── customers-service/          # forked petclinic, +springdoc, +OpenAPI yaml
│   ├── vets-service/               # forked petclinic, +springdoc
│   ├── visits-service/             # forked petclinic, +springdoc, +Kafka producer
│   ├── notifications-service/      # NEW: Kafka consumer, Spring Boot
│   ├── discovery-server/           # Eureka (petclinic)
│   └── config-server/              # Spring Cloud Config (petclinic)
├── api/openapi/                    # generated specs, committed for tooling
│   ├── customers.yaml
│   ├── vets.yaml
│   └── visits.yaml
├── deploy/
│   ├── helm/arbor/                 # umbrella chart (published to GHCR OCI)
│   │   ├── Chart.yaml
│   │   ├── values.yaml             # OTLP endpoint, OllyGarden API key ref
│   │   └── templates/              # one Deployment per service w/ Envoy sidecar
│   ├── envoy/                      # sidecar config templates
│   └── kind/                       # kind cluster config, registry, bootstrap
├── observability/
│   ├── otel-collector/             # Collector config (OTLP → OllyGarden + local Jaeger)
│   ├── prometheus/                 # scrape config
│   ├── jaeger/                     # offline-demo trace backend
│   ├── grafana/                    # offline-demo dashboards (Prom + Jaeger sources)
│   └── kafka/                      # Strimzi / Bitnami values
├── scripts/
│   ├── load.sh                     # k6 traffic generator
│   ├── bootstrap.sh                # one-shot: create cluster, install charts
│   └── teardown.sh
├── docs/
│   ├── architecture.md
│   ├── scenarios/                  # one md per scenario (overview, demo script)
│   └── superpowers/specs/          # design docs (this file)
└── .github/workflows/              # CI: build images, lint Helm, smoke test main
```

**Branch convention:**

- `main` — clean baseline, demoable on its own.
- `scenario/<slug>` — one branch per Rose capability. Each branch adds a `SCENARIO.md` at the repo root in addition to the per-scenario doc on `main` under `docs/scenarios/`.

**Image strategy:** each service has a Dockerfile; CI publishes `:main` and `:scenario-<slug>` tags. Helm chart picks tag via `values.yaml`, so switching scenarios is `helm upgrade --set imageTag=scenario-fix-baggage`.

**Chart distribution:** the umbrella chart is published as an **OCI artifact to GHCR** (`oci://ghcr.io/ollygarden-demo/charts/arbor`), so a demo machine can `helm install` without cloning the repo.

**`notifications-service` shape:** Kafka-only (consumer + minimal `/actuator/health` for k8s probes). It does not expose a business HTTP API and so does not appear in OpenAPI tooling — this keeps the async-chain story uncluttered.

## 5. Data Flow & Telemetry

**Synchronous request path (HTTP):**

```
client → api-gateway (Envoy sidecar)
       → customers-service (Envoy sidecar) ──► registers a customer
       → visits-service   (Envoy sidecar) ──► creates a visit
            │
            ├─ HTTP call back to customers-service (validates customer exists)
            └─ Publishes "visit.created" to Kafka
                  │
                  └─► notifications-service consumes, "sends" notification (logs it)
```

Every hop carries W3C `traceparent` + `baggage` headers. On `main`, baggage carries `tenant.id` and `user.id` (set at the gateway) and is expected to reach `notifications-service` via Kafka headers.

**Telemetry signals:**

- **Traces** — emitted by each Spring Boot service via OTel Java agent (auto) plus manual spans where useful (Kafka producer/consumer). Envoy sidecar emits its own spans; Collector merges them via shared `traceparent`. Exported OTLP/gRPC → Collector → OllyGarden **and** in-cluster Jaeger (offline-demo fallback).
- **Metrics** — Micrometer → Prometheus endpoint (`/actuator/prometheus`) scraped by Prometheus; also mirrored OTLP to Collector → OllyGarden. Envoy stats scraped separately.
- **Logs** — Logback with OTel appender → Collector → OllyGarden. Logs carry trace/span IDs.

**Collector pipeline:**

```
receivers:  otlp (gRPC + HTTP), prometheus (scrape)
processors: batch, resourcedetection (k8s), memory_limiter
exporters:  otlp/ollygarden, otlp/jaeger (in-cluster), debug (off by default)
```

The OllyGarden API key lives in a k8s Secret referenced by the Collector chart values; never committed.

**Demo failure handling:**

- Services fail gracefully when Kafka is down (Resilience4j retry → circuit break).
- Collector buffers if upstream is unreachable (`sending_queue`).
- The happy path is verified by a CI smoke test (`make smoke`).

## 6. Testing & Verification

The repo is a demo, not a product; testing is scoped to "the demo runs and the scenarios are real."

**Per-service:**

- Keep petclinic's existing JUnit tests; they verify business logic still works after our additions (Kafka producer, springdoc, etc.).
- New `notifications-service` ships with a Spring Boot test using embedded Kafka (`spring-kafka-test`) to verify it consumes `visit.created` and processes baggage headers.

**Whole-stack smoke test (CI on `main`):**

- `make smoke` boots the kind cluster, installs the umbrella chart, runs a small `load.sh` burst, then asserts:
  - All Deployments Ready.
  - Collector debug exporter logs at least one trace with all expected service names.
  - Prometheus scrape of each service returns 200.
- Runs in GitHub Actions on every PR to `main`.

**Per-scenario verification:**

- Each `scenario/*` branch ships a `SCENARIO.md` with a manual checklist:
  - "Before Rose: run `scripts/verify-before.sh` — expect failure with message X."
  - "After Rose: run `scripts/verify-after.sh` — expect pass."
- Verify scripts query in-cluster **Jaeger** (always present per Section 5) for the expected attribute / baggage / cardinality property — no dependency on the OllyGarden cloud for offline demos.
- Optional GH Actions workflow `scenario-smoke.yml`: for each branch, runs `verify-before.sh` and asserts it fails, proving the "bad state" is real.

**Out of scope:**

- No load / performance testing — `k6` script is for traffic generation, not benchmarks.
- No contract testing across services beyond what springdoc + petclinic already provide.

## 7. Resolved Decisions

- **Offline-demo backend:** Jaeger + Grafana ship in the umbrella chart; Collector dual-exports to OllyGarden and Jaeger.
- **`notifications-service` shape:** Kafka-only (no business HTTP API, no OpenAPI entry).
- **Helm distribution:** chart published as OCI artifact to GHCR (`oci://ghcr.io/ollygarden-demo/charts/arbor`).