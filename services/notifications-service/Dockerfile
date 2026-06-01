# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
# Download OTel Java agent as a separate layer for cache efficiency
RUN curl -L -o /opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.28.1/opentelemetry-javaagent.jar
COPY src src
RUN mvn -q -DskipTests package && \
    cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /opt/app
COPY --from=build /app.jar app.jar
COPY --from=build /opentelemetry-javaagent.jar opentelemetry-javaagent.jar
COPY configs/otel.yaml configs/otel.yaml
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -javaagent:/opt/app/opentelemetry-javaagent.jar -Dotel.config.file=/opt/app/configs/otel.yaml"
ENTRYPOINT ["java","-jar","app.jar"]
