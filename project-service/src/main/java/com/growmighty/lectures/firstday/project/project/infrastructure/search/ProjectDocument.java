package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.List;

/**
 * ES 검색 인덱스 전용 문서. status는 일부러 넣지 않는다 — 그 필터링은 MySQL Specification이
 * candidateProjectIds에 대해 그대로 수행한다(design doc 참고). categoryId는 검색어가 실제
 * 카테고리명과 정확히 일치할 때만 그 id로 term 매치시키는 용도라 비분석(long) 필드로 넣는다 —
 * 예전에는 categoryName을 nori 분석 텍스트로 매치했는데, 카테고리명의 일부 토큰만 겹쳐도
 * 매치되는 노이즈가 있었다(ProjectSearchAdapter.resolveCategoryIds 참고). rewardNames는 필터가
 * 아니라 검색어 매칭 대상이라(리워드 이름으로도 프로젝트가 찾아져야 함) 이름 자체를 색인한다.
 * 필드 매핑은 어노테이션이 아니라 project-index-mapping.json으로 직접 관리한다(dense_vector
 * 설정을 어노테이션 속성 이름에 기대지 않기 위해).
 */
@Document(indexName = "projects")
@Setting(settingPath = "elasticsearch/project-index-settings.json")
@Mapping(mappingPath = "elasticsearch/project-index-mapping.json")
public record ProjectDocument(
        @Id Long projectId,
        String title,
        String summary,
        String description,
        Long categoryId,
        List<String> rewardNames,
        float[] titleVector,
        float[] summaryVector,
        float[] descriptionVector,
        float[] categoryVector,
        float[] rewardVector
) {
}
