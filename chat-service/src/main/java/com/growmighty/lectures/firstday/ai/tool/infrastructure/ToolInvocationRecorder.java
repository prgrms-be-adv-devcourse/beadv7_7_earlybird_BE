package com.growmighty.lectures.firstday.ai.tool.infrastructure;

import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ToolStartEvent;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring 빈이 아니라 매 요청마다 {@code ChatOrchestrationService}가 직접 생성해 {@code ToolContext}로 넘기는
 * 평범한 객체다 — 스트리밍 파이프라인에서 tool 실행/구독 콜백이 서로 다른 스레드에서 돌아도
 * (Schedulers.boundedElastic() 등) 스레드 바인딩에 의존하지 않도록 하기 위함
 * (구 {@code @RequestScope} 방식은 이 스레드 전환 지점에서 ScopeNotActiveException을 냈음).
 * 같은 이유로 SseEmitter로의 모든 send()도 이 객체의 synchronized send()로 모아,
 * tool 실행 스레드와 구독 콜백 스레드가 emitter에 동시에 쓰는걸 막는다.
 */
public class ToolInvocationRecorder {

    public static final String TOOL_CONTEXT_KEY = "recorder";
    private final String conversationId;

    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private final List<ProjectSearchResult> projects = new ArrayList<>();
    private final List<ProjectSearchResult> detailProjects = new ArrayList<>();
    private final List<PolicyChunkResult> policyReferences = new ArrayList<>();
    private final SseEmitter emitter;
    private final AtomicInteger toolSequence = new AtomicInteger(0);

    public ToolInvocationRecorder(SseEmitter emitter, String conversationId) {
        this.emitter = emitter;
        this.conversationId = conversationId;
    }

    public String conversationId() {
        return conversationId;
    }

    // toolsUsed에 기록하는 동시에, 이 tool의 실제 작업이 시작된다는 tool_start 이벤트도 함께 보낸다.
    public void recordToolUsed(String toolName) {
        toolsUsed.add(toolName);
        send(SseEmitter.event()
            .name("tool_start")
            .data(new ToolStartEvent(
                toolName,
                toolSequence.incrementAndGet(),
                ToolProgressMessages.messageFor(toolName),
                ToolProgressMessages.completedMessageFor(toolName)
            )));
    }

    public synchronized void send(SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    public void recordProjects(List<ProjectSearchResult> results) {
        projects.addAll(results);
    }

    public void recordProjectDetail(ProjectSearchResult results) {
        detailProjects.add(results);
    }

    public List<ProjectSearchResult> projects() {
        return detailProjects.isEmpty() ? List.copyOf(projects) : List.copyOf(detailProjects);
    }

    public void recordPolicyReferences(List<PolicyChunkResult> references) {
        policyReferences.addAll(references);
    }

    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    public List<PolicyChunkResult> policyReferences() {
        return List.copyOf(policyReferences);
    }

}
