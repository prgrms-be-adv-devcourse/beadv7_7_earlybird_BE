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
 * Query Intent 및 2-Stage Compatibility 고도화 검색 품질 검증.
 */
@SpringBootTest
class QueryIntentSearchQualityTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;

    @MockitoBean
    private QueryIntentAnalyzer queryIntentAnalyzer;

    private final List<Long> savedProjectIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        savedProjectIds.forEach(adapter::remove);
        savedProjectIds.clear();
    }

    // ── 1. "여름용 옷 → 롱코트 감점" 핵심 케이스 ────────────────────────────

    @Test
    @DisplayName("[핵심] 여름용 옷 검색 시 - Compatibility Layer로 여름 상품 우선 랭크 및 겨울 상품 감점")
    void summerClothesSearch_compatibility_prioritizesSummerOverWinter() {
        Project summerTshirt = indexed("여름 쿨링 반팔 의류",
                "여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 시원하게 보내는 쿨링 반팔 티셔츠");
        Project winterCoat = indexed("겨울 울 혼방 롱코트",
                "프리미엄 울 혼방 롱코트", "따뜻한 겨울을 위한 울 혼방 오버핏 롱코트");

        Requirement req = new Requirement("여름용", "usage_or_context", true, List.of("겨울", "울 롱코트", "방한"));
        QueryIntent intent = new QueryIntent(
                "옷", List.of(req), "옷", "여름", null, null, null, null,
                List.of("여름용"), List.of(), "여름 쿨링 반팔 의류"
        );
        when(queryIntentAnalyzer.analyze("여름 옷")).thenReturn(intent);
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("여름 옷");
            assertThat(result).contains(summerTshirt.getProjectId());
            assertThat(result).doesNotContain(winterCoat.getProjectId());
        });
    }

    @Test
    @DisplayName("[핵심] 겨울 옷 검색 시 - 겨울 코트 우선 랭크")
    void winterClothesSearch_compatibility_prioritizesWinterOverSummer() {
        Project summerTshirt = indexed("여름 쿨링 반팔 의류",
                "여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 시원하게 보내는 쿨링 반팔 티셔츠");
        Project winterCoat = indexed("겨울 울 혼방 롱코트",
                "프리미엄 울 혼방 롱코트", "따뜻한 겨울을 위한 울 혼방 오버핏 롱코트");

        Requirement req = new Requirement("겨울용", "usage_or_context", true, List.of("여름", "반팔", "린넨", "쿨링"));
        QueryIntent intent = new QueryIntent(
                "옷", List.of(req), "옷", "겨울", null, null, null, null,
                List.of("겨울용"), List.of(), "겨울 울 혼방 롱코트"
        );
        when(queryIntentAnalyzer.analyze("겨울 옷")).thenReturn(intent);
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("겨울 옷");
            assertThat(result).contains(winterCoat.getProjectId());
            assertThat(result).doesNotContain(summerTshirt.getProjectId());
        });
    }

    // ── 2. 다양한 자연어 검색 케이스 ───────────────────────────

    @Test
    @DisplayName("'가벼운 출퇴근용 가방' - 직장인 통근 백팩 매칭")
    void commuterBag_findsCommuterBackpack() {
        Project commuterBag = indexed("출퇴근 가방",
                "직장인 통근 백팩", "출퇴근 편한 가벼운 직장인 백팩 가방");
        Project campingLantern = indexed("캠핑 야외 LED 조명",
                "아웃도어 캠핑용 LED 랜턴", "캠핑 야간 조명");

        Requirement req1 = new Requirement("가벼운", "characteristic", false, List.of("무거운", "헤비"));
        Requirement req2 = new Requirement("출퇴근용", "usage_or_context", true, List.of("캠핑", "아웃도어"));
        QueryIntent intent = new QueryIntent(
                "가방", List.of(req1, req2), "가방", null, null, "출퇴근", null, null,
                List.of("출퇴근용"), List.of("가벼운"), "출퇴근 가방"
        );
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("가방");
            assertThat(result).contains(commuterBag.getProjectId());
            assertThat(result).doesNotContain(campingLantern.getProjectId());
        });
    }

    @Test
    @DisplayName("'조용한 사무용 키보드' - 저소음 키보드 매칭")
    void quietKeyboard_findsQuietKeyboard() {
        Project quietKb = indexed("조용한 사무용 키보드",
                "무소음 저소음 적축 오피스 키보드", "사무실에서 쓰기 좋은 조용한 키보드");
        Project clickyKb = indexed("청축 게이밍 키보드",
                "찰칵거리는 경쾌한 타건음의 게이밍 키보드", "화려한 RGB 게임용 키보드");

        Requirement req = new Requirement("조용한", "characteristic", true, List.of("청축", "소음", "시끄러운"));
        QueryIntent intent = new QueryIntent(
                "키보드", List.of(req), "키보드", null, null, "사무용", null, null,
                List.of("조용한"), List.of(), "조용한 사무용 키보드"
        );
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("키보드");
            assertThat(result).contains(quietKb.getProjectId());
            if (result.contains(clickyKb.getProjectId())) {
                assertThat(result.indexOf(quietKb.getProjectId()))
                        .isLessThan(result.indexOf(clickyKb.getProjectId()));
            }
        });
    }

    // ── 3. enrichedQuery 경로 및 폴백 검증 ────────────────────────────────────────────

    @Test
    @DisplayName("BM25는 항상 원본 쿼리 텍스트를 사용한다")
    void bm25_alwaysUsesOriginalQuery() {
        Project matching = indexed("반팔 티셔츠 여름 쿨링",
                "여름에 입기 좋은 반팔", "여름 반팔 티셔츠 쿨링");
        Project other = indexed("겨울 롱코트 울 방한",
                "프리미엄 울 혼방 롱코트", "겨울 방한 롱코트");

        whenIntentStructured("반팔", "전혀다른내용의검색어텍스트xyz");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("반팔");
            assertThat(result).contains(matching.getProjectId());
            assertThat(result).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("LLM 실패 시 passThrough로 기존 BM25 동작이 유지된다")
    void llmFallback_searchStillWorksViaBm25() {
        Project dogProduct = indexed("강아지 수제 간식 세트",
                "강아지 전용 수제 간식", "강아지가 좋아하는 수제 간식");
        Project catProduct = indexed("고양이 원목 캣타워",
                "고양이 원목 캣타워", "고양이를 위한 원목 캣타워");

        whenIntentPassThrough("강아지");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("강아지");
            assertThat(result).contains(dogProduct.getProjectId());
            assertThat(result).doesNotContain(catProduct.getProjectId());
        });
    }

    private void whenIntentPassThrough(String query) {
        when(queryIntentAnalyzer.analyze(anyString()))
                .thenAnswer(inv -> QueryIntent.passThrough(inv.getArgument(0)));
    }

    private void whenIntentStructured(String originalQuery, String enrichedQuery) {
        QueryIntent intent = new QueryIntent(
                null, null, null, null, null, null,
                List.of(), List.of(),
                enrichedQuery
        );
        when(queryIntentAnalyzer.analyze(originalQuery)).thenReturn(intent);
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);
    }

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
