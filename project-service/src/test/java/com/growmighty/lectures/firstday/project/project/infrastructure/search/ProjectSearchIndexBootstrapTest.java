package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectSearchIndexBootstrapTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private static float[] dummyVector() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        return vector;
    }

    // ElasticsearchIntegrationTestSupport의 ES 컨테이너는 JVM당 싱글턴으로 여러 테스트 클래스가
    // 같은 "projects" 인덱스를 공유한다(ProjectSearchAdapterIntegrationTest 참고) — 여기서 하드코딩한
    // ID(1L, 2L)로 색인한 문서를 안 지우면 이 테스트의 hasSize(1) 단언이 다른 테스트가 우연히 남긴
    // 문서 유무에 좌우되는 취약한 상태가 된다.
    @AfterEach
    void cleanUpIndexedDocuments() {
        elasticsearchOperations.delete("1", ProjectDocument.class);
        elasticsearchOperations.delete("2", ProjectDocument.class);
    }

    @Test
    @DisplayName("기동 시 인덱스가 nori 분석기 설정으로 생성되어, 형태소가 분리된 한국어 검색이 매치된다")
    void indexBootstrapsWithNoriAnalyzer() {
        // "한국어를"은 명사 "한국어" + 조사 "를"이 공백 없이 붙은, 실제 한국어 표기 그대로다.
        // nori_tokenizer는 형태소 분석으로 이걸 "한국어"/"를" 토큰으로 쪼개므로 "한국어"만으로도
        // 매치된다 — 공백 기준으로만 쪼개는(nori 미적용) 분석기라면 "한국어를"이 통짜 토큰이라
        // 매치되지 않는다. 즉 이 테스트는 형태소 분리 자체를 검증한다(단순 공백 분리와 구분됨).
        elasticsearchOperations.save(new ProjectDocument(1L, "한국어를 사랑하는 사람들의 모임", null, null, null, null, dummyVector(), null, null, null, null));
        elasticsearchOperations.save(new ProjectDocument(2L, "전혀 관련 없는 다른 프로젝트 제목", null, null, null, null, dummyVector(), null, null, null, null));
        // ES는 근실시간 검색이라 refresh 정책을 명시하지 않으면 save() 직후 검색에 안 잡힐 수 있다 —
        // 테스트에서는 명시적으로 refresh해 색인을 즉시 검색 가능하게 만든다.
        elasticsearchOperations.indexOps(ProjectDocument.class).refresh();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("title").query("한국어")))
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(query, ProjectDocument.class);

        assertThat(hits.getSearchHits()).hasSize(1);
        assertThat(hits.getSearchHits().get(0).getContent().projectId()).isEqualTo(1L);
    }
}
