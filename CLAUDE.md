# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workspace Overview

This directory is **not a single repository** — it is the workspace for a backend advanced devcourse (백엔드 심화 데브코스 7기). It holds several independent git repos plus team documents. The user is **김하나한**, a member of Team 5, building "얼리버드" (Earlybird), a reward-based crowdfunding platform (All-or-Nothing funding: goal reached → settle to creator, failed → batch refund backers). Team 5 members: 조우진 (PO), 강대혁, 김지원 (AWS), 김하나한 (the user), 류민송, 정창민.

| Directory | What it is |
| --- | --- |
| `beadv7_7_earlybird_BE/` | **Team 5's project repo** (github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE) — the 얼리버드 semi-project, on a codebase evolved from the lecture MSA example |
| `beadv7_7_earlybird_config/` | **Team 5's Spring Cloud Config repo** (github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config, private) — per-service `.yml` files with real ports/DB URLs; local clone that config-server can also point `file://` at |

Always `cd` into the relevant repo before running git or Gradle commands — the workspace root itself is not a git repo.

## Main Project: beadv7_7_earlybird_BE

Spring Boot 4.1 / Spring Cloud 2025.1.2 / Java 21, Gradle multi-module microservices. Root `build.gradle` registers plugin versions only (`apply false`); each module applies them itself. Lombok is provided to all modules; `common` shares exceptions (`BusinessException`, `EntityNotFoundException`, `ServiceUnavailableException`, `GlobalExceptionHandler`), `BaseEntity` (JPA auditing — created/updated timestamps), and `ApiResponse` + `ApiResponseWrappingAdvice` (a `@ControllerAdvice` that auto-wraps controller return values, so controllers just return plain DTOs, not `ApiResponse` themselves).

### Modules and ports

Service modules (each with its own port and DB): `order-service` :8080, `project-service` :8081, `payment-service` :8082, `user-service` :8083, `cart-service` :8085, `settlement-service` :8086 (Spring Batch), `file-service` :8087 (unimplemented skeleton), `board-service` :8088 (창작자 공지/의견·문의/리뷰), `notification-service` :8089, `chat-service` :8090 (no DB yet; Spring AI OpenAI starter wired, provider/model TODO). Infrastructure modules: `discovery-server` (Eureka, :8761), `gateway-server` (:8000, single entry point for all external requests), `config-server` (:8888).

`/internal/**` endpoints have no gateway route by design — they're reachable only via direct Eureka-to-Eureka service calls, never from outside.

### Commands

```bash
docker compose -f infrastructure/docker-compose.yml up -d mysql   # MySQL (1 container, 9 per-service schemas)
docker compose -f infrastructure/docker-compose.yml up -d         # + Elasticsearch (nori), for project-service search

./gradlew build                                # build everything
./gradlew :order-service:test                  # tests for one module
./gradlew :order-service:test --tests "OrderServiceTest.메서드명"  # single test
./gradlew :order-service:bootRun               # run one service
```

MySQL must be running before `./gradlew build` — DB-dependent tests (order, settlement) spin up a real MySQL via **Testcontainers**, which needs Docker. This also applies in CI (`.github/workflows/ci.yml` runs `./gradlew build` on every push/PR to `main`, on a Docker-enabled runner). See `docs/1_LOCAL_DB_SETUP.md` for connection details and troubleshooting.

Startup order matters: `config-server` → `discovery-server` → `gateway-server` → business services. Each service's local `application.yml` contains only `spring.application.name` and a config-server import — **actual configuration (ports, DB URLs, etc.) lives in the `beadv7_7_earlybird_config` repo**, fetched by config-server at startup. To change a service's config, edit that repo (or point config-server at a local `file://` path), not the service module. The config repo is **private**, so config-server needs `GIT_USERNAME` / `GIT_PERSONAL_ACCESS_TOKEN` env vars (a personal GitHub PAT, `repo` scope, ≤366-day expiration per org policy) to clone it — see `docs/2_CONFIG_SERVER_SETUP.md` for setup and auth troubleshooting.

`.http` files in the repo root (`orders.http`, `domain-communication.http`, `settlement.http`) are ready-made request collections for manual testing.

### Architecture

Each service follows the same layered layout under `com.growmighty.lectures.firstday.<domain>/`:

- `presentation/` — controllers + request/response DTOs
- `application/` — services; `application/port/` holds **interfaces for calls to other domains** (with their own DTOs)
- `domain/` — entities and domain logic
- `infrastructure/` — port implementations; `infrastructure/client/` contains the HTTP adapters that call other services: a declarative `@FeignClient` interface for the actual call, wrapped by a `*HttpClient` adapter (implements the port interface) that runs the call through a Resilience4j `CircuitBreakerFactory` with a fallback method

Cross-domain references are by **ID, not object** — services never share entities or JPA relationships across domain boundaries. Synchronous queries and commands use the existing port/HTTP-adapter pair; asynchronous domain facts and batch requests use Kafka producer/consumer adapters behind the same application boundary. Settlement's target flow and current implementation state are documented in [`settlement-service/README.md`](settlement-service/README.md). Other design-background docs live under `docs/` (`1_LOCAL_DB_SETUP.md`, `2_CONFIG_SERVER_SETUP.md`, `ERD.md`).

### Code convention: framework/library defaults over custom code

Prefer an existing Spring (or other library) feature over writing a new class, annotation, or abstraction — across every layer, not just security: Bean Validation (`@Valid`/`@NotNull`/...) over hand-rolled checks, `BaseEntity`/JPA auditing over manual timestamp fields, `ResponseEntityExceptionHandler`/`@RestControllerAdvice` over ad-hoc try/catch-and-map, Resilience4j's `CircuitBreakerFactory` over a custom retry/fallback wrapper, Spring Security's `authorizeExchange`/`@PreAuthorize`/`JwtEncoder`/`oauth2-resource-server` over a custom filter or authorization annotation, and so on. This applies even when the default path costs more up front (e.g. a new dependency in a module that didn't have it, or a broader-reaching change like a shared `gateway-server` config instead of scattered per-service code) — that cost alone does not justify a custom implementation.

Before writing any new class for a problem a framework/library commonly solves, name the specific built-in feature checked and ruled out, and why, before proposing custom code. Custom code is justified only for the part genuinely specific to this project that no existing feature covers (e.g. projecting JWT claims into `X-User-Id`/`X-User-Role` headers for downstream services that don't verify JWTs themselves — see `docs/3_JWT_AUTH.md`).

## Working Context

This is a learning environment. The user is here to learn backend development — when working in the lecture/practice repos, prefer explaining, hinting, and reviewing over writing complete solutions unless explicitly asked to implement.
