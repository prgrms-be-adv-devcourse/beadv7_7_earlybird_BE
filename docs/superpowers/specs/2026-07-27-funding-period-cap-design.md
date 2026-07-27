# 펀딩 기간 상한(3개월) 도입 설계

- 날짜: 2026-07-27
- 담당: 강대혁 (project-service)
- 배경: 토스페이먼츠 환불정책상, 펀딩 시작일로부터 마감일까지의 기간이 일정 범위를 넘으면 안 됨. 현재 `Project`는 `startAt`~`endAt` 기간에 상한이 없어 이를 위반할 수 있다.

## 요구사항

- 프로젝트의 `endAt`은 `startAt`으로부터 **최대 3개월 이내**여야 한다.
- 이 제약은 **생성 시**와 **관리자의 마감일 연장(`extendDeadline`) 시** 양쪽 모두에 적용된다.
- 경계값은 포함(inclusive): `startAt`이 7/27이면 `endAt`은 10/27까지 허용된다 (`startAt.toLocalDate().plusMonths(3)`).

## 아키텍처

기존 `validatePeriod()`(생성 시 호출)와 동일한 위치 — `Project` 엔티티 내부의 `private` 메서드로 구현한다. 새 정책 클래스나 Bean Validation 어노테이션은 도입하지 않는다 (규칙이 하나뿐이고, 엔티티 밖으로 새면 우회 가능한 경로가 생기므로).

## 컴포넌트 변경

`project-service/.../project/domain/Project.java`

1. 상수 추가: `private static final int MAX_FUNDING_PERIOD_MONTHS = 3;`
2. 신규 private 메서드:
   ```java
   private void validateMaxDuration(LocalDateTime startAt, LocalDate endAt) {
       LocalDate maxEndAt = startAt.toLocalDate().plusMonths(MAX_FUNDING_PERIOD_MONTHS);
       if (endAt.isAfter(maxEndAt)) {
           throw new IllegalArgumentException(
               "마감일은 시작일로부터 최대 " + MAX_FUNDING_PERIOD_MONTHS + "개월 이내여야 합니다. "
               + "시작일=" + startAt.toLocalDate() + ", 최대허용마감일=" + maxEndAt + ", 요청값=" + endAt);
       }
   }
   ```
3. `validatePeriod(startAt, endAt)` 끝에 `validateMaxDuration(startAt, endAt)` 호출 추가 (생성 경로).
4. `extendDeadline(LocalDate newEndAt)`에서 기존 "현재 마감일 이후" 검증을 통과한 뒤 `validateMaxDuration(this.startAt, newEndAt)` 호출 추가 (연장 경로).

컨트롤러/서비스/DTO 계층은 변경 없음 — `IllegalArgumentException`은 기존 `GlobalExceptionHandler`가 이미 400으로 변환한다.

## 에러 처리

새 경로 모두 기존과 동일하게 `IllegalArgumentException` → `GlobalExceptionHandler.handleIllegalArgument` → HTTP 400, 메시지에 시작일/최대허용마감일/요청값을 포함해 원인을 바로 알 수 있게 한다.

## 테스트

`project-service/src/test/.../ProjectTest.java`에 케이스 추가 (기존 `@DisplayName` 스타일 유지):

- 생성 시: `startAt`으로부터 3개월 이내 `endAt`은 정상 등록된다 (경계값 정확히 3개월 후 포함)
- 생성 시: `startAt`으로부터 3개월을 초과한 `endAt`은 `IllegalArgumentException`
- 연장 시: 현재 마감일 이후이면서 `startAt` 기준 3개월 이내로의 연장은 성공
- 연장 시: `startAt` 기준 3개월을 초과하는 연장은 `IllegalArgumentException` (현재 마감일보다 뒤라도 거부됨)

기존 "마감일이 시작일 이후가 아니면 등록할 수 없다", "마감일은 현재 마감일 이후로만 연장할 수 있다" 테스트는 그대로 유지 (회귀 없음 확인).

## 범위 밖

- 기존에 이미 3개월을 초과해 생성된 프로젝트에 대한 마이그레이션/일괄 조정은 다루지 않는다 (현재 시드 데이터는 전부 3개월 이내).
- `startAt` 자체를 나중에 바꾸는 기능은 없으므로 고려하지 않는다.
