package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * ES는 커스텀 분석기(nori)/dense_vector 필드를 인덱스 "생성 시점"에만 지정할 수 있다 — 문서를
 * 먼저 저장해 자동 생성되게 두면 우리가 원하는 설정 없이 만들어진다. 그래서 기동 시 인덱스가
 * 없으면 명시적으로 설정+매핑을 갖춰 만든다. ES가 기동 시점에 잠깐 안 떠 있어도 앱 부팅
 * 자체를 막지 않는다 — 이후 색인/검색 호출은 어차피 각자의 실패 처리 경로(로그 흡수 / 503)를 탄다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @PostConstruct
    void ensureIndexExists() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProjectDocument.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 인덱스 초기화 실패 — 검색 기능이 정상 동작하지 않을 수 있습니다.", e);
        }
    }
}
