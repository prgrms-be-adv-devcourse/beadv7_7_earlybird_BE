package com.growmighty.lectures.firstday.ai.tool.infrastructure;

import com.growmighty.lectures.firstday.ai.policy.infrastructure.search.PolicyChunkResult;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequestScope
public class ToolInvocationRecorder {

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
