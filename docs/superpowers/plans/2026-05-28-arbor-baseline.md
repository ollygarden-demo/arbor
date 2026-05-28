# Arbor Baseline (`main`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `main` branch of `ollygarden-demo/arbor` — a forked & extended Spring PetClinic Microservices system running on a local kind cluster with Envoy sidecars, Kafka, OTel Collector, Jaeger, Grafana, and Prometheus, exporting telemetry to both OllyGarden and the in-cluster backend. Demo runs with `make up`; smoke test runs in CI.

**Architecture:** Vendored fork of `spring-petclinic-microservices` extended with: a new Kafka-only `notifications-service`, springdoc-OpenAPI on all HTTP services, Envoy as a per-pod sidecar via Helm, and an observability bundle (OTel Collector, Jaeger, Grafana, Prometheus, Kafka). Single umbrella Helm chart at `deploy/helm/arbor/`.

**Tech Stack:** Java 21 + Spring Boot 3.x (petclinic upstream), Spring Cloud Gateway, Eureka, Spring Cloud Config, Resilience4j, Micrometer + OTel bridge, Kafka (Bitnami chart), Envoy 1.30+, OpenTelemetry Collector (contrib), Jaeger v2, Grafana, Prometheus, Helm 3, kind, k6, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-05-28-rose-demo-design.md`

---

## File Structure

Top-level files this plan creates or modifies (paths relative to repo root, `arbor/`):

| Path | Responsibility |
|---|---|
| `README.md` | Quickstart, scenario index, links. |
| `LICENSE` | Apache-2.0 (matches petclinic upstream). |
| `Makefile` | One-line entry points: `up`, `down`, `load`, `smoke`, `build`, `lint`. |
| `.gitignore` | Java/Maven/IDE/k8s noise. |
| `.tool-versions` | asdf pin for java + helm + kind. |
| `services/*/` | Vendored petclinic services + new `notifications-service`. |
| `services/*/Dockerfile` | Per-service container build. |
| `api/openapi/*.yaml` | Committed OpenAPI specs (generated from running services). |
| `deploy/helm/arbor/` | Umbrella chart. One template file per service deployment; subcharts for Kafka, Prometheus, Grafana, Jaeger, OTel Collector. |
| `deploy/envoy/sidecar.yaml` | Envoy sidecar config template (mounted via ConfigMap). |
| `deploy/kind/cluster.yaml` | kind cluster definition (1 control + 2 workers, registry mirror). |
| `observability/otel-collector/config.yaml` | Collector pipeline: OTLP in → OllyGarden + Jaeger out. |
| `observability/grafana/dashboards/*.json` | Two starter dashboards (app latency, Envoy stats). |
| `scripts/bootstrap.sh` | One-shot: create cluster, install chart, wait-ready. |
| `scripts/teardown.sh` | Delete cluster. |
| `scripts/load.sh` | k6 traffic generator. |
| `scripts/smoke.sh` | Assert deployments Ready + a trace exists in Jaeger. |
| `scripts/gen-openapi.sh` | Port-forward each service, curl `/v3/api-docs`, write yaml. |
| `docs/architecture.md` | Long-form architecture (links the diagram from spec). |
| `docs/scenarios/README.md` | Per-scenario index (populated later). |
| `.github/workflows/ci.yml` | Build images, lint helm chart. |
| `.github/workflows/smoke.yml` | Spin kind cluster, run smoke test. |

---

## Task Ordering

Tasks 1-3 bootstrap the repo. Tasks 4-7 vendor petclinic and add OpenAPI/Kafka. Task 8 builds `notifications-service` (TDD). Tasks 9-10 produce Dockerfiles + OpenAPI specs. Tasks 11-16 build the Helm chart layer by layer. Tasks 17-20 add kind bootstrap, load script, smoke test, CI. Task 21 wraps up README + first commit on a remote.

Each task ends with a commit; many commits per session is intentional.

---

## Task 1: Repo skeleton

**Files:**
- Create: `README.md`, `LICENSE`, `.gitignore`, `.tool-versions`, `Makefile`, `docs/architecture.md`, `docs/scenarios/README.md`.

- [ ] **Step 1: Create `.gitignore`**

```gitignore
# Java / Maven
target/
*.class
*.jar
*.war
.mvn/wrapper/maven-wrapper.jar
# IDE
.idea/
*.iml
.vscode/
.project
.classpath
.settings/
# OS
.DS_Store
# Logs / runtime
*.log
logs/
# Helm
charts/*.tgz
# OpenAPI generated drafts
api/openapi/*.draft.yaml
```

- [ ] **Step 2: Create `.tool-versions`**

```
java temurin-21.0.5+11
maven 3.9.9
helm 3.16.3
kind 0.25.0
kubectl 1.31.4
k6 0.55.0
```

- [ ] **Step 3: Create `LICENSE`**

Copy the Apache 2.0 license text from <https://www.apache.org/licenses/LICENSE-2.0.txt>. Set the copyright line to `Copyright 2026 OllyGarden`.

- [ ] **Step 4: Create `Makefile` skeleton**

```makefile
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
```

- [ ] **Step 5: Create placeholder `README.md`**

```markdown
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
```

- [ ] **Step 6: Create `docs/architecture.md` stub**

```markdown
# Architecture

See the design spec at `docs/superpowers/specs/2026-05-28-rose-demo-design.md`
for the authoritative architecture diagram and decisions.

This file will be expanded with runtime-specific notes once the chart is in.
```

- [ ] **Step 7: Create `docs/scenarios/README.md` stub**

```markdown
# Scenarios

Each Rose scenario lives on its own branch. See the spec for the catalog;
this directory will hold the human-facing demo scripts.
```

- [ ] **Step 8: Verify, then commit**

Run: `make help`
Expected: prints the targets listed above.

```sh
git add -A
git commit -m "chore: repo skeleton (Makefile, LICENSE, gitignore, docs stubs)"
```

---

## Task 2: Vendor petclinic source

We vendor (not submodule) so the codebase is editable in-place.

**Files:**
- Create: `services/api-gateway/`, `services/customers-service/`, `services/vets-service/`, `services/visits-service/`, `services/discovery-server/`, `services/config-server/`, `services/genai-service/` (we'll delete this in step 3).
- Create: `services/README.md` documenting upstream provenance.

- [ ] **Step 1: Clone upstream to a temp location**

```sh
git clone --depth 1 https://github.com/spring-petclinic/spring-petclinic-microservices /tmp/petclinic-upstream
cd /tmp/petclinic-upstream && git rev-parse HEAD > /tmp/petclinic-sha && cd -
```

- [ ] **Step 2: Copy the service modules into `services/`**

```sh
for svc in spring-petclinic-api-gateway spring-petclinic-customers-service \
           spring-petclinic-vets-service spring-petclinic-visits-service \
           spring-petclinic-discovery-server spring-petclinic-config-server; do
  short="${svc#spring-petclinic-}"
  cp -R "/tmp/petclinic-upstream/${svc}" "services/${short}"
done
```

- [ ] **Step 3: Drop the unused upstream genai module**

If it was copied, `rm -rf services/genai-service` — out of scope for the demo.

- [ ] **Step 4: Strip module-internal `.git` references**

Each upstream module ships standalone Maven config; ensure no nested `.git` or `.mvn/wrapper/maven-wrapper.jar` binary blobs:

```sh
find services -name ".git" -type d -exec rm -rf {} +
find services -name "maven-wrapper.jar" -delete
```

- [ ] **Step 5: Write `services/README.md`**

```markdown
# Services

Forked and vendored from
[spring-petclinic/spring-petclinic-microservices](https://github.com/spring-petclinic/spring-petclinic-microservices)
at commit `<paste the sha from /tmp/petclinic-sha>`.

We do not track upstream; changes are made directly in this tree.
```

Replace `<paste the sha...>` with `cat /tmp/petclinic-sha`.

- [ ] **Step 6: Smoke-build one service**

Run: `cd services/customers-service && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS, a `target/*.jar` produced.

If the Maven wrapper script is missing, `cp -R /tmp/petclinic-upstream/.mvn services/customers-service/` and try again.

- [ ] **Step 7: Commit**

```sh
git add -A
git commit -m "feat(services): vendor spring-petclinic-microservices"
```

---

## Task 3: Add springdoc-openapi to HTTP services

Each Spring Boot HTTP service gets the springdoc starter so it exposes `/v3/api-docs` and `/swagger-ui.html`.

**Files (per service):** `pom.xml`, `src/main/resources/application.yml`.

Apply the same change to: `api-gateway`, `customers-service`, `vets-service`, `visits-service`. (`discovery-server` and `config-server` are infrastructure and don't expose a business API.)

- [ ] **Step 1: Add dependency in each `pom.xml`**

Inside `<dependencies>`:

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.6.0</version>
</dependency>
```

For `api-gateway` (Spring Cloud Gateway is reactive), use the webflux variant instead:

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
  <version>2.6.0</version>
</dependency>
```

- [ ] **Step 2: Pin the api-docs path in each `application.yml`**

Append:

```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 3: Verify each service builds**

Run: `cd services/customers-service && ./mvnw -q -DskipTests package`
Expected: BUILD SUCCESS. Repeat for the other three.

- [ ] **Step 4: Verify `/v3/api-docs` locally for one service**

```sh
cd services/customers-service
./mvnw -q spring-boot:run &
PID=$!
sleep 30
curl -s http://localhost:8081/v3/api-docs | head -c 200
kill $PID
```

Expected: a JSON document starting with `{"openapi":"3.`.

- [ ] **Step 5: Commit**

```sh
git add -A
git commit -m "feat(api): add springdoc-openapi to all HTTP services"
```

---

## Task 4: Add Kafka producer to `visits-service`

When a visit is created, publish a `visit.created` event to Kafka.

**Files:**
- Modify: `services/visits-service/pom.xml`
- Create: `services/visits-service/src/main/java/org/springframework/samples/petclinic/visits/event/VisitCreatedEvent.java`
- Create: `services/visits-service/src/main/java/org/springframework/samples/petclinic/visits/event/VisitEventPublisher.java`
- Modify: `services/visits-service/src/main/java/org/springframework/samples/petclinic/visits/web/VisitResource.java`
- Modify: `services/visits-service/src/main/resources/application.yml`
- Create: `services/visits-service/src/test/java/org/springframework/samples/petclinic/visits/event/VisitEventPublisherTest.java`

- [ ] **Step 1: Write the failing test**

`VisitEventPublisherTest.java`:

```java
package org.springframework.samples.petclinic.visits.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.Collections;
import java.util.Map;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "visit.created" })
class VisitEventPublisherTest {
  @Autowired VisitEventPublisher publisher;
  @Autowired org.springframework.kafka.test.EmbeddedKafkaBroker broker;

  @Test
  void publishesVisitCreatedEvent() {
    publisher.publish(new VisitCreatedEvent(1, 42, new Date(), "checkup"));

    Map<String, Object> props = KafkaTestUtils.consumerProps("g", "false", broker);
    try (Consumer<String, String> c = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
            props, new org.apache.kafka.common.serialization.StringDeserializer(),
            new org.apache.kafka.common.serialization.StringDeserializer())) {
      c.subscribe(Collections.singleton("visit.created"));
      ConsumerRecord<String, String> rec = KafkaTestUtils.getSingleRecord(c, "visit.created");
      assertThat(rec.value()).contains("\"petId\":42");
    }
  }
}
```

- [ ] **Step 2: Add Kafka dependencies to `pom.xml`**

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka-test</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Run the test to confirm it fails**

Run: `./mvnw -q -pl . -Dtest=VisitEventPublisherTest test`
Expected: FAIL with `VisitEventPublisher` or `VisitCreatedEvent` symbol-not-found.

- [ ] **Step 4: Implement `VisitCreatedEvent`**

```java
package org.springframework.samples.petclinic.visits.event;

import java.util.Date;
public record VisitCreatedEvent(int id, int petId, Date date, String description) {}
```

- [ ] **Step 5: Implement `VisitEventPublisher`**

```java
package org.springframework.samples.petclinic.visits.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class VisitEventPublisher {
  private static final String TOPIC = "visit.created";
  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper json;

  public VisitEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
    this.kafka = kafka;
    this.json = json;
  }

  public void publish(VisitCreatedEvent event) {
    try {
      kafka.send(TOPIC, String.valueOf(event.id()), json.writeValueAsString(event));
    } catch (Exception e) {
      throw new IllegalStateException("failed to serialize visit event", e);
    }
  }
}
```

- [ ] **Step 6: Configure Kafka in `application.yml`**

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
```

- [ ] **Step 7: Hook the publisher into `VisitResource`**

Inject `VisitEventPublisher` via the constructor; after the existing `visitRepository.save(visit)` call, add:

```java
publisher.publish(new VisitCreatedEvent(
    visit.getId(), visit.getPetId(), visit.getDate(), visit.getDescription()));
```

- [ ] **Step 8: Run tests**

Run: `./mvnw -q test`
Expected: PASS (including the new test and all existing).

- [ ] **Step 9: Commit**

```sh
git add -A
git commit -m "feat(visits): publish visit.created events to Kafka"
```

---

## Task 5: Scaffold `notifications-service`

New Kafka-only Spring Boot service. Health endpoint only (no business HTTP API).

**Files:**
- Create the directory tree:
  - `services/notifications-service/pom.xml`
  - `services/notifications-service/mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (copy from `services/visits-service/`)
  - `services/notifications-service/src/main/java/garden/olly/arbor/notifications/NotificationsApplication.java`
  - `services/notifications-service/src/main/java/garden/olly/arbor/notifications/VisitCreatedListener.java`
  - `services/notifications-service/src/main/resources/application.yml`
  - `services/notifications-service/src/main/resources/bootstrap.yml`
  - `services/notifications-service/src/test/java/garden/olly/arbor/notifications/VisitCreatedListenerTest.java`

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>
  <groupId>garden.olly.arbor</groupId>
  <artifactId>notifications-service</artifactId>
  <version>0.1.0</version>
  <properties><java.version>21</java.version></properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-config</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
      <groupId>io.micrometer</groupId>
      <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.kafka</groupId>
      <artifactId>spring-kafka-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>2023.0.3</version>
        <type>pom</type><scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <build><plugins><plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
  </plugin></plugins></build>
</project>
```

- [ ] **Step 2: Write `NotificationsApplication.java`**

```java
package garden.olly.arbor.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class NotificationsApplication {
  public static void main(String[] args) { SpringApplication.run(NotificationsApplication.class, args); }
}
```

- [ ] **Step 3: Write the failing listener test**

`VisitCreatedListenerTest.java`:

```java
package garden.olly.arbor.notifications;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(partitions = 1, topics = { "visit.created" })
class VisitCreatedListenerTest {
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired VisitCreatedListener listener;

  @Test
  void processesVisitCreatedEvents() {
    kafka.send("visit.created", "1", "{\"id\":1,\"petId\":42,\"description\":\"checkup\"}");
    await().atMost(Duration.ofSeconds(10))
           .untilAsserted(() -> assertThat(listener.processedCount()).isEqualTo(1));
  }
}
```

Add `org.awaitility:awaitility:4.2.2` as a `<scope>test</scope>` dependency in `pom.xml`.

- [ ] **Step 4: Run the test to verify it fails**

Run: `./mvnw -q test`
Expected: FAIL — `VisitCreatedListener` doesn't exist.

- [ ] **Step 5: Implement `VisitCreatedListener`**

```java
package garden.olly.arbor.notifications;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VisitCreatedListener {
  private static final Logger log = LoggerFactory.getLogger(VisitCreatedListener.class);
  private final AtomicInteger processed = new AtomicInteger(0);

  @KafkaListener(topics = "visit.created", groupId = "notifications")
  public void onVisitCreated(String payload) {
    log.info("notify: {}", payload);
    processed.incrementAndGet();
  }

  public int processedCount() { return processed.get(); }
}
```

- [ ] **Step 6: Write `application.yml`**

```yaml
spring:
  application:
    name: notifications-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest

server:
  port: 8088

management:
  endpoints:
    web:
      exposure:
        include: health, prometheus
```

- [ ] **Step 7: Write `bootstrap.yml`**

```yaml
spring:
  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://config-server:8888}
eureka:
  client:
    serviceUrl:
      defaultZone: ${EUREKA_SERVER_URL:http://discovery-server:8761/eureka/}
```

- [ ] **Step 8: Re-run tests**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 9: Commit**

```sh
git add -A
git commit -m "feat(notifications): kafka-only consumer of visit.created"
```

---

## Task 6: Dockerfiles for each service

Use a shared multi-stage Dockerfile pattern — produces small layers without external base-image surprises.

**Files (per service):** `services/<svc>/Dockerfile`, `services/<svc>/.dockerignore`.

- [ ] **Step 1: Write a reference `Dockerfile`**

For each service, create `services/<svc>/Dockerfile`:

```dockerfile
# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package && \
    cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /opt/app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENTRYPOINT ["java","-jar","app.jar"]
```

- [ ] **Step 2: Write `.dockerignore`**

```
target/
*.iml
.idea/
.git
```

- [ ] **Step 3: Build one image to verify**

Run:
```sh
docker build -t arbor-customers:dev services/customers-service
```
Expected: image built, `docker images | grep arbor-customers` shows it.

- [ ] **Step 4: Repeat for all services**

```sh
for d in services/*/; do
  s=$(basename "$d"); docker build -t "arbor-${s}:dev" "$d";
done
```

Expected: 7 images.

- [ ] **Step 5: Commit**

```sh
git add -A
git commit -m "build: per-service Dockerfiles"
```

---

## Task 7: Generate and commit OpenAPI specs

**Files:**
- Create: `scripts/gen-openapi.sh`, `api/openapi/{customers,vets,visits}.yaml`.

- [ ] **Step 1: Write `scripts/gen-openapi.sh`**

```sh
#!/usr/bin/env bash
set -euo pipefail

# Generates OpenAPI yaml from each running service.
# Requires the stack to be up: `make up`.

declare -A SVC=(
  [customers]=customers-service:8081
  [vets]=vets-service:8083
  [visits]=visits-service:8082
)

mkdir -p api/openapi

for name in "${!SVC[@]}"; do
  host="${SVC[$name]}"
  echo "→ ${name} (${host})"
  kubectl -n arbor port-forward "svc/${host%%:*}" "${host##*:}:${host##*:}" >/dev/null &
  PF=$!
  trap "kill $PF 2>/dev/null || true" EXIT
  sleep 3
  curl -fsS "http://localhost:${host##*:}/v3/api-docs.yaml" \
    > "api/openapi/${name}.yaml"
  kill $PF
  trap - EXIT
done
echo "✓ wrote api/openapi/*.yaml"
```

Make executable: `chmod +x scripts/gen-openapi.sh`.

- [ ] **Step 2: Add `springdoc.api-docs.enabled=true` and a YAML route**

In each service's `application.yml`, ensure:
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
```

(YAML version is auto-served at `/v3/api-docs.yaml` by springdoc — no extra config needed in 2.6.0.)

- [ ] **Step 3: Run the stack and generate specs**

This depends on Tasks 11-17 (chart + bootstrap). Defer step 3 + commit until after Task 17. Add a note at the top of `gen-openapi.sh` describing the dependency. Mark this task **Provisional** in the plan tracker and move on; revisit after `make up` works.

- [ ] **Step 4: Commit the script now (specs generated later)**

```sh
git add scripts/gen-openapi.sh
git commit -m "build: openapi generation script (specs committed after chart is up)"
```

---

## Task 8: Envoy sidecar config

A single Envoy config injected via ConfigMap into every app pod, providing an egress proxy and emitting tracing/metrics.

**Files:**
- Create: `deploy/envoy/sidecar.yaml`.

- [ ] **Step 1: Write `sidecar.yaml`**

```yaml
admin:
  address:
    socket_address: { address: 0.0.0.0, port_value: 9901 }

static_resources:
  listeners:
    - name: egress
      address:
        socket_address: { address: 0.0.0.0, port_value: 15001 }
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                stat_prefix: egress
                codec_type: AUTO
                route_config:
                  name: local_route
                  virtual_hosts:
                    - name: backend
                      domains: ["*"]
                      routes:
                        - match: { prefix: "/" }
                          route: { cluster: dynamic_forward_proxy_cluster }
                http_filters:
                  - name: envoy.filters.http.dynamic_forward_proxy
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.filters.http.dynamic_forward_proxy.v3.FilterConfig
                      dns_cache_config: { name: dns_cache, dns_lookup_family: V4_ONLY }
                  - name: envoy.filters.http.router
                    typed_config:
                      "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
                tracing:
                  provider:
                    name: envoy.tracers.opentelemetry
                    typed_config:
                      "@type": type.googleapis.com/envoy.config.trace.v3.OpenTelemetryConfig
                      grpc_service:
                        envoy_grpc: { cluster_name: otel_collector }
                      service_name: envoy-sidecar

  clusters:
    - name: dynamic_forward_proxy_cluster
      lb_policy: CLUSTER_PROVIDED
      cluster_type:
        name: envoy.clusters.dynamic_forward_proxy
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.clusters.dynamic_forward_proxy.v3.ClusterConfig
          dns_cache_config: { name: dns_cache, dns_lookup_family: V4_ONLY }
    - name: otel_collector
      type: STRICT_DNS
      lb_policy: ROUND_ROBIN
      typed_extension_protocol_options:
        envoy.extensions.upstreams.http.v3.HttpProtocolOptions:
          "@type": type.googleapis.com/envoy.extensions.upstreams.http.v3.HttpProtocolOptions
          explicit_http_config: { http2_protocol_options: {} }
      load_assignment:
        cluster_name: otel_collector
        endpoints:
          - lb_endpoints:
              - endpoint:
                  address:
                    socket_address:
                      address: otel-collector.arbor.svc.cluster.local
                      port_value: 4317
```

- [ ] **Step 2: Validate config locally**

Run:
```sh
docker run --rm -v "$PWD/deploy/envoy/sidecar.yaml:/c.yaml" \
  envoyproxy/envoy:v1.31-latest --mode validate -c /c.yaml
```
Expected: `configuration '/c.yaml' OK`.

- [ ] **Step 3: Commit**

```sh
git add -A
git commit -m "feat(envoy): sidecar config with OTel tracing"
```

---

## Task 9: Helm chart skeleton

**Files:**
- Create: `deploy/helm/arbor/Chart.yaml`, `Chart.lock`, `values.yaml`, `values-local.yaml`, `templates/_helpers.tpl`, `templates/NOTES.txt`, `.helmignore`.

- [ ] **Step 1: Write `Chart.yaml`**

```yaml
apiVersion: v2
name: arbor
description: OllyGarden arbor demo (Rose showcase) — PetClinic + Envoy + Kafka + OTel
type: application
version: 0.1.0
appVersion: "main"
icon: https://ollygarden.com/favicon.svg
dependencies:
  - name: kafka
    version: 30.1.8
    repository: https://charts.bitnami.com/bitnami
  - name: opentelemetry-collector
    version: 0.108.0
    repository: https://open-telemetry.github.io/opentelemetry-helm-charts
  - name: jaeger
    version: 3.4.1
    repository: https://jaegertracing.github.io/helm-charts
  - name: kube-prometheus-stack
    version: 65.5.1
    repository: https://prometheus-community.github.io/helm-charts
    alias: prom
```

- [ ] **Step 2: Write `.helmignore`**

```
.git/
*.tgz
charts/
README.md.gotmpl
```

- [ ] **Step 3: Write `values.yaml`**

```yaml
imageRegistry: ghcr.io/ollygarden-demo
imageTag: main
imagePullPolicy: IfNotPresent

ollyGarden:
  otlpEndpoint: ""            # set via --set or values-local.yaml
  apiKeySecretRef:
    name: ollygarden-api-key
    key: api-key

envoy:
  image: envoyproxy/envoy:v1.31-latest

services:
  apiGateway:     { name: api-gateway,     port: 8080 }
  customers:      { name: customers-service, port: 8081 }
  vets:           { name: vets-service,    port: 8083 }
  visits:         { name: visits-service,  port: 8082 }
  notifications:  { name: notifications-service, port: 8088 }
  discovery:      { name: discovery-server, port: 8761 }
  config:         { name: config-server,   port: 8888 }

kafka:
  controller: { replicaCount: 1 }
  broker:     { replicaCount: 1 }
  listeners:
    client:
      protocol: PLAINTEXT
  sasl:
    enabledMechanisms: ""
  provisioning:
    enabled: true
    topics:
      - name: visit.created
        partitions: 3
        replicationFactor: 1

opentelemetry-collector:
  mode: deployment
  image: { repository: otel/opentelemetry-collector-contrib }
  config: {}  # rendered from configmap in templates/otel-config.yaml

jaeger:
  provisionDataStore: { cassandra: false }
  allInOne: { enabled: true }
  storage: { type: memory }
  agent: { enabled: false }
  collector: { enabled: false }
  query: { enabled: false }

prom:
  grafana:
    adminPassword: arbor
    dashboardProviders: {}
  prometheus:
    prometheusSpec:
      serviceMonitorSelectorNilUsesHelmValues: false
```

- [ ] **Step 4: Write `values-local.yaml`**

```yaml
ollyGarden:
  otlpEndpoint: ""  # leave blank to skip cloud export in local demos
imagePullPolicy: Never  # use locally-built kind images
```

- [ ] **Step 5: Write `templates/_helpers.tpl`**

```
{{- define "arbor.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: arbor
app.kubernetes.io/managed-by: Helm
{{- end -}}

{{- define "arbor.image" -}}
{{ $.Values.imageRegistry }}/{{ .name }}:{{ $.Values.imageTag }}
{{- end -}}
```

- [ ] **Step 6: Write `templates/NOTES.txt`**

```
🌹 arbor is up.

Gateway:     kubectl -n arbor port-forward svc/api-gateway 8080:8080
Jaeger UI:   kubectl -n arbor port-forward svc/jaeger 16686:16686
Grafana:     kubectl -n arbor port-forward svc/arbor-prom-grafana 3000:80   (admin / arbor)

Generate load:   make load
Smoke test:      make smoke
```

- [ ] **Step 7: Update dependencies and lint**

Run:
```sh
helm dependency update deploy/helm/arbor
helm lint deploy/helm/arbor
```
Expected: `1 chart(s) linted, 0 chart(s) failed`.

- [ ] **Step 8: Commit**

```sh
git add deploy/helm/arbor
git commit -m "feat(deploy): helm chart skeleton with bundled deps"
```

---

## Task 10: Helm — per-service Deployment templates with Envoy sidecar

One template file per service. Each Deployment runs the app container plus an Envoy sidecar that mounts `deploy/envoy/sidecar.yaml` from a ConfigMap.

**Files:**
- Create: `deploy/helm/arbor/templates/envoy-config.yaml` (ConfigMap holding the sidecar config).
- Create: `deploy/helm/arbor/templates/service-<name>.yaml` for each of: discovery, config, api-gateway, customers, vets, visits, notifications.

- [ ] **Step 1: Write `envoy-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: arbor-envoy-sidecar
  labels: {{- include "arbor.labels" (dict "name" "envoy") | nindent 4 }}
data:
  sidecar.yaml: |
{{ .Files.Get "files/envoy-sidecar.yaml" | indent 4 }}
```

- [ ] **Step 2: Copy the envoy config into the chart**

```sh
mkdir -p deploy/helm/arbor/files
cp deploy/envoy/sidecar.yaml deploy/helm/arbor/files/envoy-sidecar.yaml
```

- [ ] **Step 3: Write a reference Deployment template for one service**

`templates/service-customers.yaml`:

```yaml
{{- $s := .Values.services.customers }}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $s.name }}
  labels: {{- include "arbor.labels" (dict "name" $s.name) | nindent 4 }}
spec:
  replicas: 1
  selector: { matchLabels: { app.kubernetes.io/name: {{ $s.name }} } }
  template:
    metadata:
      labels: {{- include "arbor.labels" (dict "name" $s.name) | nindent 8 }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "{{ $s.port }}"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: app
          image: {{ include "arbor.image" (dict "name" $s.name) | trim }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          ports: [{ containerPort: {{ $s.port }} }]
          env:
            - { name: SPRING_PROFILES_ACTIVE,         value: "kubernetes" }
            - { name: EUREKA_SERVER_URL,              value: "http://discovery-server:8761/eureka/" }
            - { name: CONFIG_SERVER_URL,              value: "http://config-server:8888" }
            - { name: KAFKA_BOOTSTRAP,                value: "arbor-kafka:9092" }
            - { name: OTEL_EXPORTER_OTLP_ENDPOINT,    value: "http://otel-collector:4318" }
            - { name: OTEL_SERVICE_NAME,              value: "{{ $s.name }}" }
            - { name: OTEL_RESOURCE_ATTRIBUTES,       value: "deployment.environment=arbor" }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: {{ $s.port }} }
            initialDelaySeconds: 10
        - name: envoy
          image: {{ .Values.envoy.image }}
          args: ["-c", "/etc/envoy/sidecar.yaml"]
          ports: [{ containerPort: 15001 }]
          volumeMounts:
            - { name: envoy-config, mountPath: /etc/envoy }
      volumes:
        - name: envoy-config
          configMap: { name: arbor-envoy-sidecar }
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $s.name }}
spec:
  selector: { app.kubernetes.io/name: {{ $s.name }} }
  ports: [{ name: http, port: {{ $s.port }}, targetPort: {{ $s.port }} }]
```

- [ ] **Step 4: Copy-adapt for the other six services**

Repeat step 3 for `apiGateway`, `vets`, `visits`, `notifications`, `discovery`, `config`. Each template lives in its own `service-<name>.yaml`. Substitute `customers` → the service key from `values.yaml`. For `notifications`, set the readiness probe path to `/actuator/health/readiness` on port `8088` and do **not** add Prometheus scrape annotations (it has no HTTP business surface; metrics still flow OTLP).

For `discovery` and `config`, drop the Envoy sidecar — they're infra and don't need it.

- [ ] **Step 5: Lint and render**

Run:
```sh
helm lint deploy/helm/arbor
helm template arbor deploy/helm/arbor --namespace arbor > /tmp/render.yaml
grep -c '^kind: Deployment' /tmp/render.yaml
```
Expected: lint passes; `grep` returns at least 7.

- [ ] **Step 6: Commit**

```sh
git add -A
git commit -m "feat(deploy): service Deployments with Envoy sidecar"
```

---

## Task 11: Helm — OTel Collector configmap

The Collector's config sits in a ConfigMap; the subchart's `config:` value references it via `--config`.

**Files:**
- Create: `deploy/helm/arbor/templates/otel-collector-config.yaml`.
- Modify: `deploy/helm/arbor/values.yaml` (point the subchart at the configmap).

- [ ] **Step 1: Write `otel-collector-config.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
data:
  collector.yaml: |
    receivers:
      otlp:
        protocols:
          grpc: { endpoint: 0.0.0.0:4317 }
          http: { endpoint: 0.0.0.0:4318 }
      prometheus:
        config:
          scrape_configs:
            - job_name: kubernetes-pods
              kubernetes_sd_configs: [{ role: pod }]
              relabel_configs:
                - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
                  action: keep
                  regex: "true"
                - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
                  target_label: __metrics_path__
                  regex: (.+)

    processors:
      batch: {}
      memory_limiter: { check_interval: 1s, limit_mib: 400 }
      resourcedetection: { detectors: [env, system, k8snode] }

    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls: { insecure: true }
{{- if .Values.ollyGarden.otlpEndpoint }}
      otlp/ollygarden:
        endpoint: {{ .Values.ollyGarden.otlpEndpoint | quote }}
        headers:
          x-api-key: ${env:OLLYGARDEN_API_KEY}
{{- end }}
      debug: {}

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, resourcedetection, batch]
          exporters: [otlp/jaeger{{- if .Values.ollyGarden.otlpEndpoint }}, otlp/ollygarden{{- end }}]
        metrics:
          receivers: [otlp, prometheus]
          processors: [memory_limiter, resourcedetection, batch]
          exporters: [{{ if .Values.ollyGarden.otlpEndpoint }}otlp/ollygarden{{ else }}debug{{ end }}]
        logs:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [{{ if .Values.ollyGarden.otlpEndpoint }}otlp/ollygarden{{ else }}debug{{ end }}]
```

- [ ] **Step 2: Wire the configmap into the subchart**

In `values.yaml`, expand the `opentelemetry-collector:` block:

```yaml
opentelemetry-collector:
  mode: deployment
  image: { repository: otel/opentelemetry-collector-contrib }
  command:
    name: otelcol-contrib
    extraArgs: ["--config=/conf/collector.yaml"]
  extraVolumes:
    - name: cfg
      configMap: { name: otel-collector-config }
  extraVolumeMounts:
    - { name: cfg, mountPath: /conf }
  extraEnvs:
    - name: OLLYGARDEN_API_KEY
      valueFrom:
        secretKeyRef:
          name: {{ "{{" }} .Values.ollyGarden.apiKeySecretRef.name {{ "}}" }}
          key:  {{ "{{" }} .Values.ollyGarden.apiKeySecretRef.key {{ "}}" }}
          optional: true
  service:
    type: ClusterIP
```

(Subchart values don't support template substitution; replace the templated `secretKeyRef` with a literal `name: ollygarden-api-key` / `key: api-key` and document that the names are pinned.)

- [ ] **Step 3: Lint**

Run: `helm lint deploy/helm/arbor`
Expected: clean.

- [ ] **Step 4: Commit**

```sh
git add -A
git commit -m "feat(otel): collector config dual-exporting to jaeger and ollygarden"
```

---

## Task 12: Grafana dashboards

Two JSON dashboards baked into the chart and auto-provisioned via the kube-prometheus-stack sidecar.

**Files:**
- Create: `observability/grafana/dashboards/arbor-overview.json`
- Create: `observability/grafana/dashboards/envoy-stats.json`
- Create: `deploy/helm/arbor/templates/grafana-dashboards.yaml` (ConfigMap with `grafana_dashboard: "1"` label).

- [ ] **Step 1: Author dashboards**

Start with two minimal JSON dashboards. For `arbor-overview.json`, include panels: requests/sec by service (`sum by(service)(rate(http_server_requests_seconds_count[1m]))`), p95 latency, error rate. For `envoy-stats.json`, include: upstream rq total, upstream rq time p95.

Use Grafana's "Export for sharing externally" to produce the JSON, or hand-write skeletons — store under `observability/grafana/dashboards/`.

- [ ] **Step 2: Write `grafana-dashboards.yaml`**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: arbor-grafana-dashboards
  labels:
    grafana_dashboard: "1"
data:
{{- range $path, $_ := .Files.Glob "files/grafana-dashboards/*.json" }}
  {{ base $path }}: |-
{{ $.Files.Get $path | indent 4 }}
{{- end }}
```

- [ ] **Step 3: Copy dashboards into the chart's files dir**

```sh
mkdir -p deploy/helm/arbor/files/grafana-dashboards
cp observability/grafana/dashboards/*.json deploy/helm/arbor/files/grafana-dashboards/
```

- [ ] **Step 4: Enable dashboard sidecar in `values.yaml`**

Under `prom.grafana`:

```yaml
sidecar:
  dashboards:
    enabled: true
    label: grafana_dashboard
    searchNamespace: ALL
```

- [ ] **Step 5: Lint**

Run: `helm lint deploy/helm/arbor`
Expected: clean.

- [ ] **Step 6: Commit**

```sh
git add -A
git commit -m "feat(grafana): seed dashboards (arbor-overview, envoy-stats)"
```

---

## Task 13: kind cluster + bootstrap script

**Files:**
- Create: `deploy/kind/cluster.yaml`, `scripts/bootstrap.sh`, `scripts/teardown.sh`.

- [ ] **Step 1: Write `deploy/kind/cluster.yaml`**

```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: arbor
nodes:
  - role: control-plane
  - role: worker
  - role: worker
containerdConfigPatches:
  - |
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5001"]
      endpoint = ["http://kind-registry:5000"]
```

- [ ] **Step 2: Write `scripts/bootstrap.sh`**

```sh
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
```

Make executable: `chmod +x scripts/bootstrap.sh`.

- [ ] **Step 3: Write `scripts/teardown.sh`**

```sh
#!/usr/bin/env bash
set -euo pipefail
kind delete cluster --name arbor || true
docker rm -f kind-registry 2>/dev/null || true
```

Make executable: `chmod +x scripts/teardown.sh`.

- [ ] **Step 4: Bring up the stack**

Run: `make up`
Expected: cluster created, images built and pushed, chart installed, pods reach Ready. Allow up to 5 minutes for first run.

- [ ] **Step 5: Manual smoke verification**

Run:
```sh
kubectl -n arbor get pods
kubectl -n arbor port-forward svc/api-gateway 8080:8080 &
sleep 5
curl -fsS http://localhost:8080/api/customer/owners | head -c 200
```
Expected: pods Ready; gateway returns JSON.

- [ ] **Step 6: Commit**

```sh
git add -A
git commit -m "feat(deploy): kind bootstrap + teardown scripts"
```

---

## Task 14: Generate OpenAPI specs (resumed from Task 7)

Now that `make up` works, run the generator.

- [ ] **Step 1: With the stack up, run the generator**

Run: `make openapi`
Expected: `api/openapi/customers.yaml`, `vets.yaml`, `visits.yaml` are written, each starts with `openapi: 3.`.

- [ ] **Step 2: Commit**

```sh
git add api/openapi
git commit -m "feat(api): commit generated OpenAPI specs"
```

---

## Task 15: k6 load script

**Files:**
- Create: `scripts/load.sh`, `scripts/load.js`.

- [ ] **Step 1: Write `scripts/load.js`**

```javascript
import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 5,
  duration: '60s',
};

const BASE = __ENV.GATEWAY || 'http://localhost:8080';

export default function () {
  const owners = http.get(`${BASE}/api/customer/owners`);
  check(owners, { 'owners 200': r => r.status === 200 });

  const vets = http.get(`${BASE}/api/vet/vets`);
  check(vets, { 'vets 200': r => r.status === 200 });

  const visits = http.post(
    `${BASE}/api/visit/owners/1/pets/1/visits`,
    JSON.stringify({ date: '2026-05-28', description: 'checkup' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(visits, { 'visit 200': r => [200,201,204].includes(r.status) });

  sleep(1);
}
```

- [ ] **Step 2: Write `scripts/load.sh`**

```sh
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
```

Make executable: `chmod +x scripts/load.sh`.

- [ ] **Step 3: Run it**

Run: `make load`
Expected: 5 VUs run for 60s, all checks succeed at >95%.

- [ ] **Step 4: Commit**

```sh
git add -A
git commit -m "feat(scripts): k6 load generator"
```

---

## Task 16: Smoke test

Verifies pods are Ready and that a trace with all expected service names is present in Jaeger.

**Files:**
- Create: `scripts/smoke.sh`.

- [ ] **Step 1: Write `scripts/smoke.sh`**

```sh
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
```

Make executable: `chmod +x scripts/smoke.sh`.

- [ ] **Step 2: Run it**

Run: `make smoke`
Expected: all "✓ traces found for ..." lines, ends with `✓ smoke OK`.

- [ ] **Step 3: Commit**

```sh
git add -A
git commit -m "feat(scripts): end-to-end smoke test"
```

---

## Task 17: CI — build images + lint helm

**Files:**
- Create: `.github/workflows/ci.yml`.

- [ ] **Step 1: Write `ci.yml`**

```yaml
name: ci
on:
  pull_request: {}
  push: { branches: [main] }
jobs:
  java:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - api-gateway
          - customers-service
          - vets-service
          - visits-service
          - notifications-service
          - discovery-server
          - config-server
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - name: build + test
        working-directory: services/${{ matrix.service }}
        run: ./mvnw -q verify
      - name: docker build
        run: docker build -t arbor-${{ matrix.service }}:ci services/${{ matrix.service }}

  helm:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: azure/setup-helm@v4
      - run: helm dependency update deploy/helm/arbor
      - run: helm lint deploy/helm/arbor
      - run: helm template arbor deploy/helm/arbor --namespace arbor > /tmp/render.yaml
```

- [ ] **Step 2: Commit**

```sh
git add -A
git commit -m "ci: build & test all services + lint helm chart"
```

---

## Task 18: CI — smoke test on kind

**Files:**
- Create: `.github/workflows/smoke.yml`.

- [ ] **Step 1: Write `smoke.yml`**

```yaml
name: smoke
on:
  pull_request: {}
  push: { branches: [main] }
jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - uses: helm/kind-action@v1
        with:
          config: deploy/kind/cluster.yaml
          cluster_name: arbor
      - uses: azure/setup-helm@v4
      - uses: grafana/setup-k6-action@v1
      - name: install jq
        run: sudo apt-get update && sudo apt-get install -y jq
      - name: bootstrap
        run: ./scripts/bootstrap.sh
        env:
          OLLYGARDEN_API_KEY: ""
      - name: smoke
        run: ./scripts/smoke.sh
```

- [ ] **Step 2: Commit**

```sh
git add -A
git commit -m "ci: kind-based smoke test"
```

---

## Task 19: Publish Helm chart to GHCR

**Files:**
- Create: `.github/workflows/release-chart.yml`.

- [ ] **Step 1: Write `release-chart.yml`**

```yaml
name: release-chart
on:
  push:
    tags: ['v*']
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write }
    steps:
      - uses: actions/checkout@v4
      - uses: azure/setup-helm@v4
      - name: login GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | helm registry login ghcr.io -u "${{ github.actor }}" --password-stdin
      - name: package
        run: |
          helm dependency update deploy/helm/arbor
          helm package deploy/helm/arbor -d /tmp
      - name: push
        run: helm push /tmp/arbor-*.tgz oci://ghcr.io/ollygarden-demo/charts
```

- [ ] **Step 2: Commit**

```sh
git add -A
git commit -m "ci: publish chart to GHCR OCI on tag"
```

---

## Task 20: Final README pass

**Files:**
- Modify: `README.md`.

- [ ] **Step 1: Expand `README.md`**

```markdown
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
```

- [ ] **Step 2: Commit**

```sh
git add README.md
git commit -m "docs: expand README with quickstart and UIs"
```

---

## Task 21: Create the GitHub repo and push

**Out-of-band actions (require human auth):**

- [ ] **Step 1: Create `ollygarden-demo` org on GitHub (if missing)**

Through the GitHub UI. Confirm with the OllyGarden team before doing this.

- [ ] **Step 2: Create the `arbor` repo under that org**

```sh
gh repo create ollygarden-demo/arbor --public --source=. --remote=origin --push
```

Expected: repo created, `main` pushed, CI runs.

- [ ] **Step 3: Verify CI**

Visit the repo's Actions tab. Both `ci` and `smoke` workflows should run and pass on the initial push.

- [ ] **Step 4: Tag v0.1.0 to test chart publishing**

```sh
git tag v0.1.0 && git push origin v0.1.0
```

Expected: `release-chart` workflow runs, chart is published to `oci://ghcr.io/ollygarden-demo/charts/arbor`.

---

## Done criteria

- `make up && make smoke` exits 0 on a fresh machine.
- `helm install arbor oci://ghcr.io/ollygarden-demo/charts/arbor` works against a generic kind cluster.
- `git diff main..main^` is empty after a clean run (no committed cruft from `make up`).
- All three workflows (`ci`, `smoke`, `release-chart`) green.

When done, write the scenario plans against the *actual* baseline by re-entering `superpowers:brainstorming` (already brainstormed) → `superpowers:writing-plans` for each `scenario/*` branch.
