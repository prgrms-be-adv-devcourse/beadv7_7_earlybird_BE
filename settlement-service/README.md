# Settlement Service

프로젝트 결과에 따라 성공 프로젝트의 **프로젝트 정산과 창작자 지급**을 관리하고, 실패·취소 프로젝트의 **결제 취소**를 Payment에 요청한다.

Settlement는 다른 서비스의 판단을 대신하지 않는다.

- Project: `projectId`, `creatorId`, 결과(`SUCCEEDED`, `FAILED`, `CANCELLED`)
- Order: 프로젝트별 `orders(orderId, paymentAmount)`
- Payment: 실패·취소 주문의 전액 환불 필요 여부와 처리 결과
- Settlement: 성공 프로젝트의 금액 계산·확정, 지급 의무·지급 시도, 결제 취소 명령

## 처리 흐름

```mermaid
sequenceDiagram
    participant S as Settlement
    participant P as Project
    participant O as Order
    participant Pay as Payment
    participant PG as Dummy payout

    S->>P: 결과별 프로젝트 조회
    P-->>S: projectId, creatorId, status
    S->>O: 프로젝트별 주문 조회
    O-->>S: orders(orderId, paymentAmount)
    alt SUCCEEDED
        S->>S: 프로젝트 정산과 지급 의무 확정
        S->>PG: 예약 지급 요청
    else FAILED or CANCELLED
        S->>Pay: orderId별 전액 환불 요청
    end
```

성공 정산 금액은 Payment가 아니라 Order의 주문 결제금액에서 계산한다. 현행 정책은 결제·정산 대행 수수료 4%, 플랫폼 수수료 4%, 각 수수료의 부가세 10%이며 공제액은 원 미만을 버린다.

재실행 시 이미 확정된 프로젝트 정산과 완료된 결제 취소를 재사용한다. 기존 결과와 새 Project 결과가 충돌하면 저장된 원본을 바꾸지 않고 `OUTCOME_CONFLICT`로 반환한다.

## 현재 구현 범위

- Project·Order·Payment 연동은 HTTP adapter와 테스트용 dummy adapter를 제공한다.
- 지급대행은 항상 결정적 dummy adapter를 사용하며 실제 송금은 발생하지 않는다.
- Payment adapter는 주문별 단건 환불 happy case를 지원한다. 제공자 멱등키, batch 요청, 부분 실패 복구, 실제 서비스 간 E2E는 아직 지원하지 않는다.
- 정산 실행은 현재 관리자용 동기 HTTP 요청이다. Kafka 입력 수집과 Spring Batch 월 정산은 후속 범위다.

## 실행과 테스트

Java 21과 Docker가 필요하다. 저장소 루트에서 테스트한다. DB 통합 테스트는 Testcontainers MySQL을 사용한다.

```shell
./gradlew :settlement-service:test
```

애플리케이션 실행에는 Config Server, Eureka, MySQL이 필요하다. 운영 adapter를 사용할 때는 Project, Order, Payment도 실행한다. 전체 기동 순서는 [루트 README](../README.md)를 따른다.

```shell
./gradlew :settlement-service:bootRun
```

외부 서비스 없이 입력 adapter를 확인하려면 Project·Order·Payment만 dummy로 바꿀 수 있다. DB와 공통 인프라는 여전히 필요하다.

```shell
./gradlew :settlement-service:bootRun --args='--settlement.project-target.mode=dummy --settlement.project-order.mode=dummy --settlement.payment-cancellation.mode=dummy'
```

dummy 입력은 성공 프로젝트 1건과 100,000원 주문 1건을 제공한다. 지급 결과의 기본값은 `COMPLETED`다.

## API

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| `POST` | `/internal/v1/settlements/runs` | `ADMIN` | 프로젝트 결과를 조회해 정산 또는 결제 취소 실행 |
| `GET` | `/api/v1/settlements` | `CREATOR` | 로그인한 창작자의 프로젝트 정산 내역 목록 |
| `GET` | `/api/v1/settlements/{settlementId}` | `CREATOR` | 로그인한 창작자의 프로젝트 정산 상세 |
| `GET` | `/api/v1/settlements/all` | `ADMIN` | 전체 프로젝트 정산 내역 목록 |
| `GET` | `/api/v1/settlements/all/{settlementId}` | `ADMIN` | 프로젝트 정산 상세와 지급 시도 목록 |

인증·인가는 Gateway가 담당하고 Settlement는 전달된 사용자 식별자를 신뢰한다. 월 실행 요청은 내부 API다.

```http
POST /internal/v1/settlements/runs
Content-Type: application/json

{
  "settlementMonth": "2026-07"
}
```

외부 조회 API는 게이트웨이 `http://localhost:8000`을 통해 호출한다. Swagger UI는 전체 스택 실행 후 `http://localhost:8000/settlement-service/swagger-ui.html`에서 확인할 수 있다.

## 설정

| 속성 | 기본값 | 설명 |
|---|---|---|
| `settlement.project-target.mode` | `http` | `http` 또는 `dummy` |
| `settlement.project-target.http.base-url` | `http://project-service` | Project 서비스 주소 |
| `settlement.project-order.mode` | `http` | `http` 또는 `dummy` |
| `settlement.project-order.http.base-url` | `http://order-service` | Order 서비스 주소 |
| `settlement.payment-cancellation.mode` | `http` | `http` 또는 `dummy` |
| `settlement.payment-cancellation.http.base-url` | `http://payment-service` | Payment 서비스 주소 |
| `settlement.external-data.mode` | `dummy` | dummy 창작자 지급 프로필 초기화 여부 |
| `settlement.dummy-payout.scenario` | `COMPLETED` | dummy 지급 결과 시나리오 |

세 HTTP adapter의 기본 연결 제한시간은 1초, 응답 제한시간은 3초다. 지급 시나리오는 `COMPLETED`, `REQUESTED`, `IN_PROGRESS`, `RETRYABLE_FAILED`, `NON_RETRYABLE_FAILED`, `UNKNOWN`을 지원한다.

Config Server 설정은 이 모듈의 요청 범위가 아니므로 여기서 변경하지 않는다.

## 패키지 구조

```text
settlement
├── presentation     HTTP API, DTO, 오류 응답
├── application      유스케이스와 외부 통신 port
├── domain           도메인 모델과 repository interface
└── infrastructure   HTTP·dummy adapter, JPA 구현, 기술 설정
```

의존 방향은 `presentation → application → domain`이며 infrastructure가 application port와 domain repository를 구현한다.
