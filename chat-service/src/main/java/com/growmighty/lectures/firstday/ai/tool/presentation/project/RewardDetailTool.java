package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.RewardPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.RewardResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RewardDetailTool {

    private final RewardPort rewardPort;
    private final ToolInvocationRecorder recorder;

    @Tool(name = "get_reward_detail", description = "리워드 하나의 상세 정보를 조회한다. " +
        "get_project_rewards로 목록을 본 뒤, 사용자가 그중 특정 리워드에 대해 더 자세히 물을 때 사용.")
    public RewardResult getRewardDetail(
        @ToolParam(description = "상세 정보를 조회할 리워드 ID. 이전 대화에서 언급된 리워드라도 정확한 id를 " +
            "이번 턴에 다시 확인하지 않았다면 추측해서 넣지 말고, get_project_rewards로 먼저 id를 확인할 것")
        Long rewardId
    ) {
       recorder.recordToolUsed("get_reward_detail");
       return rewardPort.findById(rewardId);
    }

}
