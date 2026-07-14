package com.growmighty.lectures.firstday.project.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.project.application.ProjectSearchService;
import com.growmighty.lectures.firstday.project.application.ProjectSearchSyncService;
import com.growmighty.lectures.firstday.project.infrastructure.search.ProjectDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/search")
public class ProjectSearchController {

    private final ProjectSearchService searchService;
    private final ProjectSearchSyncService syncService;

    @GetMapping
    public ApiResponse<List<ProjectDocument>> search(
        @RequestParam String keyword,
        @RequestParam(required = false) Double minGoalAmount,
        @RequestParam(required = false) Double maxGoalAmount,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(searchService.search(keyword, minGoalAmount, maxGoalAmount, page, size));
    }

    @GetMapping("/autocomplete")
    public ApiResponse<List<String>> autocomplete(@RequestParam String prefix) {
        return ApiResponse.ok(searchService.autocomplete(prefix));
    }

    @PostMapping("/internal/reindex")
    public ApiResponse<Long> reindex() {
        return ApiResponse.ok(syncService.reindexAll());
    }
}
