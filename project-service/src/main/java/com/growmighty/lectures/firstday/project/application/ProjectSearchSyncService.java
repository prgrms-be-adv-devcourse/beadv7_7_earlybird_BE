package com.growmighty.lectures.firstday.project.application;

import com.growmighty.lectures.firstday.project.domain.ProjectRepository;
import com.growmighty.lectures.firstday.project.infrastructure.search.ProjectDocument;
import com.growmighty.lectures.firstday.project.infrastructure.search.ProjectSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSearchSyncService {

    private final ProjectRepository projectRepository;
    private final ProjectSearchRepository searchRepository;

    @Transactional(readOnly = true)
    public long reindexAll() {
        List<ProjectDocument> docs = projectRepository.findAll().stream()
            .map(ProjectDocument::from)
            .toList();
        searchRepository.saveAll(docs);        // ★ 내부적으로 bulk API 사용 — Step 7에서 단건 저장과 비교
        log.info("전체 재색인 완료: {}건", docs.size());
        return docs.size();
    }
}
