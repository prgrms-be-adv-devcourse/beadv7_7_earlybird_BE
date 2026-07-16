package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.infrastructure.search.ProjectDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProjectElasticSearchRepository extends ElasticsearchRepository<ProjectDocument, Long> {
}