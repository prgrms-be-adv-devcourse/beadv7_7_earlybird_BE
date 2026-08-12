# 프로젝트 모금액(fundedAmount) push 수신 엔드포인트 복구 + pull 주기 완화

- 날짜: 2026-08-12
- 담당: 강대혁 (project-service)
- 배경: [`2026-07-28-funded-amount-pull-sync-design.md`](2026-07-28-funded-amount-pull-sync-design.md)에서 push 수신 엔드포인트 구현을 "order-service가 발신 코드를 완성하면 빠른 후속 작업으로 처리"하도록 범위 밖으로 미뤄뒀다. PR #301(류민송/order/fundedamount-push)에서 order-service가 결제 확정(PAID)/취소 시 project-service로 `PUT .../funded-amount`를 호출하는 발신 코드를 실제로 추가했으므로, 이제 project-service 쪽 수신 엔드포인트를 복구할 차례다.

## 경위

- push 수신 엔드포인트, 요청 DTO, 서비스 테스트는 2026-07-27 PR #100(커밋 `178d977`/`fbc6a0c`)에서 이미 한 번 구현·리뷰됐다. 이 PR은 `develop`이 아니라 `강대혁/project/order-existence-dip` 브랜치로 머지됐고 그 브랜치가 방치되어 develop에는 반영된 적이 없다.
- 7/28 설계에서는 pull 관련 부분(`OrderPort`/`OrderFeignClient`/`OrderHttpClient`, `Project.updateFundedAmount`, `ProjectService.updateFundedAmount`의 낙관적 락 재시도, `FundedAmountReconciliationScheduler`)만 cherry-pick으로 복구했다 — push를 호출할 발신자가 없어 데드코드였기 때문(YAGNI).
- PR #301로 발신자가 생겼으므로, 이번엔 push 수신 부분(컨트롤러 엔드포인트 + DTO + 테스트)만 `178d977`에서 복구하면 된다. 서비스 레이어(`ProjectServiceImpl.updateFundedAmount`)는 이미 develop에 있어 그대로 재사용.

## 발견한 이슈: 경로 불일치 (order-service 팀 조율 필요)

PR #301의 `ProjectFundedAmountFeignClient`가 호출하는 경로가 `PUT /internal/v1/project/{projectId}/funded-amount`(단수 `project`)로 되어 있다. project-service의 기존 컨벤션(`ProjectInternalController`가 `@RequestMapping("/internal/v1/projects")`, 복수형)과 7/28에 이미 합의한 계약 둘 다 복수형이다. Request body(`{fundedAmount}`)는 계약과 동일해 문제없음.

project-service는 계약대로 복수형 경로만 연다 — order-service 쪽 Feign 경로에 `s`를 추가해야 실제로 연결된다. 류민송 님께 전달 필요(팀 조율 사항, 이 스펙 범위 밖).

## 요구사항

- order-service가 결제 확정/취소 시 push로 보내는 `fundedAmount`를 project-service가 받아 즉시 반영한다(거의 실시간).
- push가 primary 경로가 되고, 기존 1분 주기 pull(`FundedAmountReconciliationScheduler`)은 push 유실 시를 대비한 안전망(백스톱)으로 역할이 바뀐다 — 실시간성을 push가 담당하므로 pull 주기를 늘려도 안전하다.
- 판정 순간(`closeByDeadline`, `closeEarly`)의 동기 pull(판정 직전 order-service 재조회)은 push 도입과 무관하게 그대로 유지한다 — 이 동기 호출은 원래도 1분 캐시를 신뢰하지 않기 위한 것이었고, 그 원칙은 push가 생겨도 바뀌지 않는다(judgement 순간엔 여전히 최신값을 보장해야 함).

## 컴포넌트 변경

**복구 (cherry-pick `178d977`에서 push 관련 부분만)**

`project-service/.../project/presentation/ProjectInternalController.java`
```java
/**
 * order-service가 결제 확정/취소 시 호출(push)한다 — 절대값 덮어쓰기라 멱등적.
 * project-service의 주기적 pull(OrderPort.getFundedAmount)은 이 push가 유실됐을 때의 안전망이다.
 */
@PutMapping("/{projectId}/funded-amount")
public Void updateFundedAmount(@PathVariable Long projectId, @Valid @RequestBody FundedAmountUpdateRequest request) {
    projectService.updateFundedAmount(projectId, request.fundedAmount());
    return null;
}
```
최종 경로: `PUT /internal/v1/projects/{projectId}/funded-amount` (클래스 레벨 `@RequestMapping("/internal/v1/projects")` + 메서드 매핑).

`project-service/.../project/presentation/dto/request/FundedAmountUpdateRequest.java` (신규)
```java
/** PUT /internal/v1/projects/{projectId}/funded-amount — 절대값(누적 총액) 덮어쓰기, 멱등. */
public record FundedAmountUpdateRequest(
        @NotNull @PositiveOrZero BigDecimal fundedAmount
) {
}
```

`project-service/src/test/.../application/ProjectServiceImplFundedAmountTest.java` (신규, `178d977` 원본 그대로)
- 전달받은 절대값으로 모금액 갱신
- 존재하지 않는 프로젝트면 `EntityNotFoundException`

서비스 레이어(`ProjectServiceImpl.updateFundedAmount`, `@Retryable`+`@Recover` 낙관적 락 처리)는 이미 develop에 있어 변경 없음.

**수정**

`project-service/.../project/application/FundedAmountReconciliationScheduler.java`
- `@Scheduled(fixedRate = 60 * 1000)` → `@Scheduled(fixedRate = 60 * 60 * 1000)` (1시간).
- 클래스 Javadoc 갱신: "push가 없어 이게 유일한 경로" → "push가 1차 경로, 이 스케줄러는 push 유실 대비 백스톱".

`project-service/.../project/domain/Project.java`
- `fundedAmount` 필드 주석("project-service가 1분마다 pull 조회해 덮어쓴다")을 push가 primary임을 반영하도록 갱신.

## 에러 처리

- push 요청의 `fundedAmount`가 음수/`null`: `@Valid`가 400으로 거부(Bean Validation, `GlobalExceptionHandler` 기존 처리 재사용).
- 존재하지 않는 `projectId`로 push: `EntityNotFoundException`(기존 `getProject()` 경로 재사용, 404).
- push 중 낙관적 락 충돌: 기존 `updateFundedAmount`의 `@Retryable`(3회)+`@Recover`가 그대로 처리 — 3회 재시도 후에도 실패하면 409(`ConcurrentUpdateFailedException`). push는 절대값 덮어쓰기라 멱등이므로 order-service가 재시도해도 안전.
- push가 유실되거나 실패해도(order-service 재시도 없이 유실되는 경우) 다음 pull 주기(최대 1시간) 안에 보정된다. 단, 판정 순간(`closeByDeadline`/`closeEarly`)은 그 1시간 지연과 무관하게 항상 동기 재조회로 판정하므로 실제 성공/실패 오판 위험은 없다.

## 테스트

- `ProjectServiceImplFundedAmountTest`: `178d977` 원본 복구 (정상 갱신, not-found).
- 컨트롤러 레벨 통합 테스트는 기존 `ProjectInternalController` 테스트 스타일을 따라 추가할지 여부는 구현 단계에서 기존 테스트 파일 유무를 보고 판단.
- `FundedAmountReconciliationScheduler`는 `@Scheduled` 값만 바뀌는 설정 변경이라 별도 테스트 불필요(기존 스케줄러 로직 테스트는 `ProjectServiceImplReconciliationTest` 등 기존 테스트가 커버).

## 외부 의존성

- order-service의 push 발신(PR #301, `류민송/order/fundedamount-push`)이 이미 구현되어 있으나, Feign 경로가 단수(`/internal/v1/project/...`)로 되어 있어 project-service의 복수형 엔드포인트와 맞지 않는다. 류민송 님께 경로 수정 요청 필요 — project-service 쪽 구현/머지와는 독립적으로 진행 가능(고쳐지기 전까진 push가 404로 실패하고 pull이 계속 안전망 역할을 하므로 기능이 깨지지 않음).

## 범위 밖

- order-service 쪽 Feign 경로 수정은 이 스펙 범위 밖(팀 간 조율 사항).
- cart-service 관련 이슈(별도로 보고됨)는 이 스펙과 무관, 별도 트랙.
