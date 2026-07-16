package com.growmighty.lectures.firstday.project.presentation;

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
    public List<ProjectDocument> search(
        @RequestParam String keyword,
        @RequestParam(required = false) Double minGoalAmount,
        @RequestParam(required = false) Double maxGoalAmount,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
        return searchService.search(keyword, minGoalAmount, maxGoalAmount, page, size);
    }

    @GetMapping("/autocomplete")
    public List<String> autocomplete(@RequestParam String prefix) {
        return searchService.autocomplete(prefix);
    }

    @PostMapping("/internal/reindex")
    public Long reindex() {
        return syncService.reindexAll();
    }
}
