# syntax=docker/dockerfile:1
# Generic Dockerfile for every runnable module in this Gradle multi-module build
# (order-service, project-service, ..., discovery-server, gateway-server, config-server).
# Build context must be the repo root, e.g.:
#   docker build --build-arg MODULE=user-service -t earlybird/user-service .

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /workspace

# Dependency layer: only the files Gradle needs to resolve the dependency graph
# (root/module build.gradle, settings.gradle, wrapper - no application source). This
# layer's cache key is the digest of just these files, so a commit that only touches
# application.yml/source (the common case) reuses it as-is - both via plain Docker
# layer cache locally and via cache-from/cache-to type=gha in cd.yml - instead of
# re-running dependency resolution for a module whose build.gradle never changed.
# Deliberately no --mount=type=cache here: a cache mount's content lives outside the
# image and isn't part of the layer's filesystem diff, so it can't be exported/reused
# via type=gha the way a plain RUN's output can. Once source-layer changes stop
# invalidating this layer, the mount isn't needed for that either - Docker's union
# filesystem already carries this layer's downloaded jars forward into every later
# layer, mount or not.
COPY --parents build.gradle settings.gradle gradlew gradle/wrapper/gradle-wrapper.properties gradle/wrapper/gradle-wrapper.jar */build.gradle ./
RUN sh ./gradlew :${MODULE}:dependencies --no-daemon

# Source layer: everything else. Only this layer and the build below are invalidated
# by day-to-day source/config changes - the dependency layer above stays cached.
COPY . .
RUN sh ./gradlew :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
