# 얼리버드 (Earlybird) — Backend

리워드 기반 크라우드펀딩 플랫폼. **All-or-Nothing 펀딩** 모델 — 마감일까지 목표 금액을 달성하면 창작자에게 정산하고, 실패하면 후원자 전원에게 일괄 환불한다.

> 프로그래머스 백엔드 심화 데브코스 7기 · 7회차 세미프로젝트 · **5팀**

## 팀원

| 이름 | 역할 |
| --- | --- |
| 조우진 | PO |
| 강대혁 | 팀원 |
| 김지원 | 팀원 (AWS/인프라) |
| 김하나한 | 팀원 |
| 류민송 | 팀원 |
| 정창민 | 팀원 |

## 아키텍처

Spring Boot 4.1 / Spring Cloud 2025.1.2 / Java 21, Gradle 멀티모듈 MSA.

각 서비스는 동일한 레이어 구조를 따른다:

```
presentation/    컨트롤러 + 요청/응답 DTO
application/     서비스. application/port/ 에 타 도메인 호출용 인터페이스(+DTO)
domain/          엔티티와 도메인 로직
infrastructure/  포트 구현체. client/ 에 RestClient/Feign 기반 HTTP 어댑터
```

**도메인 간 참조는 객체가 아닌 ID로만** 한다 — 서비스끼리 엔티티나 JPA 연관관계를 공유하지 않고, 포트 인터페이스 + HTTP 어댑터를 통해 호출한다. 기능을 추가할 때 이 패턴을 유지할 것. 배경 설명은 [`docs/lecture/`](docs/lecture/)의 강의 세션 노트 참고.

### 모듈 / 포트

| 모듈 | 포트 | 설명 |
| --- | --- | --- |
| config-server | 8888 | 설정 중앙화 — [beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config) 저장소에서 설정을 읽는다 |
| discovery-server | 8761 | Eureka 서비스 디스커버리 |
| gateway-server | 8000 | API 게이트웨이 (모든 외부 요청 진입점) |
| order-service | 8080 | 주문(후원) |
| project-service | 8081 | 펀딩 프로젝트 + 리워드 (Elasticsearch 검색 포함) |
| payment-service | 8082 | 결제 |
| user-service | 8083 | 회원 |
| cart-service | 8085 | 장바구니 |
| settlement-service | 8086 | 정산 (Spring Batch) |
| file-service | 8087 | 파일 (미구현 스켈레톤) |
| board-service | 8088 | 커뮤니티 (공지/의견/리뷰) |
| notification-service | 8089 | 알림 |
| ai-service | 8090 | AI |
| common | — | 공유 모듈 (`ApiResponse`, `BusinessException` 등) |

`/internal/**` API는 게이트웨이에 라우트가 없다 — 서비스 간 Eureka 직접 호출 전용이며 외부에서 접근 불가.

## 실행 방법

### 사전 준비

- Java 21
- Docker (Elasticsearch/Kibana 용 — project-service 검색 기능에만 필요)

### 설정 저장소

각 서비스의 로컬 `application.yml`에는 `spring.application.name`과 config-server 주소만 있다. **포트/DB 등 실제 설정은 [beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config) 저장소에 있고**, config-server가 기동 시 GitHub에서 가져온다. 설정을 바꾸려면 그 저장소를 수정할 것. (로컬 설정 저장소로 테스트하려면 config-server를 `--spring.cloud.config.server.git.uri=file:///경로` 로 실행)

### 기동 순서 (순서 중요!)

```bash
# 0. (검색 기능 쓸 때만) Elasticsearch + Kibana
docker compose -f infrastructure/docker-compose.yml up -d

# 1. 설정 서버
./gradlew :config-server:bootRun

# 2. 디스커버리 (Eureka)
./gradlew :discovery-server:bootRun

# 3. 게이트웨이
./gradlew :gateway-server:bootRun

# 4. 비즈니스 서비스 (필요한 것만, 순서 무관)
./gradlew :project-service:bootRun
./gradlew :user-service:bootRun
# ...
```

기동 확인: Eureka 대시보드 http://localhost:8761 에 서비스가 등록되면 성공. 모든 API 호출은 게이트웨이(http://localhost:8000)를 통한다.

### 빌드 / 테스트

```bash
./gradlew build                                        # 전체 빌드
./gradlew :order-service:test                          # 모듈 테스트
./gradlew :order-service:test --tests "OrderServiceTest.메서드명"  # 단일 테스트
```

### 수동 테스트

`http/` 폴더의 `.http` 파일(`orders.http`, `domain-communication.http`, `settlement.http`, `backer-flow.http`,
`creator-flow.http`)은 IntelliJ HTTP Client로 바로 실행 가능한 요청 모음이다.

`backer-flow.http`는 회원가입 → 로그인 → 프로젝트/리워드 조회 → 후원(주문 생성) → 내 후원 내역 조회 →
후원 취소로 이어지는 실제 후원자 플로우를, `creator-flow.http`는 회원가입 → 로그인 → 창작자 전환 →
토큰 재발급 → 프로젝트 생성 → 리워드 등록 → 내 프로젝트/리워드 조회 → 관리자 심사 승인으로 이어지는
창작자 플로우를 순서대로 실행/검증한다(관리자 로그인은 UserDataInitializer가 시드하는
admin@earlybird.co.kr 계정을 쓴다). 같은 폴더의 `http-client.env.json`이
`local`(`http://localhost:8000`)/`production`(DuckDNS 게이트웨이) 두 환경의 `baseUrl`을 정의해두었으므로,
IntelliJ 우측 상단 환경 드롭다운에서 골라 그대로 재사용하면 된다 — 요청 파일을 환경별로 복제할 필요는 없다.

두 라이브 플로우는 `system-test` 모듈의 JUnit 테스트(`BackerFlowLiveTest`, `CreatorFlowLiveTest`)로도
동일하게 검증할 수 있다 — 로컬/운영 스택이 떠 있는 상태에서 `./gradlew :system-test:liveTest`.

현재 모든 서비스는 인메모리 H2를 쓴다 (`ddl-auto: create`, 재시작 시 초기화). 각 서비스의 `/h2-console` 에서 DB를 볼 수 있다.

## 문서

- `5팀 프로젝트 문서/` (팀 공유 폴더) — 기획서, API 명세서, 회의록
- [`docs/lecture/`](docs/lecture/) — 강사님 세션 노트 (DDD, 도메인 통신, 정산 등 설계 배경)
