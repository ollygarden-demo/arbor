# arbor

OllyGarden demo: a forked Spring PetClinic Microservices system with Envoy
sidecars, Kafka, and OpenTelemetry — designed to be reviewed and repaired by
**Rose**.

See `docs/architecture.md` and `docs/scenarios/README.md`.

## Quickstart

```sh
make up      # create kind cluster, install the umbrella chart
make load    # generate traffic
make smoke   # verify the stack
make down    # tear it all down
```
