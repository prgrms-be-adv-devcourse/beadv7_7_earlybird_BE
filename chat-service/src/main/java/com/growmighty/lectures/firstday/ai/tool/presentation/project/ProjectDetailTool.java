package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectDetailPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectDetailResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectDetailTool {

    private final ProjectDetailPort projectDetailPort;

    @Tool(name = "get_project_detail", description = "특정 프로젝트 하나의 상세 정보(설명 포함)를 조회한다. " +
        "browse_projects/search_projects로 후보를 찾은 뒤, 사용자가 그중 하나에 대해 더 자세히 알고 싶어할 때 사용.")
    public ProjectDetailResult getProjectDetail(
        @ToolParam(description = "상세 정보를 조회할 프로젝트 ID. 이전 대화에서 언급된 프로젝트라도 정확한 id를 " +
            "이번 턴에 다시 확인하지 않았다면 추측해서 넣지 말고, search_projects나 browse_projects로 먼저 id를 확인할 것")
        Long projectId,
        ToolContext toolContext
    ) {
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("get_project_detail");
        return projectDetailPort.findById(projectId);
    }

}
