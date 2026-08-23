package com.growmighty.lectures.firstday.ai.tool.presentation.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.ProjectCategoryPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.ProjectCategoryResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectCategoryTool {

    private final ProjectCategoryPort projectCategoryPort;
    private final ToolInvocationRecorder recorder;

    @Tool(name = "list_project_categories", description = "프로젝트 카테고리 전체 목록을 id/name과 함께 조회한다. " +
        "browse_projects나 search_projects의 categoryId를 채우기 전에, 사용자가 언급한 카테고리 이름에 해당하는 " +
        "실제 id를 확인할 때 사용. 여기 반환되는 id만 categoryId로 쓸 수 있다. - path는 상위 분류를 보여주는 참고 정보일 뿐, " +
        "path에 나온 이름을 id로 조회할 수는 없다.")
    public List<ProjectCategoryResult> listProjectCategories() {
        recorder.recordToolUsed("list_project_categories");
        return projectCategoryPort.findAllLeafCategories();
    }
}
