# 펀딩 기간 상한(3개월) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Project`의 `endAt`이 `startAt`으로부터 3개월을 넘지 못하도록, 생성 시와 관리자 마감일 연장(`extendDeadline`) 시 양쪽 모두에 상한 검증을 추가한다.

**Architecture:** `Project` 엔티티 내부에 공유 `private` 검증 메서드 `validateMaxDuration(LocalDateTime, LocalDate)`를 추가하고, 기존 `validatePeriod()`(생성 경로)와 `extendDeadline()`(연장 경로) 양쪽에서 호출한다. 새 클래스나 DTO 레벨 검증은 도입하지 않는다.

**Tech Stack:** Java 21, JUnit 5, AssertJ (기존 `ProjectTest.java` 스타일 그대로 사용).

## Global Constraints

- 경계값은 포함(inclusive): `endAt <= startAt.toLocalDate().plusMonths(3)`이면 허용, 그보다 늦으면 거부.
- 이 검증은 생성(`validatePeriod`)과 연장(`extendDeadline`) 양쪽에 동일하게 적용한다.
- 위반 시 `IllegalArgumentException`을 던진다 (기존 `GlobalExceptionHandler`가 이미 400으로 변환하므로 컨트롤러/DTO 변경 불필요).
- 대상 파일: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java`
- 테스트 파일: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java`

---

### Task 1: 생성 시 3개월 상한 검증 추가

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java:97-99` (상수 추가), `:268-272` (`validatePeriod` 뒤에 새 메서드 추가 및 호출 연결)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java` (기존 파일에 테스트 2개 추가, `register_endAtNotAfterStartAt_throws` 테스트 바로 아래)

**Interfaces:**
- Consumes: 없음 (기존 `Project.register(...)` 정적 팩토리만 사용)
- Produces: `private void validateMaxDuration(LocalDateTime startAt, LocalDate endAt)` — Task 2가 `extendDeadline()`에서 그대로 재사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`ProjectTest.java`의 `register_endAtNotAfterStartAt_throws` 테스트(라인 49-54) 바로 다음에 추가:

```java
    @Test
    @DisplayName("마감일이 시작일로부터 정확히 3개월 이내면 등록할 수 있다")
    void register_endAtExactlyThreeMonthsAfterStartAt_succeeds() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        LocalDate endAt = LocalDate.of(2026, 4, 27);

        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, endAt);

        assertThat(project.getEndAt()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("마감일이 시작일로부터 3개월을 초과하면 등록할 수 없다")
    void register_endAtExceedsThreeMonths_throws() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        LocalDate endAt = LocalDate.of(2026, 4, 28);

        assertThatThrownBy(() -> Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, endAt))
                .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew :project-service:test --tests "ProjectTest.register_endAtExceedsThreeMonths_throws"`
Expected: `register_endAtExactlyThreeMonthsAfterStartAt_succeeds`는 PASS (아직 상한이 없으므로 통과), `register_endAtExceedsThreeMonths_throws`는 FAIL (예외가 안 던져져서 `assertThatThrownBy`가 실패) — 이 FAIL을 확인하는 게 이 스텝의 목적.

- [ ] **Step 3: 최소 구현 작성**

`Project.java:97-99` (`@Version private Long version;` 필드 선언 다음, 생성자 시작 전)에 상수 추가:

```java
    /** 토스페이먼츠 환불정책상 펀딩 기간(startAt~endAt)이 이 개월 수를 넘을 수 없다. */
    private static final int MAX_FUNDING_PERIOD_MONTHS = 3;
```

`Project.java:268-272`의 `validatePeriod` 메서드를 아래로 교체 (기존 로직 유지 + 마지막에 호출 추가):

```java
    private void validatePeriod(LocalDateTime startAt, LocalDate endAt) {
        if (startAt == null || endAt == null || !endAt.atStartOfDay().isAfter(startAt)) {
            throw new IllegalArgumentException("마감일은 시작일 이후여야 합니다. startAt=" + startAt + ", endAt=" + endAt);
        }
        validateMaxDuration(startAt, endAt);
    }

    private void validateMaxDuration(LocalDateTime startAt, LocalDate endAt) {
        LocalDate maxEndAt = startAt.toLocalDate().plusMonths(MAX_FUNDING_PERIOD_MONTHS);
        if (endAt.isAfter(maxEndAt)) {
            throw new IllegalArgumentException(
                    "마감일은 시작일로부터 최대 " + MAX_FUNDING_PERIOD_MONTHS + "개월 이내여야 합니다. "
                            + "시작일=" + startAt.toLocalDate() + ", 최대허용마감일=" + maxEndAt + ", 요청값=" + endAt);
        }
    }
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectTest"`
Expected: `ProjectTest`의 모든 테스트 PASS (새 테스트 2개 포함, 기존 테스트도 회귀 없이 통과 — 특히 `register_endAtNotAfterStartAt_throws`와 기본 `project()` 헬퍼가 쓰는 `LocalDate.now().plusDays(30)`은 3개월보다 훨씬 짧아 영향 없음).

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java
git commit -m "Feat: 프로젝트 생성 시 펀딩 기간 최대 3개월 제한 추가"
```

---

### Task 2: 마감일 연장 시에도 동일한 3개월 상한 적용

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java:204-210` (`extendDeadline` 메서드)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java` (`extendDeadline_onlyForward` 테스트 바로 아래, 라인 145-156 부근)

**Interfaces:**
- Consumes: Task 1에서 만든 `private void validateMaxDuration(LocalDateTime startAt, LocalDate endAt)` (같은 클래스 내부라 바로 호출 가능)
- Produces: 없음 (이 기능의 마지막 태스크)

- [ ] **Step 1: 실패하는 테스트 작성**

`ProjectTest.java`의 `extendDeadline_onlyForward` 테스트(라인 145-156) 바로 다음에 추가:

```java
    @Test
    @DisplayName("마감일 연장도 시작일로부터 3개월을 초과할 수 없다")
    void extendDeadline_beyondThreeMonthsFromStartAt_throws() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, LocalDate.of(2026, 2, 1));
        project.approve();

        project.extendDeadline(LocalDate.of(2026, 4, 27));
        assertThat(project.getEndAt()).isEqualTo(LocalDate.of(2026, 4, 27));

        assertThatThrownBy(() -> project.extendDeadline(LocalDate.of(2026, 4, 28)))
                .isInstanceOf(IllegalArgumentException.class);
    }
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew :project-service:test --tests "ProjectTest.extendDeadline_beyondThreeMonthsFromStartAt_throws"`
Expected: FAIL — `project.extendDeadline(LocalDate.of(2026, 4, 28))`이 지금은 "현재 마감일 이후"라는 조건만 통과하면 그대로 저장되므로 예외가 안 던져짐.

- [ ] **Step 3: 최소 구현 작성**

`Project.java:204-210`의 `extendDeadline` 메서드를 아래로 교체:

```java
    /** 관리자 전용: 마감일 연장만 허용 (과거로 당길 수 없고, 시작일로부터 최대 개월 수도 넘을 수 없음) */
    public void extendDeadline(LocalDate newEndAt) {
        if (newEndAt == null || !newEndAt.isAfter(this.endAt)) {
            throw new IllegalArgumentException("마감일은 현재 마감일 이후로만 연장할 수 있습니다. 현재 마감일=" + this.endAt + ", 요청값=" + newEndAt);
        }
        validateMaxDuration(this.startAt, newEndAt);
        this.endAt = newEndAt;
    }
```

- [ ] **Step 4: 테스트 실행해서 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectTest"`
Expected: `ProjectTest`의 모든 테스트 PASS (새 테스트 포함, 기존 `extendDeadline_onlyForward`도 `currentEndAt.plusDays(10)`으로 3개월 안쪽이라 회귀 없음).

- [ ] **Step 5: 전체 모듈 테스트로 회귀 확인**

Run: `./gradlew :project-service:test`
Expected: BUILD SUCCESSFUL (다른 서비스 계층 테스트도 이 도메인 변경에 영향받지 않는지 최종 확인).

- [ ] **Step 6: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java
git commit -m "Feat: 마감일 연장에도 펀딩 기간 3개월 상한 적용"
```
