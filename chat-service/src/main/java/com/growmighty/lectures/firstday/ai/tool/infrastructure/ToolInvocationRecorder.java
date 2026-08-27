package com.growmighty.lectures.firstday.ai.tool.infrastructure;

import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ChatStreamMetadata;
import com.growmighty.lectures.firstday.ai.chat.presentation.dto.ToolStartEvent;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectSearchResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spring 빈이 아니라 매 요청마다 {@code ChatOrchestrationService}가 직접 생성해 {@code ToolContext}로 넘기는
 * 평범한 객체다 — 스트리밍 파이프라인에서 tool 실행/구독 콜백이 서로 다른 스레드에서 돌아도
 * (Schedulers.boundedElastic() 등) 스레드 바인딩에 의존하지 않도록 하기 위함
 * (구 {@code @RequestScope} 방식은 이 스레드 전환 지점에서 ScopeNotActiveException을 냈음).
 * 같은 이유로 SseEmitter로의 모든 send()도 이 객체의 synchronized send()로 모아,
 * tool 실행 스레드와 구독 콜백 스레드가 emitter에 동시에 쓰는걸 막는다.
 */
public class ToolInvocationRecorder {

    private static final Pattern PROJECT_HEADER_LINE = Pattern.compile("^\\*\\*(.+)\\*\\*$");

    public static final String TOOL_CONTEXT_KEY = "recorder";
    private final String conversationId;

    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private final List<ProjectSearchResult> projects = new ArrayList<>();
    private final List<ProjectSearchResult> browseCandidates = new ArrayList<>();
    private final List<ProjectSearchResult> detailProjects = new ArrayList<>();
    private final List<PolicyChunkResult> policyReferences = new ArrayList<>();
    private final SseEmitter emitter;
    private final AtomicInteger toolSequence = new AtomicInteger(0);

    private final Object metadataLock = new Object();
    private boolean metadataSent = false;

    /**
     * 첫 content chunk가 나가기 직전, ChatOrchestrationService가 정확히 호출해야 하는 진입점.
     * 이 시점에 tool 호출이 아직 안 끝나 있었다면(모델이 tool 호출 전에 안내 텍스트부터 냈다면)
     * 이 최초 metadata는 비어있을 수 있는데, 이후 recordXxx가 resendMetadataIfAlreadySent()로
     * 갱신본을 다시 보낸다.
     */
    public void ensureMetadataSent() {
        synchronized (metadataLock) {
            if (!metadataSent) {
                metadataSent = true;
                sendMetadataEvent();
            }
        }
    }

    private void resendMetadataIfAlreadySent() {
        synchronized (metadataLock) {
            if (metadataSent) {
                sendMetadataEvent();
            }
        }
    }

    private void sendMetadataEvent() {
        send(SseEmitter.event()
            .name("metadata")
            .data(ChatStreamMetadata.of(toolsUsed(), policyReferences(), projects())));
    }

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
        resendMetadataIfAlreadySent();
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
        resendMetadataIfAlreadySent();
    }

    public void recordBrowseCandidates(List<ProjectSearchResult> results) {
        browseCandidates.addAll(results);
    }

    public void recordProjectDetail(ProjectSearchResult results) {
        detailProjects.add(results);
        resendMetadataIfAlreadySent();
    }

    public List<ProjectSearchResult> projects() {
        return detailProjects.isEmpty() ? List.copyOf(projects) : List.copyOf(detailProjects);
    }

    public List<Long> narratedBrowseCandidateIds(String fullText) {
        Set<String> narratedTitles = fullText.lines()
            .map(line -> PROJECT_HEADER_LINE.matcher(line.trim()))
            .filter(Matcher::matches)
            .map(m -> m.group(1).trim())
            .collect(Collectors.toSet());

        return browseCandidates.stream()
            .filter(p -> narratedTitles.contains(p.title()))
            .map(ProjectSearchResult::projectId)
            .distinct()
            .toList();
    }

    public void recordPolicyReferences(List<PolicyChunkResult> references) {
        policyReferences.addAll(references);
        resendMetadataIfAlreadySent();
    }

    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    public List<PolicyChunkResult> policyReferences() {
        return List.copyOf(policyReferences);
    }

}
