# arbor 🌳

OllyGarden demo: a forked **Spring PetClinic Microservices** system with Envoy
sidecars, Kafka, and OpenTelemetry — purpose-built to be reviewed and repaired by
[Rose](https://github.com/ollygarden/rose).

## Architecture

See `docs/superpowers/specs/2026-05-28-rose-demo-design.md` (authoritative) and
`docs/architecture.md` (notes).

## Quickstart

Requires: `docker`, `kind`, `kubectl`, `helm`, `k6`, `jq`, Java 21, Maven 3.9+.

```sh
make up        # build images, create kind cluster, install chart (~5 min first run)
make load      # generate traffic with k6
make smoke     # verify the stack
make down      # tear it all down
```

Optional: export `OLLYGARDEN_API_KEY` before `make up` to forward telemetry to the
OllyGarden cloud (in addition to in-cluster Jaeger).

## UIs

| URL | What |
|---|---|
| `kubectl -n arbor port-forward svc/api-gateway 8080:8080` | App entrypoint |
| `kubectl -n arbor port-forward svc/jaeger 16686:16686`    | Jaeger traces |
| `kubectl -n arbor port-forward svc/arbor-prom-grafana 3000:80` | Grafana (admin / arbor) |

## Scenarios

Each Rose scenario is a Git branch. See `docs/scenarios/README.md` for the index.

## Provenance

`services/` is vendored from
[spring-petclinic-microservices](https://github.com/spring-petclinic/spring-petclinic-microservices).
See `services/README.md` for the source commit.
