package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.RewardPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.RewardResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectRewardTool {

    private final RewardPort rewardPort;

    @Tool(name = "get_project_rewards", description = "특정 프로젝트의 리워드(후원 구성) 목록을 조회한다. " +
        "사용자가 프로젝트의 리워드 구성/가격/재고를 물을 때 사용.")
    public List<RewardResult> getProjectRewards(
        @ToolParam(description = "리워드를 조회할 프로젝트 ID. 이전 대화에서 언급된 프로젝트라도 정확한 id를 " +
            "이번 턴에 다시 확인하지 않았다면 추측해서 넣지 말고, search_projects나 browse_projects로 먼저 id를 확인할 것")
        Long projectId,
        ToolContext toolContext
    ) {
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("get_project_rewards");
        return rewardPort.findByProject(projectId);
    }

}
