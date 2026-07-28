# 프로젝트 모금액(fundedAmount) 주기적 pull 동기화 설계

- 날짜: 2026-07-28
- 담당: 강대혁 (project-service)
- 배경: `Project.fundedAmount`가 항상 0으로 남아있어(결제 성공해도 반영되는 트리거가 없음), 목표 달성 판정(`closeByDeadline`)과 조기 종료(`closeEarlyAsSucceeded`)가 실질적으로 항상 실패한다.

## 경위

- 이 문제를 해결하는 project-service 쪽 구현이 이미 한 번 작성됐었다(PR #100, 2026-07-27, 커밋 `178d977`/`fbc6a0c`). push(PUT 수신 엔드포인트) + pull(GET 조회 클라이언트) 이중화 설계였고, 낙관적 락 재시도까지 포함되어 있었다.
- 하지만 이 PR은 `develop`이 아니라 `강대혁/project/order-existence-dip` 브랜치로 머지됐고, 그 브랜치가 (먼저 PR #64로 develop에 한 번 머지된 뒤) 후속 PR 없이 방치되어 **develop에는 반영된 적이 없다.**
- order-service 쪽은 pull에 응답할 `GET /internal/v1/orders/{projectId}/funded-amount`가 `류민송/order/authority` 브랜치에 이미 정확하게 구현되어 있다(PAID 주문만 합산). 하지만 이 브랜치의 PR #117이 다른 기능(주문 API 권한 검증)과 묶인 채 2026-07-28에 머지 없이 CLOSE 되어, 역시 develop에는 없다.
- push(PUT 발신)는 order-service 어디에도 구현된 적이 없다.

## 요구사항

- project-service가 `IN_PROGRESS` 상태 프로젝트의 `fundedAmount`를, order-service의 실제 결제 확정(PAID) 누적 총액과 최대 1분 이내로 일치시킨다.
- 방식은 **pull(주기 조회)만** 사용한다. push는 이번 범위에서 제외한다 — push는 order-service 쪽에 결제확정/취소 시점마다 project-service를 호출하는 신규 발신 코드가 필요한데, 그건 project-service 스코프 밖이라 이번 작업으로 만들 수 없다. pull만으로도 목표 요구사항(1분 이내 일치)을 충족한다.
- 아직 후원이 하나도 없는 프로젝트는 order-service가 조회 결과 없음(빈 값)을 돌려준다 — 이 경우 0원으로 처리한다.
- order-service 호출이 실패해도(서비스 다운 등) 다른 프로젝트의 갱신에 영향을 주면 안 된다.

## 아키텍처

기존 PR #100의 pull 관련 부분(`OrderPort`/`OrderFeignClient`/`OrderHttpClient`, `Project.updateFundedAmount`, 낙관적 락 재시도)을 cherry-pick으로 복구하고, 그 위에 실제로 이 클라이언트를 호출하는 `FundedAmountReconciliationScheduler`를 새로 추가한다. push 관련 부분(PUT 수신 엔드포인트, `FundedAmountUpdateRequest`, 순서보장 필드)은 복구하지 않는다 — 호출할 발신자가 없는 코드를 미리 만들어두지 않는다(YAGNI).

`ProjectDeadlineScheduler`와 동일한 패턴(`@Scheduled` + Asia/Seoul)을 따른다.

## 컴포넌트 변경

**복구 (cherry-pick `178d977`, `fbc6a0c`에서 pull 관련 부분만 선별 적용)**

`project-service/.../project/domain/Project.java`
- `updateFundedAmount(BigDecimal fundedAmount)`: 절대값 덮어쓰기, `fundedAmount < 0`이면 `IllegalArgumentException`. `null`은 호출자(서비스 레이어)가 0으로 치환해서 넘기므로 여기서는 다루지 않는다.

`project-service/.../project/application/port/OrderPort.java`
- `BigDecimal getFundedAmount(Long projectId)` 추가 (기존 `hasOrderedReward`와 나란히)

`project-service/.../project/infrastructure/client/OrderFeignClient.java`, `OrderHttpClient.java`
- `GET /internal/v1/orders/{projectId}/funded-amount` 호출. 응답 `data()`가 `null`이면(무후원 프로젝트) `BigDecimal.ZERO`로 치환.
- 서킷브레이커 open/타임아웃 시 `ServiceUnavailableException` — fail-closed(값을 함부로 0으로 덮어쓰지 않고 이번 사이클을 건너뛴다).

`project-service/.../project/application/ProjectService.java`, `ProjectServiceImpl.java`
- `void updateFundedAmount(Long projectId, BigDecimal fundedAmount)` — `@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))` + `@Recover`로 낙관적 락 충돌을 `ConcurrentUpdateFailedException`(409)으로 변환. `cancel`/`extendDeadline`/`closeEarlyAsSucceeded`/`closeByDeadline`과 같은 패턴.

**신규 추가**

`project-service/.../project/application/FundedAmountReconciliationScheduler.java`
```java
@Component
@RequiredArgsConstructor
public class FundedAmountReconciliationScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedRate = 60 * 1000)
    public void reconcile() {
        projectService.reconcileFundedAmounts();
    }
}
```

`ProjectService`에 `void reconcileFundedAmounts()` 추가, `ProjectServiceImpl`에서 구현:
- `projectRepository.findByStatus(IN_PROGRESS)`로 대상 조회
- 각 프로젝트마다 `orderPort.getFundedAmount(projectId)` 호출 → `updateFundedAmount(projectId, amount)`
- 한 프로젝트에서 `ServiceUnavailableException`이 나면 `WARN` 로그(projectId, 원인) 남기고 다음 프로젝트로 계속 — 예외를 전파해서 전체 배치를 중단시키지 않는다.

## 에러 처리

- order-service 응답 없음/타임아웃: 해당 프로젝트만 스킵, 다음 1분 주기에 재시도. 기존 값 유지(0으로 덮어쓰지 않음).
- 무후원 프로젝트(`Optional.empty()` → `null`): `BigDecimal.ZERO`로 정상 처리 (에러 아님).
- 낙관적 락 충돌(다른 mutator와 동시 수정): 최대 3회 재시도 후에도 실패하면 `ConcurrentUpdateFailedException` — 스케줄러 루프에서는 이것도 다른 예외와 동일하게 WARN 로그 후 다음 프로젝트로 계속.

## 테스트

- `ProjectTest`: `updateFundedAmount()` 정상/음수 거부 (PR #100 원본 케이스 복구)
- `ProjectServiceImplFundedAmountTest`: 정상 갱신, 낙관적 락 충돌 시 재시도/복구 (PR #100 원본 케이스 복구)
- `OrderHttpClientTest`: 정상 조회, `null` 응답 → `ZERO` 치환, 서킷브레이커 open 시 `ServiceUnavailableException` (신규 케이스 추가)
- `FundedAmountReconciliationScheduler`/`ProjectServiceImpl.reconcileFundedAmounts()`: 여러 프로젝트 중 하나가 실패해도 나머지는 갱신되는지 (신규)

## 외부 의존성 (블로커)

- order-service의 `GET /internal/v1/orders/{projectId}/funded-amount`가 develop에 없다(PR #117 closed). 이 작업은 그 API가 없어도 완결적으로 구현/테스트 가능하지만(호출은 항상 503 fallback을 타고 스킵됨), **실제로 모금액이 채워지려면 류민송 님이 이 엔드포인트를 별도 PR로 develop에 올려야 한다.** 이 스펙 문서를 그대로 전달용으로 쓸 수 있다.

## 범위 밖

- push(PUT 수신, 순서보장 필드)는 다음 단계로 미룬다. order-service가 실시간 발신 코드를 만들 준비가 되면 별도 스펙으로 다시 브레인스토밍한다.
- order-service 쪽 GET 엔드포인트 자체의 구현/PR 처리는 이 스펙의 범위 밖이다(팀 간 조율 사항으로 별도 전달).
