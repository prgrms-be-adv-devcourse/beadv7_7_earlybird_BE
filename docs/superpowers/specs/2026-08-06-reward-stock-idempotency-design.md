# Reward 재고 변경 API 멱등성 설계

## 배경

[GitHub #195](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/195) — `RewardInternalController`의
`POST /internal/v1/rewards/{rewardId}/decrease-stock` / `restore-stock`은 `reward.decreaseStock(quantity)` /
`reward.restoreStock(quantity)`로 단순 증감만 수행하고, 같은 요청이 두 번 도착했는지 판별할 방법이 없다.

`@Version` 낙관적 락 + `@Retryable`(reward-stock-load-test 브랜치의 k6 부하테스트로 검증됨,
[2026-08-03 결과 문서](./2026-08-03-reward-stock-k6-load-test-results.md) 참고)은 "동시에 여러 요청이 충돌"하는
문제(lost update)를 막을 뿐, "같은 요청이 네트워크 타임아웃 등으로 중복 도착"하는 문제와는 별개라 막아주지 못한다.

호출측인 order-service의 `RewardFeignClient`는 별도 `HttpClient`/`CircuitBreakerFactory` 래핑 없이 Feign 인터페이스
default 메서드로 바로 포트를 구현하고 있어, Feign의 기본 `Retryer`가 `IOException`(연결 실패·타임아웃)에 자동
재시도한다 — 애플리케이션 레벨 재시도 코드가 전혀 없어도, 응답이 클라이언트에 도달하기 전에 끊기기만 하면
project-service는 이미 처리를 마쳤는데 order-service는 실패로 착각해 같은 요청을 다시 보낼 수 있다.

## 목표

`decrease-stock` / `restore-stock`에 멱등성을 보장해, 같은 논리적 요청이 여러 번 도착해도 재고 변경은 정확히
한 번만 반영되도록 한다.

## 스코프

**project-service만** 다룬다. order-service가 실제로 멱등키(`orderId`)를 실어 보내도록 고치는 작업은 담당자가
다른 서비스(강대혁 소유 아님)라 이번 범위 밖이며, 별도 GitHub 이슈로 넘긴다.

이 스코프 결정의 함의를 명확히 해둔다: **`orderId`를 캐치(caller가 보내는 상관관계 식별자) 없이 멱등성을
보장하는 것은 원천적으로 불가능하다.** `rewardId`+`quantity`만으로 중복을 판별하려 하면, 서로 다른 두 주문이
우연히 같은 리워드를 같은 수량으로 주문한 정상 케이스(인기 리워드일수록 흔함)를 중복으로 오인해 재고를 누락
차감하는 실제 버그가 된다. 따라서 이번 작업은 **project-service 쪽 배선(수신·판별 로직)을 미리 구현**해두는
것이고, order-service가 후속 작업으로 `orderId`를 실어 보내기 전까지는 실질 효과가 없다 — board-service의
`getCreator` 계약을 project-service 구현 전에 미리 넣어둔 것과 같은 성격의 선행 작업이다.

## 멱등키

`(orderId, rewardId, operation)` 3개 조합.

- `orderId`+`rewardId`만으로는 부족하다 — 같은 조합이 정상적으로 두 번 발생할 수 있다(주문 시 차감 →
  이후 취소 시 복원은 같은 `orderId`+`rewardId`에 대한 별개의 정당한 이벤트).
- `operation`(`DECREASE`/`RESTORE`)까지 포함해야 차감 로그와 복원 로그가 서로 충돌하지 않는다.

## 컴포넌트

### `StockChangeLog` 엔티티 (신규)

`project-service` 자기 스키마에 속한 신규 테이블. `orderId`는 order-service의 `orders` 테이블을 참조하는 게
아니라 order-service가 보낸 값을 저장하는 단순 `Long` 컬럼이다 — `Reward.projectId`, order-service
`OrderItem.rewardId`와 동일한 "ID로만 참조" 관례를 따른다.

```java
@Entity
@Table(name = "stock_change_logs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "reward_id", "operation"}))
public class StockChangeLog extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockChangeOperation operation; // DECREASE, RESTORE
}
```

`StockChangeOperation`(`DECREASE`, `RESTORE`)은 `reward` 패키지 안의 단순 enum. `StockChangeLogRepository`는
기존 `RewardRepository`와 동일하게 `JpaRepository<StockChangeLog, Long>`만 상속하는 표준 Spring Data
리포지토리 — 커스텀 쿼리 메서드는 필요 없다(저장만 하고, 조회는 유니크 제약 위반 예외로 판별하므로).

### `StockChangeRequest` DTO 변경

```java
public record StockChangeRequest(
    @NotNull @Positive Integer quantity,
    Long orderId  // nullable — order-service가 이 필드를 채워 보내기 전까지는 항상 null
) {}
```

`orderId`가 없으면(`null`) 멱등성 체크를 건너뛰고 오늘과 동일하게 바로 적용한다 — 하위 호환.

### `RewardServiceImpl.decreaseStock` / `restoreStock` 변경

기존 `@Retryable` + `@Transactional`은 그대로 유지하고, 메서드 안에 멱등성 체크를 추가한다.

```java
@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 50), recover = "recoverDecreaseStockConflict")
@Transactional
public void decreaseStock(Long rewardId, int quantity, Long orderId) {
    if (orderId != null) {
        try {
            stockChangeLogRepository.save(new StockChangeLog(orderId, rewardId, StockChangeOperation.DECREASE));
        } catch (DataIntegrityViolationException e) {
            return; // 이미 처리된 요청 — 조용히 종료(200 no-op)
        }
    }
    Reward reward = getRewardEntity(rewardId);
    findProjectStatus(reward.getProjectId())
        .filter(ProjectStatusView::open)
        .orElseThrow(() -> new IllegalStateException(...));
    reward.decreaseStock(quantity);
}
```

`restoreStock`도 대칭으로 동일한 패턴을 적용한다.

## 데이터 흐름

```
1. orderId == null → 멱등성 체크 건너뜀 → 기존 로직 그대로 (하위 호환 경로)
2. orderId != null:
   a. StockChangeLog INSERT 시도 (같은 트랜잭션 안)
      → 유니크 제약 위반 시 DataIntegrityViolationException
   b. 예외 발생 → catch → 즉시 return (이미 처리된 요청, 200 no-op)
   c. 예외 없음 → 최초 요청 → Reward.decreaseStock/restoreStock 진행
3. 커밋 시 로그 INSERT + 재고 변경이 원자적으로 함께 반영된다.
   낙관적 락 충돌(ObjectOptimisticLockingFailureException) 시 트랜잭션 전체(로그 포함)가
   롤백되고 @Retryable이 재시도 — 재시도마다 로그 INSERT부터 다시 수행되므로 일관성이 깨지지 않는다.
```

## 왜 "선-INSERT 후 예외 포착"인가 (검토한 대안)

- **조회 후 분기(`existsBy` 먼저 SELECT)**: 두 중복 요청이 동시에 도착하면 둘 다 "존재 안 함"을 보고
  통과하는 TOCTOU 레이스가 있어, 결국 DB 유니크 제약을 백스톱으로 또 둬야 한다 — 불필요한 사전 조회만
  얹는 셈이라 기각.
- **멱등키 기록을 별도 트랜잭션(`REQUIRES_NEW`)으로 먼저 커밋**: 로그 커밋과 재고 변경의 원자성이
  깨진다. 재고 변경이 "진짜" 사유(재고 부족 등)로 실패해도 로그는 이미 커밋돼버려서, 이후 정당한 재시도가
  와도 "이미 처리됨"으로 잘못 응답하게 된다 — 기각.

## 에러 처리

- `DataIntegrityViolationException`(유니크 제약 위반) → 메서드 내부에서 즉시 catch, 정상 반환. 기존
  `@Recover`(`recoverDecreaseStockConflict`/`recoverRestoreStockConflict`)는
  `ObjectOptimisticLockingFailureException` 전용이라 이 예외와는 무관 — 완전히 별개 경로이며 기존
  재시도/복구 로직에 영향 없음.
- `orderId == null`인 기존 호출 경로는 시그니처·동작이 그대로라 회귀 없음.

## 테스트

- `RewardServiceImplTest` 단위 테스트 추가:
  - 같은 `(orderId, rewardId, DECREASE)`로 2번 호출 → `remainingQuantity`는 1번만 줄어들고 예외 없음
  - `orderId == null` → 기존 동작 그대로 적용(회귀 방지)
  - 같은 `(orderId, rewardId)`에 대해 decrease 후 restore → 둘 다 정상 적용(별개 `operation`이라 충돌 안 함)
- `RewardConcurrencyIntegrationTest`와 같은 패턴(진짜 MySQL/Testcontainers, 진짜 스레드)으로 동시성 테스트
  추가: 같은 `(orderId, rewardId, DECREASE)`로 N개 스레드가 동시에 `decreaseStock` 호출 → 재고는 정확히
  1번만 차감됨을 최종 DB 상태로 검증.

## 범위 밖

- order-service가 `orderId`를 실제로 실어 보내도록 고치는 작업(`StockChangeBody`, `RewardFeignClient`,
  `OrderApiService.reserveStock`/`releaseStock`) — 별도 GitHub 이슈로 분리, 이번 스코프 밖.
- Feign 기본 `Retryer` 정책 자체를 손보는 것(예: `CircuitBreakerFactory`로 감싸는 리팩토링) — order-service
  담당 영역이라 이번 범위 밖.
- `StockChangeLog` 테이블의 보관 기간/정리(retention) 정책 — 지금 규모에서는 불필요, 필요해지면 별도 논의.
