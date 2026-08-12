# FundedAmount Push 수신 엔드포인트 복구 + Pull 주기 완화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** order-service가 결제 확정/취소 시 push로 보내는 fundedAmount를 project-service가 받는 엔드포인트를 복구하고, 이제 push가 1차 경로가 됐으니 기존 1분 주기 pull을 1시간 백스톱으로 완화한다.

**Architecture:** PR #100(커밋 `178d977`)에서 이미 구현·리뷰됐던 push 수신 엔드포인트(컨트롤러 + DTO)를 복구한다. 서비스 레이어(`ProjectServiceImpl.updateFundedAmount`, 낙관적 락 재시도 포함)는 이미 develop에 있으므로 새로 만들지 않는다. `FundedAmountReconciliationScheduler`의 주기만 설정값을 바꾼다.

**Tech Stack:** Spring Boot(WebMvc, Bean Validation), JUnit 5 + MockMvc(`@WebMvcTest`) + Mockito

## Global Constraints

- 최종 push 수신 경로는 `PUT /internal/v1/projects/{projectId}/funded-amount` (복수형 `projects`) — 기존 `ProjectInternalController`의 `@RequestMapping("/internal/v1/projects")`를 그대로 따름.
- Request body: `{ "fundedAmount": <숫자> }` — 절대값(누적 총액) 덮어쓰기, 필드 추가 없음.
- `ProjectServiceImpl.updateFundedAmount(Long, BigDecimal)`은 이미 develop에 존재하며 수정하지 않는다 (변경 대상 아님).
- order-service Feign 경로(`/internal/v1/project/...`, 단수) 수정은 이 플랜 범위 밖 — project-service 쪽 작업과 무관하게 완료 가능.

---

### Task 1: Push 수신 엔드포인트 (DTO + 컨트롤러)

**Files:**
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/request/FundedAmountUpdateRequest.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerTest.java` (신규)

**Interfaces:**
- Consumes: `ProjectService.updateFundedAmount(Long projectId, BigDecimal fundedAmount)` — 기존 develop에 존재, 변경 없음.
- Produces: `FundedAmountUpdateRequest(BigDecimal fundedAmount)` record — 다른 태스크에서 참조하지 않음(이 태스크로 완결).

- [ ] **Step 1: 실패하는 컨트롤러 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerTest.java` 새로 생성:

```java
package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * order-service가 결제 확정/취소 시 push로 호출하는 경로 — 정상 위임과, 음수/누락 값이
 * 서비스까지 가지 않고 400으로 거부되는지 확인한다.
 */
@WebMvcTest(ProjectInternalController.class)
class ProjectInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    @DisplayName("fundedAmount: 정상 요청이면 서비스에 그대로 위임한다")
    void updateFundedAmount_delegatesToService() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundedAmount\": 500000}"))
                .andExpect(status().isOk());

        verify(projectService).updateFundedAmount(eq(1L), eq(BigDecimal.valueOf(500000)));
    }

    @Test
    @DisplayName("fundedAmount: 음수면 400으로 거부되고 서비스는 호출되지 않는다")
    void updateFundedAmount_negative_rejectedWith400() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundedAmount\": -1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
    }

    @Test
    @DisplayName("fundedAmount: 누락되면 400으로 거부되고 서비스는 호출되지 않는다")
    void updateFundedAmount_missing_rejectedWith400() throws Exception {
        mockMvc.perform(put("/internal/v1/projects/{projectId}/funded-amount", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(projectService);
    }
}
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew :project-service:test --tests "ProjectInternalControllerTest"`
Expected: 컴파일 실패 또는 404 — `PUT /internal/v1/projects/{projectId}/funded-amount` 엔드포인트가 아직 없음.

- [ ] **Step 3: DTO 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/request/FundedAmountUpdateRequest.java` 신규 생성:

```java
package com.growmighty.lectures.firstday.project.project.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** PUT /internal/v1/projects/{projectId}/funded-amount — 절대값(누적 총액) 덮어쓰기, 멱등. */
public record FundedAmountUpdateRequest(
        @NotNull @PositiveOrZero BigDecimal fundedAmount
) {
}
```

- [ ] **Step 4: 컨트롤러에 엔드포인트 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java`를 다음과 같이 수정 — import 4개 추가, 클래스 마지막에 메서드 추가:

```java
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.FundedAmountUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

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

(기존 `PathVariable`은 이미 import되어 있으므로 중복 import는 추가하지 않는다 — 파일을 먼저 읽고 없는 것만 추가할 것.)

- [ ] **Step 5: 테스트 실행해서 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectInternalControllerTest"`
Expected: PASS (3개 테스트 모두)

- [ ] **Step 6: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/request/FundedAmountUpdateRequest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerTest.java
git commit -m "Feat: fundedAmount push 수신 엔드포인트 복구 (PUT /internal/v1/projects/{projectId}/funded-amount)"
```

---

### Task 2: Pull 주기 1시간으로 완화 + 주석 갱신

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/FundedAmountReconciliationScheduler.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java:60-61`

**Interfaces:**
- Consumes: 없음 (설정값 변경).
- Produces: 없음 — 다른 태스크가 이 변경에 의존하지 않음.

Task 1로 push가 primary 경로가 됐으므로, 이 태스크는 순수 설정값 변경 + 주석 정정이다. `@Scheduled` 간격 자체는 로직이 아니라 이 변경을 검증하는 별도 테스트는 만들지 않는다(기존 `ProjectServiceImplReconciliationTest`가 `reconcileFundedAmounts()`의 동작 자체는 이미 커버).

- [ ] **Step 1: 스케줄러 간격과 Javadoc 수정**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/FundedAmountReconciliationScheduler.java` 전체를 다음으로 교체:

```java
package com.growmighty.lectures.firstday.project.project.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IN_PROGRESS 프로젝트의 fundedAmount를 order-service push(주문 확정/취소 시 즉시 반영)로
 * 최신 상태를 유지한다. 이 스케줄러는 1시간마다 pull 조회로 재확인하는 백스톱이다 — push가
 * 네트워크 오류 등으로 유실된 경우를 대비한 안전망이며, 평상시 실시간 반영은 push가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class FundedAmountReconciliationScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void reconcile() {
        projectService.reconcileFundedAmounts();
    }
}
```

- [ ] **Step 2: Project.java 주석 수정**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java:60-61`의 주석을 찾아서:

```java
    // updateFundedAmount()로 갱신된다 — project-service가 1분마다 order-service를 pull 조회해
    // 이 값을 확정 누적 총액으로 덮어쓴다(FundedAmountReconciliationScheduler 참고).
```

다음으로 교체:

```java
    // updateFundedAmount()로 갱신된다 — order-service가 결제 확정/취소 시 push로 즉시 갱신하고,
    // 유실 대비 백스톱으로 1시간마다 pull 재확인한다(FundedAmountReconciliationScheduler 참고).
```

- [ ] **Step 3: 전체 빌드로 회귀 확인**

Run: `./gradlew :project-service:test`
Expected: 기존 테스트 전부 PASS (주석/간격 값만 바뀌었으므로 동작 변화 없음).

- [ ] **Step 4: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/FundedAmountReconciliationScheduler.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java
git commit -m "Refactor: fundedAmount pull 주기를 1시간으로 완화 (push가 1차 경로로 전환됨)"
```

---

## 완료 후 남는 일 (이 플랜 범위 밖)

- PR #301(`류민송/order/fundedamount-push`)의 `ProjectFundedAmountFeignClient` 경로가 단수(`/internal/v1/project/...`)로 되어 있어, 류민송 님이 `s`를 붙이기 전까지는 push가 404로 실패한다(그동안은 1시간 pull이 안전망 역할을 계속하므로 기능은 깨지지 않는다). 팀 채팅으로 전달 필요.
