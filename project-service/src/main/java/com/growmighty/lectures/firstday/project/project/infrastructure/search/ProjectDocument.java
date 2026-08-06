package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * ES 검색 인덱스 전용 문서. categoryId/status는 일부러 넣지 않는다 — 그 필터링은 MySQL
 * Specification이 candidateProjectIds에 대해 그대로 수행한다(design doc 참고). 필드 매핑은
 * 어노테이션이 아니라 project-index-mapping.json으로 직접 관리한다(dense_vector 설정을
 * 어노테이션 속성 이름에 기대지 않기 위해).
 */
@Document(indexName = "projects")
@Setting(settingPath = "elasticsearch/project-index-settings.json")
@Mapping(mappingPath = "elasticsearch/project-index-mapping.json")
public record ProjectDocument(
        @Id Long projectId,
        String title,
        String summary,
        String description,
        float[] embedding
) {
}
