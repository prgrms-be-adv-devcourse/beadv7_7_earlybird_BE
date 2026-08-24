package com.growmighty.lectures.firstday.ai.tool.presentation;

import com.growmighty.lectures.firstday.ai.tool.feign.port.ReviewSearchPort;
import com.growmighty.lectures.firstday.ai.tool.feign.port.dto.ReviewSearchResult;
import com.growmighty.lectures.firstday.ai.tool.infrastructure.ToolInvocationRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReviewSearchTool {

    private final ReviewSearchPort reviewSearchPort;
    private final ToolInvocationRecorder recorder;

    @Tool(name = "search_reviews", description = "특정 프로젝트의 리뷰 목록을 조회한다. 사용자가 이미 대화에서 확정한 프로젝트에 대해 리뷰 경향/내용을 물을 때, " +
        "또는 search_projects로 찾은 추천 후보 프로젝트들에 대해 짧은 리뷰 요약을 함께 제시할 때 사용. 후자의 경우 후보 프로젝트마다 한 번 씩 호출한다.")
    public List<ReviewSearchResult> searchReviews(
        @ToolParam(description = "리뷰를 조회할 프로젝트 ID")
        Long projectId
    ) {
        recorder.recordToolUsed("search_reviews");
        return reviewSearchPort.search(projectId);
    }
}
