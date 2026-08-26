package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectCategoryPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectCategoryResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectCategoryTool {

    private final ProjectCategoryPort projectCategoryPort;

    @Tool(name = "list_project_categories", description = "프로젝트 카테고리 전체 목록을 id/name과 함께 조회한다. " +
        "browse_projects나 search_projects의 categoryId를 채우기 전에, 사용자가 언급한 카테고리 이름에 해당하는 " +
        "실제 id를 확인할 때 사용. 여기 반환되는 id만 categoryId로 쓸 수 있다. - path는 상위 분류를 보여주는 참고 정보일 뿐, " +
        "path에 나온 이름을 id로 조회할 수는 없다. 답변에서 카테고리 이름을 하나라도 예시로 들거나 언급하려면 " +
        "지어내지 말고 반드시 이 도구를 먼저 호출해서 실제 목록에서만 골라 써야 한다.")
    public List<ProjectCategoryResult> listProjectCategories(ToolContext toolContext) {
        ToolInvocationRecorder recorder =
            (ToolInvocationRecorder) toolContext.getContext().get(ToolInvocationRecorder.TOOL_CONTEXT_KEY);
        recorder.recordToolUsed("list_project_categories");
        return projectCategoryPort.findAllLeafCategories();
    }
}
