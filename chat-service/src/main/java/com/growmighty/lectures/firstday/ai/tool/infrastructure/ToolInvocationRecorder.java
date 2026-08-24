package com.growmighty.lectures.firstday.ai.tool.infrastructure;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring 빈이 아니라 매 요청마다 {@code ChatOrchestrationService}가 직접 생성해 {@code ToolContext}로 넘기는
 * 평범한 객체다 — 스트리밍 파이프라인에서 tool 실행/구독 콜백이 서로 다른 스레드에서 돌아도
 * (Schedulers.boundedElastic() 등) 스레드 바인딩에 의존하지 않도록 하기 위함
 * (구 {@code @RequestScope} 방식은 이 스레드 전환 지점에서 ScopeNotActiveException을 냈음).
 */
public class ToolInvocationRecorder {

    public static final String TOOL_CONTEXT_KEY = "recorder";

    private final Set<String> toolsUsed = new LinkedHashSet<>();
    private final List<PolicyChunkResult> policyReferences = new ArrayList<>();

    public void recordToolUsed(String toolName) {
        toolsUsed.add(toolName);
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
