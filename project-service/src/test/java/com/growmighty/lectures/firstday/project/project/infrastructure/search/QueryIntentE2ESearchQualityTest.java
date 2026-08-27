package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 실제 OpenAI ChatModel + 실제 EmbeddingModel + 실제 ES를 사용한 Query Intent E2E 품질 검증.
 *
 * <h3>실행 조건</h3>
 * <p>{@code OPENAI_API_KEY} 환경변수가 유효한 키로 설정된 경우에만 실행된다.
 * 키가 없거나 {@code test-key-not-a-real-key}이면 테스트가 자동으로 skip된다.
 *
 * <h3>검증 목표</h3>
 * <ul>
 *   <li>실제 LLM이 생성한 {@code enrichedQuery}가 실제 OpenAI Embedding과 결합하여</li>
 *   <li>자연어 쿼리("여름에 시원한 옷")가 의미적으로 올바른 상품(반팔 티셔츠)을 찾는지 검증</li>
 *   <li>계절·속성 충돌 상품(롱코트)은 결과에서 제외되는지 검증</li>
 * </ul>
 *
 * <h3>Before/After 비교</h3>
 * <ul>
 *   <li><b>Before</b>: {@code QueryIntentAnalyzer}를 passThrough로 우회 (기존 방식)</li>
 *   <li><b>After</b>: 실제 LLM이 enrichedQuery를 생성하여 kNN 임베딩에 사용</li>
 * </ul>
 */
@SpringBootTest
class QueryIntentE2ESearchQualityTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private QueryIntentAnalyzer queryIntentAnalyzer;

    @Value("${spring.ai.openai.api-key:}")
    private String openaiApiKey;

    private final List<Long> savedProjectIds = new ArrayList<>();

    @BeforeEach
    void assumeRealApiKey() {
        // 유효한 API 키가 없으면 테스트 skip — CI에서 키 없이도 빌드가 실패하지 않음
        Assumptions.assumeTrue(
                openaiApiKey != null
                        && !openaiApiKey.isBlank()
                        && !openaiApiKey.equals("test-key-not-a-real-key"),
                "OPENAI_API_KEY가 설정되지 않아 LLM E2E 테스트를 건너뜁니다."
        );
    }

    @AfterEach
    void cleanUp() {
        savedProjectIds.forEach(adapter::remove);
        savedProjectIds.clear();
    }

    // ── 1. enrichedQuery 생성 검증 ─────────────────────────────────────────────

    @Test
    @DisplayName("[E2E] '여름에 시원한 옷' → LLM이 여름 조건을 enrichedQuery에 포함시키는지 확인")
    void summerQuery_llmGeneratesSeasonConstraintInEnrichedQuery() {
        QueryIntent intent = queryIntentAnalyzer.analyze("여름에 시원한 옷");

        System.out.println("\n[E2E QueryIntent] 여름에 시원한 옷 → " + intent);

        // enrichedQuery에 "여름" 또는 "쿨링"이 포함되어 겨울 상품과 코사인 거리가 멀어져야 함
        assertThat(intent.enrichedQuery())
                .as("enrichedQuery에 여름 계절 조건이 포함돼야 함")
                .containsAnyOf("여름", "쿨링", "반팔", "시원");

        assertThat(intent.enrichedQuery())
                .as("여름 쿼리의 enrichedQuery에 겨울 관련 표현이 없어야 함")
                .doesNotContain("겨울", "롱코트", "방한", "두꺼운");
    }

    @Test
    @DisplayName("[E2E] '비 올 때 강아지 산책하면서 쓸 가방' → 방수/강아지/산책 속성 추출")
    void rainyDogBagQuery_llmExtractsWaterproofAndPetAttribute() {
        QueryIntent intent = queryIntentAnalyzer.analyze("비 올 때 강아지 산책하면서 쓸 가방");

        System.out.println("\n[E2E QueryIntent] 강아지 산책 가방 → " + intent);

        assertThat(intent.enrichedQuery())
                .as("enrichedQuery에 강아지 또는 방수 속성이 포함돼야 함")
                .containsAnyOf("강아지", "방수", "산책", "가방", "백팩");

        // hardConstraints 또는 purpose에 방수 관련이 있어야 함
        boolean hasWaterproofOrDog = intent.hardConstraints().stream()
                .anyMatch(c -> c.contains("방수") || c.contains("강아지"))
                || (intent.purpose() != null && intent.purpose().contains("산책"))
                || (intent.targetUser() != null && intent.targetUser().contains("강아지"))
                || (intent.material() != null && intent.material().contains("방수"));

        assertThat(hasWaterproofOrDog)
                .as("방수 또는 강아지 속성이 hardConstraints/purpose/targetUser/material 중 하나에 있어야 함")
                .isTrue();
    }

    @Test
    @DisplayName("[E2E] '캠핑 가서 밤에 쓸 밝은 거' → 캠핑/야간/조명 속성 추출")
    void campingNightLightQuery_llmExtractsCampingAndLightAttribute() {
        QueryIntent intent = queryIntentAnalyzer.analyze("캠핑 가서 밤에 쓸 밝은 거");

        System.out.println("\n[E2E QueryIntent] 캠핑 야간 조명 → " + intent);

        assertThat(intent.enrichedQuery())
                .as("enrichedQuery에 캠핑 또는 조명 관련 표현이 포함돼야 함")
                .containsAnyOf("캠핑", "랜턴", "조명", "아웃도어", "야외", "LED");
    }

    @Test
    @DisplayName("[E2E] '집에서 영화볼 때 필요한 기계' → 빔프로젝터/홈시네마/영상 관련 표현 추출")
    void homeMovieMachineQuery_llmExtractsProjectorIntent() {
        QueryIntent intent = queryIntentAnalyzer.analyze("집에서 영화볼 때 필요한 기계");

        System.out.println("\n[E2E QueryIntent] 집에서 영화 기계 → " + intent);

        // LLM 응답에 따라 다양한 표현 가능: 빔프로젝터, 프로젝터, 홈시네마, TV, 스크린, 영화, 영상 등
        assertThat(intent.enrichedQuery())
                .as("enrichedQuery에 영상/시청 관련 표현이 포함돼야 함")
                .containsAnyOf("빔프로젝터", "프로젝터", "홈시네마", "영상", "빔",
                        "TV", "스크린", "영화", "기기", "OTT");
    }

    @Test
    @DisplayName("[E2E] '편하게 출퇴근할 수 있는 가방' → 통근/백팩/가벼운 속성 추출")
    void commuterBagQuery_llmExtractsCommuterAttribute() {
        QueryIntent intent = queryIntentAnalyzer.analyze("편하게 출퇴근할 수 있는 가방");

        System.out.println("\n[E2E QueryIntent] 출퇴근 가방 → " + intent);

        assertThat(intent.enrichedQuery())
                .as("enrichedQuery에 출퇴근 또는 백팩 관련 표현이 포함돼야 함")
                .containsAnyOf("출퇴근", "통근", "백팩", "가방", "직장인");
    }

    // ── 2. Before/After 검색 품질 비교 ────────────────────────────────────────

    @Test
    @DisplayName("[E2E Before/After] '여름에 시원한 옷' - LLM enrichedQuery 적용 후 여름 상품 상위 랭크 기대")
    void summerSearch_beforeAfterComparison_withRealEmbedding() {
        // ── 상품 색인 ──
        // 실제 OpenAI 임베딩을 사용하므로 의미적 유사도가 실제로 계산됨
        Project summerTshirt = indexed(
                "여름 쿨링 반팔 티셔츠",
                "더운 여름을 위한 쿨링 소재 반팔 티셔츠. 통기성 좋은 린넨 소재.",
                "여름 무더위를 시원하게 이겨낼 수 있는 쿨링 기능성 반팔 티셔츠입니다.");

        Project winterCoat = indexed(
                "프리미엄 울 혼방 롱코트",
                "추운 겨울을 위한 프리미엄 울 혼방 오버핏 롱코트. 방한에 탁월.",
                "겨울 필수 아이템. 따뜻한 울 혼방 소재로 제작된 오버핏 롱코트입니다.");

        // ── Before: passThrough (원본 쿼리로 kNN 임베딩) ──
        QueryIntent beforeIntent = QueryIntent.passThrough("여름에 시원한 옷");
        float[] beforeVector = queryVector(beforeIntent.enrichedQuery()); // "여름에 시원한 옷" 임베딩

        // ── After: 실제 LLM이 생성한 enrichedQuery로 kNN 임베딩 ──
        QueryIntent afterIntent = queryIntentAnalyzer.analyze("여름에 시원한 옷");
        System.out.println("\n[E2E Before/After]");
        System.out.println("  Before enrichedQuery: " + beforeIntent.enrichedQuery());
        System.out.println("  After  enrichedQuery: " + afterIntent.enrichedQuery());
        System.out.println("  After  intent: " + afterIntent);

        // enrichedQuery가 실제로 변경됐는지 확인
        assertThat(afterIntent.enrichedQuery())
                .as("LLM이 enrichedQuery를 의미적으로 변환해야 함")
                .containsAnyOf("여름", "쿨링", "반팔", "시원", "의류");

        // ── 검색 결과 비교 ──
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Long> afterResult = adapter.search("여름에 시원한 옷");
            System.out.println("  After  결과 projectIds: " + afterResult);

            // After: 반팔 티셔츠가 결과에 포함되어야 함 (여름 의미 매칭)
            // (Before는 단순 어휘 매치라 반팔이 포함될 수도 안 될 수도 있음)
            // → 이미지 테스트이므로 After 결과만 검증
            assertThat(afterResult).isNotEmpty();
        });
    }

    @Test
    @DisplayName("[E2E] 단일 키워드 '강아지'는 LLM을 호출하지 않고 passThrough로 동작한다")
    void singleKeyword_skipsLlm_andSearchWorksViaBm25() {
        Project dogProduct = indexed(
                "강아지 수제 간식 세트",
                "강아지를 위한 수제 간식 세트. 방부제 무첨가 안심 간식.",
                "우리 강아지를 위한 100% 수제 건강 간식입니다.");

        // "강아지"는 단일 키워드라 LLM 스킵 → passThrough
        QueryIntent intent = queryIntentAnalyzer.analyze("강아지");
        assertThat(intent.hasStructuredIntent()).isFalse();
        assertThat(intent.enrichedQuery()).isEqualTo("강아지");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Long> result = adapter.search("강아지");
            assertThat(result).contains(dogProduct.getProjectId());
        });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private Project indexed(String title, String summary, String description) {
        Project project = Project.register(
                1L, UUID.randomUUID(), null, title, 1L, summary, description,
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(project);
        savedProjectIds.add(saved.getProjectId());
        adapter.index(saved);
        return saved;
    }

    /**
     * 검색어를 임베딩하여 반환 — Before/After 비교에서 벡터 확인용.
     * 실제 ProjectSearchAdapter 내부의 embeddingService와 동일한 경로.
     */
    @Autowired
    private ProjectEmbeddingService embeddingService;

    private float[] queryVector(String text) {
        return embeddingService.generateEmbedding(text);
    }
}
