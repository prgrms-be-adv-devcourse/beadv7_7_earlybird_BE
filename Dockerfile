# syntax=docker/dockerfile:1
# Generic Dockerfile for every runnable module in this Gradle multi-module build
# (order-service, project-service, ..., discovery-server, gateway-server, config-server).
# Build context must be the repo root, e.g.:
#   docker build --build-arg MODULE=user-service -t earlybird/user-service .

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
