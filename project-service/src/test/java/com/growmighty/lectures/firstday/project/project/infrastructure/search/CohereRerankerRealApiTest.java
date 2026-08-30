package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Cohere Rerank API를 호출해 "방금 배선한" 리랭킹 단계가 노이즈를 실제로 걸러내는지 확인하는
 * 로컬 전용 테스트. {@code COHERE_API_KEY} 환경변수가 없으면 {@code assumeTrue}로 전체 skip한다
 * (CI에서는 정상적으로 건너뜀 — CI는 NoOpReranker로 돈다).
 *
 * <p>실행: {@code COHERE_API_KEY=... ./gradlew :project-service:test --tests CohereRerankerRealApiTest}
 * (또는 {@code set -a; . infrastructure/.env; set +a} 후 실행)
 *
 * <p>임베딩/ES 색인을 태우지 않고 {@link CohereReranker} 빈만 직접 호출한다 — 검증 대상은
 * "fusion이 뽑아준 후보 순서를 Cohere가 재정렬하는가"이지 검색 전체 파이프라인이 아니다.
 */
@SpringBootTest(properties = "cohere.rerank.enabled=true")
class CohereRerankerRealApiTest extends ElasticsearchIntegrationTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CohereRerankerRealApiTest.class);

    @Autowired(required = false)
    private Reranker reranker;

    @Value("${cohere.rerank.api-key:}")
    private String cohereKey;

    @BeforeEach
    void requireKey() {
        Assumptions.assumeTrue(cohereKey != null && !cohereKey.isBlank(),
                "COHERE_API_KEY 없음 → 실 Cohere 리랭커 테스트 skip");
    }

    private static ProjectDocument doc(long id, String title, String summary) {
        return new ProjectDocument(id, title, summary, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("enabled=true면 Reranker 빈은 CohereReranker다 (NoOp 아님)")
    void wiring() {
        assertThat(reranker).isInstanceOf(CohereReranker.class);
    }

    @Test
    @DisplayName("'강아지 음식' 후보에 섞인 빔프로젝터를 Cohere가 맨 뒤로 내리고 강아지 간식을 1위로 올린다")
    void demotesCrossCategoryNoise() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "4K UHD 빔프로젝터 홈시네마 프로젝터", "거실을 극장으로, 밝은 곳에서도 선명한 화면"),
                2L, doc(2L, "저알러지 동결건조 강아지 간식", "국내산 닭가슴살 100%, 알러지 걱정 없는 반려견 트릿"),
                3L, doc(3L, "반려묘 원목 스크래처", "고양이 발톱 관리용 대형 스크래처"),
                4L, doc(4L, "강아지 노즈워크 장난감 담요", "숨은 간식을 찾는 코 운동 장난감"));

        // fusion이 뽑아줬다고 가정하는 후보 순서 — 빔프로젝터가 맨 앞(현재 버그 상황)
        List<Long> fusionOrder = List.of(1L, 2L, 3L, 4L);

        List<Long> reranked = reranker.rerank("강아지 음식", fusionOrder, docs);
        log.info("[CohereRerankerRealApiTest] fusion={} → reranked={}", fusionOrder, reranked);

        // 관련도 컷(#763) 이후로는 뒤로 밀리는 게 아니라 아예 빠진다 — 실측 점수 기준
        // 강아지 간식 0.56 vs 빔프로젝터 0.017(1등 대비 3%)로 절대·상대 하한을 둘 다 못 넘는다.
        assertThat(reranked).isNotEmpty();
        assertThat(reranked.get(0)).as("강아지 간식이 1위").isEqualTo(2L);
        assertThat(reranked).as("빔프로젝터는 관련도 컷으로 제외").doesNotContain(1L);
    }
}
