package com.growmighty.lectures.firstday.project.infrastructure.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProjectSearchRepository extends ElasticsearchRepository<ProjectDocument, Long> {
}
