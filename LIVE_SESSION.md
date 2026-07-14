# DDD 라이브 세션 — 기본 코드 (Before)

이 프로젝트는 라이브 세션의 **출발점(Before)** 입니다.
도메인(상품/장바구니/결제/주문/유저/셀러)이 의도적으로 **직접 객체 참조로 뒤엉켜** 있고,
주문 로직은 **트랜잭션 스크립트**로 `OrderService` 한 곳에 몰려 있습니다.
여기서부터 코드를 추가/변경하며 리팩토링 과정을 보여줍니다.

## 실행 / 확인

```bash
./gradlew bootRun          # 부팅 시 시드 데이터(구매자/셀러/상품/장바구니) 자동 생성
curl -X POST "http://localhost:8080/orders?userId=1"   # 첫 주문 성공 → {"id":1}
curl -X POST "http://localhost:8080/orders?userId=1"   # 장바구니 비어 있음 → 실패
```

- H2 콘솔: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:tangled`)
- 시드: `DataInitializer` (테스트 슬라이스에는 로딩되지 않음)

## 현재 "뒤엉킨" 연관 관계 (직접 참조)

```
Seller ──@OneToOne──▶ User
Seller ──@OneToMany─▶ Product
Product ─@ManyToOne─▶ Seller
Cart ───@OneToOne──▶ User
CartItem @OneToOne──▶ Product
Order ──@OneToOne──▶ Payment
OrderItem @ManyToOne▶ Product   ← [상품 ↔ 주문] 강사가 먼저 시연할 경계
```

각 엔티티의 도메인 경계를 넘는 참조에는 `TODO[DDD]` 주석으로 "끊어낼 지점"을 표시해 두었습니다.

## 세션 주제 → 코드 매핑

| 세션 주제 | 출발 코드 | 진행 방향 |
|---|---|---|
| 연관 관계 끊기 (간접 참조 ID) | `OrderItem.product`, `CartItem.product` 등 직접 참조 | 강사: 상품↔주문을 `productId(Long)` 로 전환 → 나머지는 실습 |
| DDD 4계층 + DIP | 한 패키지에 controller/service/entity/repo 혼재 | 주문 도메인을 application/domain/infra/presentation 으로 분리 |
| 애그리거트/캡슐화/불변식 | `@Setter` 로 열린 `Product.stockQuantity`, `Payment.status`, `Order.totalAmount` | setter 제거 + `decreaseStock()`, `pay()`, 합계 계산 등 도메인 메서드로 캡슐화 |
| 스크립트→도메인 모델 | `OrderService.placeOrder()` (뚱뚱한 트랜잭션 스크립트) | 재고차감/합계/결제/상태전이 로직을 Entity 로 이동 |
| 도메인 간 소통 | 현재는 객체를 직접 타고 들어가 호출 | Facade vs API/이벤트 비교 시연 |

## 핵심 "냄새" 위치

- `order/OrderService.java` — 재고 검증·차감, 합계 계산, 결제 생성·승인, 상태 전이가 모두 절차적으로 모여 있음
- `product/Product.java` — `@Setter` 로 재고가 외부에 노출됨 (불변식 깨짐)
- `payment/Payment.java` — 상태 전이를 외부(서비스)가 setter 로 수행
- `order/Order.java` — 합계/상태를 서비스가 계산해서 주입
