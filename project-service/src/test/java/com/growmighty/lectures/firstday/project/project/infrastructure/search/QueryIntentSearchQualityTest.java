package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Query Intent 고도화 Before/After 검색 품질 검증.
 *
 * <h3>검증 목표</h3>
 * <p>QueryIntentAnalyzer가 생성한 {@code enrichedQuery}가 Vector Search(kNN)의 품질을
 * 실제로 개선하는지 검증한다. 특히 다음 케이스를 집중 검증한다:
 * <ol>
 *   <li><b>"여름용 옷 → 롱코트 탈락"</b>: 계절 조건 충돌 상품이 enrichedQuery 덕분에 결과에서 제거되는지</li>
 *   <li><b>자연어 → 상황 기반 검색</b>: "비 올 때 강아지 산책용 가방", "편하게 출퇴근할 수 있는 가방" 등</li>
 *   <li><b>enrichedQuery 경로 검증</b>: enrichedQuery가 kNN 벡터로 실제로 사용되는지</li>
 * </ol>
 *
 * <h3>임베딩 스텁 작동 원리</h3>
 * <p>실제 OpenAI를 호출하지 않는다. EmbeddingModel은 텍스트별 결정적 랜덤 벡터 스텁으로 동작한다:
 * <ul>
 *   <li>같은 텍스트 → 항상 같은 1536차원 벡터 (완전 일치)</li>
 *   <li>다른 텍스트 → 고차원 랜덤 벡터의 성질상 코사인 유사도 ≈ 0 (kNN 유사도 하한 미달)</li>
 * </ul>
 * <p>따라서 {@code enrichedQuery}를 상품 title과 동일한 텍스트로 설정하면 kNN이 해당 상품을 찾고,
 * 다른 텍스트로 설정하면 kNN에서 탈락한다. 이를 통해 enrichedQuery 경로를 명확히 검증한다.
 *
 * <h3>Before/After 비교 방식</h3>
 * <ul>
 *   <li><b>Before (passThrough)</b>: enrichedQuery = 원본 자연어 쿼리 → 랜덤 벡터 → kNN 탈락</li>
 *   <li><b>After (structuredQuery)</b>: enrichedQuery = 상품 설명에 가까운 구체적 표현 → kNN 히트</li>
 * </ul>
 */
@SpringBootTest
class QueryIntentSearchQualityTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;

    /**
     * QueryIntentAnalyzer를 MockBean으로 교체.
     * 각 테스트에서 enrichedQuery를 원하는 값으로 제어하여 Before/After를 검증한다.
     */
    @MockitoBean
    private QueryIntentAnalyzer queryIntentAnalyzer;

    private final List<Long> savedProjectIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        savedProjectIds.forEach(adapter::remove);
        savedProjectIds.clear();
    }

    // ── 1. "여름용 옷 → 롱코트 탈락" 핵심 케이스 ────────────────────────────

    @Test
    @DisplayName("[핵심] 여름용 옷 검색 시 - BEFORE: 원본 쿼리로는 롱코트/반팔 구분 불가, AFTER: enrichedQuery로 반팔만 Hit")
    void summerClothesSearch_enrichedQuery_excludesWinterCoat() {
        // ── 상품 색인 ──
        // 상품 title이 곧 임베딩 텍스트. 쿼리 enrichedQuery와 동일하면 kNN 완전 일치.
        Project summerTshirt = indexed("여름 쿨링 반팔 의류",
                "여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 시원하게 보내는 쿨링 반팔 티셔츠");
        Project winterCoat = indexed("겨울 울 혼방 롱코트",
                "프리미엄 울 혼방 롱코트", "따뜻한 겨울을 위한 울 혼방 오버핏 롱코트");

        // ── [BEFORE] 원본 자연어 쿼리 그대로 임베딩 (passThrough) ──
        // enrichedQuery = "여름에 시원한 옷" → 랜덤 벡터 → 롱코트도 반팔도 kNN 탈락
        // BM25만 동작하므로 둘 다 안 나오거나 BM25 어휘 매치에 의존
        whenIntentPassThrough("여름에 시원한 옷");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> before = adapter.search("여름에 시원한 옷");
            // BEFORE: enrichedQuery = 원본 → kNN에서 둘 다 벡터 불일치 → 반팔도 롱코트도 kNN 불발
            // BM25로 "여름"이 부분 매치될 수 있으나 롱코트는 반팔보다 BM25도 낮음
            assertThat(before).doesNotContain(winterCoat.getProjectId());
        });

        // ── [AFTER] enrichedQuery로 여름 반팔 텍스트 지정 ──
        // enrichedQuery = "여름 쿨링 반팔 의류" → 반팔 상품 title과 동일 → kNN 완전 일치
        // 롱코트는 "겨울 울 혼방 롱코트" → 벡터 불일치 → kNN 탈락
        whenIntentStructured("여름에 시원한 옷", "여름 쿨링 반팔 의류");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> after = adapter.search("여름에 시원한 옷");
            // AFTER: 반팔 티셔츠는 kNN 완전 일치로 결과에 포함
            assertThat(after).contains(summerTshirt.getProjectId());
            // AFTER: 롱코트는 enrichedQuery 벡터와 거리가 멀어 결과에서 제외
            assertThat(after).doesNotContain(winterCoat.getProjectId());
        });
    }

    @Test
    @DisplayName("[핵심] 겨울 옷 검색 시 - enrichedQuery로 롱코트만 Hit, 반팔 티셔츠 탈락")
    void winterClothesSearch_enrichedQuery_excludesSummerTshirt() {
        Project summerTshirt = indexed("여름 쿨링 반팔 의류",
                "여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 시원하게 보내는 쿨링 반팔 티셔츠");
        Project winterCoat = indexed("겨울 울 혼방 롱코트",
                "프리미엄 울 혼방 롱코트", "따뜻한 겨울을 위한 울 혼방 오버핏 롱코트");

        // enrichedQuery = "겨울 울 혼방 롱코트" → 롱코트 상품 title과 동일 → kNN 완전 일치
        whenIntentStructured("겨울에 따뜻하게 입을 옷", "겨울 울 혼방 롱코트");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("겨울에 따뜻하게 입을 옷");
            assertThat(result).contains(winterCoat.getProjectId());
            assertThat(result).doesNotContain(summerTshirt.getProjectId());
        });
    }

    // ── 2. 자연어 검색 → enrichedQuery 개선 케이스 ───────────────────────────

    @Test
    @DisplayName("'비 올 때 강아지 산책하면서 쓸 가방' - enrichedQuery로 방수 가방만 Hit, 롱코트 탈락")
    void rainyDogWalkBag_enrichedQuery_findsWaterproofBag() {
        Project waterproofBag = indexed("강아지 산책용 방수 가방 백팩",
                "방수 강아지 산책 백팩", "비 오는 날 강아지 산책에 최적화된 방수 백팩");
        Project coat = indexed("겨울 울 혼방 롱코트",
                "프리미엄 울 혼방 롱코트", "따뜻한 겨울을 위한 롱코트");

        // enrichedQuery = "강아지 산책용 방수 가방 백팩" → 방수 가방 상품과 동일 → kNN 일치
        whenIntentStructured("비 올 때 강아지 산책하면서 쓸 가방", "강아지 산책용 방수 가방 백팩");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("비 올 때 강아지 산책하면서 쓸 가방");
            assertThat(result).contains(waterproofBag.getProjectId());
            assertThat(result).doesNotContain(coat.getProjectId());
        });
    }

    @Test
    @DisplayName("'캠핑 가서 밤에 쓸 밝은 거' - enrichedQuery로 캠핑 조명만 Hit, 반팔 의류 탈락")
    void campingNightLight_enrichedQuery_findsCampingLantern() {
        Project campingLantern = indexed("캠핑 야외 LED 랜턴 조명",
                "아웃도어 캠핑용 LED 랜턴", "캠핑 밤 야간 밝은 LED 랜턴 조명 아웃도어");
        Project tshirt = indexed("여름 쿨링 반팔 의류",
                "여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 시원하게 보내는 쿨링 반팔 티셔츠");

        whenIntentStructured("캠핑 가서 밤에 쓸 밝은 거", "캠핑 야외 LED 랜턴 조명");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("캠핑 가서 밤에 쓸 밝은 거");
            assertThat(result).contains(campingLantern.getProjectId());
            assertThat(result).doesNotContain(tshirt.getProjectId());
        });
    }

    @Test
    @DisplayName("'편하게 출퇴근할 수 있는 가방' - enrichedQuery로 통근용 백팩만 Hit, 캠핑 조명 탈락")
    void commuterBag_enrichedQuery_findsCommuterBackpack() {
        Project commuterBag = indexed("출퇴근 통근용 가벼운 백팩 가방",
                "직장인 통근 백팩", "출퇴근 편한 가벼운 직장인 백팩 가방");
        Project campingLantern = indexed("캠핑 야외 LED 랜턴 조명",
                "아웃도어 캠핑용 LED 랜턴", "캠핑 야간 조명");

        whenIntentStructured("편하게 출퇴근할 수 있는 가방", "출퇴근 통근용 가벼운 백팩 가방");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("편하게 출퇴근할 수 있는 가방");
            assertThat(result).contains(commuterBag.getProjectId());
            assertThat(result).doesNotContain(campingLantern.getProjectId());
        });
    }

    @Test
    @DisplayName("'집에서 영화볼 때 필요한 기계' - enrichedQuery로 빔프로젝터만 Hit, 텀블러 탈락")
    void homeMovieMachine_enrichedQuery_findsBimProjector() {
        Project projector = indexed("홈시네마 빔프로젝터 영상 기기",
                "4K 홈시네마 빔프로젝터", "집에서 영화 감상을 위한 홈시네마 빔프로젝터");
        Project tumbler = indexed("친환경 스테인리스 텀블러",
                "보온보냉 스테인리스 텀블러", "오래 보온되는 친환경 스테인리스 텀블러");

        whenIntentStructured("집에서 영화볼 때 필요한 기계", "홈시네마 빔프로젝터 영상 기기");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("집에서 영화볼 때 필요한 기계");
            assertThat(result).contains(projector.getProjectId());
            assertThat(result).doesNotContain(tumbler.getProjectId());
        });
    }

    // ── 3. enrichedQuery 경로 검증 ────────────────────────────────────────────

    @Test
    @DisplayName("BM25는 항상 원본 쿼리 텍스트를 사용한다 — enrichedQuery가 BM25에 영향을 주지 않는다")
    void bm25_alwaysUsesOriginalQuery_notEnrichedQuery() {
        // BM25는 trimmedKeyword(원본)을 사용하므로 원본 쿼리에 있는 단어로만 매치됨
        Project matching = indexed("반팔 티셔츠 여름 쿨링",
                "여름에 입기 좋은 반팔", "여름 반팔 티셔츠 쿨링");  // "반팔"이 title에 있음
        Project other = indexed("겨울 롱코트 울 방한",
                "프리미엄 울 혼방 롱코트", "겨울 방한 롱코트");

        // enrichedQuery를 완전히 다른 텍스트로 설정해도
        // BM25에서 원본 "반팔"이 matching을 찾음
        whenIntentStructured("반팔", "전혀다른내용의검색어텍스트xyz");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("반팔");
            // BM25는 "반팔"로 matching을 찾아야 함
            assertThat(result).contains(matching.getProjectId());
            // other는 "반팔"을 포함하지 않으므로 BM25에서 탈락
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("LLM 실패 시 passThrough로 기존 BM25 동작이 유지된다 — kNN 없이 BM25만 동작")
    void llmFallback_searchStillWorksViaBm25() {
        // BM25는 정확 어휘 매치이므로 원본 쿼리 "강아지"로 강아지 상품을 찾아야 함
        Project dogProduct = indexed("강아지 수제 간식 세트",
                "강아지 전용 수제 간식", "강아지가 좋아하는 수제 간식");
        Project catProduct = indexed("고양이 원목 캣타워",
                "고양이 원목 캣타워", "고양이를 위한 원목 캣타워");

        // LLM 실패 → passThrough → enrichedQuery = "강아지"
        whenIntentPassThrough("강아지");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("강아지");
            assertThat(result).contains(dogProduct.getProjectId());
            assertThat(result).doesNotContain(catProduct.getProjectId());
        });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /**
     * enrichedQuery == 원본 쿼리인 passThrough 상태를 설정한다.
     * kNN 벡터 = 원본 자연어 쿼리의 랜덤 벡터 → 상품 title과 다른 텍스트이므로 코사인 유사도 ≈ 0.
     */
    private void whenIntentPassThrough(String query) {
        when(queryIntentAnalyzer.analyze(anyString()))
                .thenAnswer(inv -> QueryIntent.passThrough(inv.getArgument(0)));
    }

    /**
     * enrichedQuery를 원하는 값으로 고정하는 구조화된 QueryIntent를 설정한다.
     * enrichedQuery가 상품 title과 동일하면 kNN 완전 일치 → 해당 상품 결과에 포함.
     * enrichedQuery가 다른 상품 title과 다르면 코사인 유사도 ≈ 0 → kNN 탈락.
     */
    private void whenIntentStructured(String originalQuery, String enrichedQuery) {
        QueryIntent intent = new QueryIntent(
                null, null, null, null, null, null,
                List.of(), List.of(),
                enrichedQuery
        );
        when(queryIntentAnalyzer.analyze(originalQuery)).thenReturn(intent);
        // 다른 모든 쿼리에 대해서도 동일 처리 (BM25 쿼리와 동일한 query가 다시 analyze될 때)
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);
    }

    /**
     * 상품을 DB에 저장하고 ES에 색인한다.
     * 임베딩 텍스트는 title에서 결정적 랜덤 벡터를 생성하므로
     * enrichedQuery와 title이 동일하면 kNN 완전 일치가 발생한다.
     */
    private Project indexed(String title, String summary, String description) {
        Project project = Project.register(
                1L, UUID.randomUUID(), null, title, 1L, summary, description,
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(project);
        savedProjectIds.add(saved.getProjectId());
        adapter.index(saved);
        return saved;
    }
}
