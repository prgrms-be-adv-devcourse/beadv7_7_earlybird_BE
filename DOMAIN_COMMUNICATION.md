# 도메인 간 소통 — Facade vs API 호출

> 같은 "주문 생성" use-case 를 **두 가지 방식**으로 구현해 나란히 비교하는 라이브 데모입니다.
> 베이스라인(현재 `OrderService`)은 다른 도메인의 서비스 빈을 직접 호출합니다. 여기서 출발해 두 방향을 봅니다.

## 한눈에 보기

| 구분 | **방식 1. Facade** | **방식 2. API 호출(HTTP)** |
|---|---|---|
| 진입점 | `POST /orders/facade` | `POST /orders/api` |
| 구현 | [`OrderFacade`](src/main/java/com/growmighty/lectures/firstday/tangledmonolith/order/application/OrderFacade.java) | [`OrderApiService`](src/main/java/com/growmighty/lectures/firstday/tangledmonolith/order/application/OrderApiService.java) |
| 주입받는 것 | 상대 도메인의 **Service 빈** (`ProductService`, `PaymentService`, `UserService`) | 주문이 정의한 **Port** (`ProductPort`, `PaymentPort`) |
| 상대 타입 의존 | 상대 DTO(`ProductInfo`, `PaymentInfo`)를 그대로 import | 주문 소유 타입(`ProductSnapshot`, `PaymentResult`)으로 번역 |
| 호출 방식 | 같은 JVM, 직접 메서드 호출(동기) | RestClient 로 HTTP 요청(동기) |
| 트랜잭션 | **단일 트랜잭션** → 강한 일관성, 실패 시 전체 롤백 | 호출마다 **별도 트랜잭션** → 즉시 커밋, 부분 실패 가능 |
| 결합/배포 | 컴파일 타임 결합, 하나의 배포 단위 | 계약(JSON)만 공유, **독립 배포 가능** |
| 적합한 때 | 모놀리식 안에서 use-case 흐름 정리 | 도메인 자율성 확보 / 서비스 분리 대비 |

**핵심 한 줄**: Facade 는 *한 프로세스 안에서 흐름을 한 곳에 모으는 것*, API 호출은 *경계를 진짜로 긋고 계약으로만 대화하게 만드는 것*. — 결합을 **정리**하느냐 **끊느냐**의 차이.

## 방식 1 — Facade

```
OrderFacade.placeOrder()              ← 교차 도메인 흐름 조율을 전담
   ├─ userService.getUser(...)        (직접 빈 호출)
   ├─ productService.getProductInfo() / decreaseStock()
   ├─ paymentService.pay(...)
   └─ orderRepository.save(order)
   └─ @Transactional  ── 한 트랜잭션 안에서 전부. 결제 실패 시 재고 차감까지 자동 롤백.
```

- 장점: 단순·직관적, 강한 일관성, 디버깅 쉬움. 모놀리식에서 가장 실용적.
- 한계: 주문이 상품/결제의 **내부 서비스와 타입을 컴파일 타임에 알고** 있음 → 변경 전파, 분리 어려움.

## 방식 2 — API 호출(HTTP)

```
OrderApiService.placeOrder()          ← 오직 Port(계약)만 안다
   ├─ ProductPort  ──impl──▶ ProductHttpClient ──HTTP──▶ GET  /products/{id}
   │                                              └─HTTP──▶ POST /products/{id}/decrease-stock
   └─ PaymentPort  ──impl──▶ PaymentHttpClient ──HTTP──▶ POST /payments
   └─ orderRepository.save(order)      ← 여기서 실패하면? 재고·결제는 이미 커밋됨 → 보상 필요
```

- 의존성 역전(DIP) + 부패 방지 계층(ACL): 주문 코드에는 product/payment 패키지 import 가 **0개**.
  외부 JSON 은 어댑터(`order/infrastructure/client`)에서 주문 소유 타입으로 **번역**된다.
- `order.client.base-url` 만 `http://product-service` 로 바꾸면 그대로 **MSA 호출**이 된다.
- 대신 **전역 트랜잭션이 사라진다.** 재고 차감·결제는 각자 별도 트랜잭션으로 즉시 커밋되므로,
  주문 저장이 실패하면 정합성이 깨진다 → **보상 트랜잭션(사가)** 또는 **이벤트 기반** 정합성이 필요.

## 강의용 흐름 제안

1. 현재 `OrderService`(베이스라인)에서 "주문이 4개 도메인 서비스를 직접 알고 있다" 는 결합을 짚는다.
2. `OrderFacade` 로 흐름 조율을 한 곳에 모으되, **여전히 컴파일 타임 결합**임을 강조한다.
3. `OrderApiService` 로 넘어가 import 목록을 비교 → "product/payment 가 사라졌다" 를 보여준다.
4. `decrease-stock` 만 성공하고 결제에서 실패하는 시나리오로 **트랜잭션 경계 차이**(롤백 vs 보상)를 체감시킨다.
5. 마무리: "그럼 동기 HTTP 의 강결합·장애 전파는? → **이벤트/메시지(비동기)** 로 이어진다" 로 다음 주제 예고.

## 실행 / 비교

```bash
./gradlew bootRun
# 베이스라인(직접 호출)
curl -X POST localhost:8080/orders        -H 'Content-Type: application/json' -d '{"userId":1,"requests":[{"productId":1,"quantity":1}]}'
# 방식 1: Facade
curl -X POST localhost:8080/orders/facade -H 'Content-Type: application/json' -d '{"userId":1,"requests":[{"productId":1,"quantity":1}]}'
# 방식 2: API/HTTP
curl -X POST localhost:8080/orders/api    -H 'Content-Type: application/json' -d '{"userId":1,"requests":[{"productId":1,"quantity":1}]}'
```

요청 모양과 결과(JSON)는 셋 다 동일하다. **다른 것은 "주문이 어떻게 다른 도메인과 소통했는가"** 뿐이다.
세 호출의 코드를 나란히 펼쳐 결합도 차이를 보여주는 것이 이 데모의 목적이다.
