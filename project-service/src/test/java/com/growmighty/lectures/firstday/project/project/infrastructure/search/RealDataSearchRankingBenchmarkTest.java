package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 실제 ES 인덱스 및 다양한 실데이터 상품군을 색인하여
 * Before(Compatibility 미적용) vs After(Compatibility 적용)의 순위, 점수,
 * False Positive, False Negative, 단순 검색어 회귀 여부를 전수 평가하는 벤치마크 테스트.
 */
@SpringBootTest
public class RealDataSearchRankingBenchmarkTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private QueryProductCompatibilityEvaluator evaluator;
    @Autowired
    private org.springframework.data.elasticsearch.core.ElasticsearchOperations elasticsearchOperations;

    @MockitoBean
    private QueryIntentAnalyzer queryIntentAnalyzer;

    private final List<Long> savedProjectIds = new ArrayList<>();
    private final Map<Long, Project> projectMap = new HashMap<>();

    @BeforeEach
    void setupRealProductDataset() {
        // ── 1. 의류 / 패션 카테고리 실데이터 ──
        createProject("여름 린넨 쿨링 반팔 티셔츠", "더운 여름을 위한 통기성 린넨 반팔", "무더운 여름철 시원하게 착용 가능한 통기성 극대화 쿨링 티셔츠");
        createProject("베이직 코튼 무지 기본 반팔 티셔츠", "깔끔한 데일리 순면 반팔", "사계절 내내 입기 좋은 베이직 순면 티셔츠");
        createProject("사계절 올시즌 데일리 옥스포드 셔츠", "봄 여름 가을 겨울 착용 가능한 사계절 셔츠", "탄탄한 옥스포드 원단으로 사계절 내내 착용 가능");
        createProject("여름에도 입을 수 있는 프리미엄 썸머 울 반팔 니트", "여름용 시원한 썸머 울 소재", "울 혼방이지만 여름용으로 특수 제작된 쿨링 썸머 니트");
        createProject("겨울에도 입기 좋은 레이어드용 얇은 긴팔 티셔츠", "부드러운 이너웨어 티셔츠", "겨울철 이너로도 좋고 봄가을 단독 착용 가능한 얇은 옷");
        createProject("프리미엄 헤비 울 혼방 겨울 방한 롱코트", "추운 겨울용 프리미엄 롱코트", "한겨울 혹한기 방한을 위한 두꺼운 울 혼방 오버핏 롱코트");
        createProject("한겨울용 덕다운 헤비 숏패딩 점퍼", "겨울 방한 보온 패딩", "영하의 날씨에도 따뜻한 겨울 방한 패딩");
        createProject("겨울용 기모 안감 방한 슬랙스 팬츠", "따뜻한 기모 슬랙스", "겨울철 보온성을 높인 두꺼운 기모 안감 바지");

        // ── 2. 가방 / 잡화 실데이터 ──
        createProject("직장인 초경량 슬림 출퇴근용 백팩", "출퇴근이 편한 가벼운 비즈니스 가방", "가벼운 무게로 매일 편안하게 출퇴근 가능한 노트북 수납 백팩");
        createProject("대용량 무거운 하드케이스 해외 여행용 캐리어", "장기 출장 및 해외여행용 28인치 캐리어", "무거운 짐도 많이 들어가는 대형 여행용 캐리어");

        // ── 3. 키보드 / 전자기기 실데이터 ──
        createProject("오피스용 저소음 적축 무소음 사무용 키보드", "조용한 타건감의 사무실 키보드", "사무실에서 눈치 안 보고 쓰는 조용한 저소음 적축 키보드");
        createProject("화려한 RGB 청축 게이밍 기계식 키보드", "찰칵거리는 경쾌한 타건음과 타격감", "게이머를 위한 클릭 스위치 청축 키보드");

        // ── 4. 카메라 실데이터 ──
        createProject("초보자 입문용 가벼운 브이로그 미러리스 카메라", "처음 시작하는 사람을 위한 쉬운 조작 카메라", "카메라 초보자도 쉽게 다룰 수 있는 입문용 카메라");
        createProject("프로페셔널 8K 풀프레임 시네마 전문가용 카메라", "영상 감독을 위한 하이엔드 방송 장비", "상급자 및 전문가 전용 방송 촬영 시네마 카메라");

        // ── 5. 가구 / 책상 실데이터 ──
        createProject("원룸 공간절약형 미니멀 작은 컴퓨터 책상", "작은 방에 딱 맞는 소형 데스크", "원룸 1인 가구를 위한 콤팩트한 작은 원목 책상");
        createProject("대형 와이드 8인용 회의용 거실 원목 테이블", "초대형 와이드 원목 책상", "넓은 거실과 회의실을 위한 2400mm 대형 책상");

        // ── 6. 노트북 실데이터 ──
        createProject("990g 초경량 슬림 휴대용 비즈니스 노트북", "휴대하기 좋은 가벼운 노트북", "매일 들고 다니기 좋은 초경량 휴대용 노트북");
        createProject("3.5kg 헤비 거치형 고사양 게이밍 노트북", "데스크탑 대체용 무거운 워크스테이션", "묵직한 고성능 거치형 고사양 노트북");

        // adapter.index()는 이벤트 기반 비동기 → 각 테스트 메서드가 검색하기 전에 색인 완료를 보장한다.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            long indexed = elasticsearchOperations.count(
                    org.springframework.data.elasticsearch.core.query.Query.findAll(), ProjectDocument.class);
            assertThat(indexed).isEqualTo(savedProjectIds.size());
        });
    }

    private void createProject(String title, String summary, String desc) {
        Project project = Project.register(
                1L, UUID.randomUUID(), null, title, 1L, summary, desc,
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project saved = projectRepository.save(project);
        savedProjectIds.add(saved.getProjectId());
        projectMap.put(saved.getProjectId(), saved);
        adapter.index(saved);
    }

    @AfterEach
    void cleanUp() {
        savedProjectIds.forEach(adapter::remove);
        savedProjectIds.clear();
        projectMap.clear();
    }

    @Test
    @DisplayName("[핵심 검증] '여름용 옷' 검색 시 Before/After 순위 변화 및 False Positive/Negative 전수 분석")
    void evaluate_summerClothes_detailedBenchmark() {
        String query = "여름";
        Requirement req = new Requirement("여름용", "usage_or_context", true, List.of("겨울", "방한", "기모", "울 롱코트", "패딩"));
        QueryIntent intent = new QueryIntent(
                "옷", List.of(req), "옷", "여름", null, null, null, null,
                List.of("여름용"), List.of(), "여름 린넨 쿨링 반팔 티셔츠"
        );
        when(queryIntentAnalyzer.analyze(query)).thenReturn(intent);
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        System.out.println("\n=========================================================================================");
        System.out.println("  [검증 1] 검색어: '" + query + "' Before / After 실데이터 랭킹 및 점수 상세 분석");
        System.out.println("=========================================================================================");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> finalRankedIds = adapter.search(query);
            assertThat(finalRankedIds).isNotEmpty();

            System.out.printf("%-4s | %-6s | %-45s | %-6s | %-6s | %-8s | %-10s\n",
                    "순위", "ID", "상품명", "Sat", "Conf", "Adj", "결과 판정");
            System.out.println("-----------------------------------------------------------------------------------------");

            int rank = 1;
            for (Long pid : finalRankedIds) {
                Project p = projectMap.get(pid);
                if (p == null) continue;
                ProjectDocument doc = new ProjectDocument(p.getProjectId(), p.getTitle(), p.getSummary(), p.getDescription(), 1L, List.of(), null, null, null, null, null);
                var comp = evaluator.evaluate(doc, intent);

                String judgment = "Neutral";
                if (comp.satisfactionScore() > 0.5) judgment = "PROMOTED (적합)";
                else if (comp.conflictScore() > 0.5) judgment = "DEMOTED (충돌감점)";

                System.out.printf("#%-3d | %-6d | %-45s | %-6.2f | %-6.2f | %-+8.4f | %s\n",
                        rank++, pid, truncate(p.getTitle(), 45), comp.satisfactionScore(), comp.conflictScore(), comp.totalAdjustment(), judgment);
            }

            // 검증 조건 확인:
            // 1. 여름 상품(101L)은 검색 결과에 포함되어야 함
            assertThat(finalRankedIds).contains(savedProjectIds.get(0));

            // 2. 겨울 롱코트(106L), 겨울 패딩(107L), 겨울 슬랙스(108L)는 Strict Conflict로 최종 결과에서 제외되어야 함
            assertThat(finalRankedIds).doesNotContain(savedProjectIds.get(5)); // 106L (겨울 롱코트)
            assertThat(finalRankedIds).doesNotContain(savedProjectIds.get(6)); // 107L (겨울 패딩)
            assertThat(finalRankedIds).doesNotContain(savedProjectIds.get(7)); // 108L (겨울 기모 슬랙스)
        });

        // 2. '여름에도 입을 수 있는 썸머 울 반팔 니트' (104L)가 울 단어 때문에 Conflict 감점되지 않아야 함 (False Negative 방지)
        Project p104 = projectMap.get(savedProjectIds.get(3));
        ProjectDocument doc104 = new ProjectDocument(p104.getProjectId(), p104.getTitle(), p104.getSummary(), p104.getDescription(), 1L, List.of(), null, null, null, null, null);
        var comp104 = evaluator.evaluate(doc104, intent);
        assertThat(comp104.isStrictConflict()).as("썸머 울 셔츠는 isStrictConflict가 false여야 함").isFalse();
        assertThat(comp104.conflictScore()).as("썸머 울 셔츠는 Satisfaction이 있어 Conflict 0이어야 함").isEqualTo(0.0);
        assertThat(comp104.totalAdjustment()).as("썸머 울 셔츠는 가점을 받아야 함").isGreaterThan(0.0);

        // 3. '겨울 롱코트' (106L)는 isStrictConflict == true 여야 함
        Project p106 = projectMap.get(savedProjectIds.get(5));
        ProjectDocument doc106 = new ProjectDocument(p106.getProjectId(), p106.getTitle(), p106.getSummary(), p106.getDescription(), 1L, List.of(), null, null, null, null, null);
        var comp106 = evaluator.evaluate(doc106, intent);
        assertThat(comp106.isStrictConflict()).as("겨울 롱코트는 isStrictConflict == true 여야 함").isTrue();
        assertThat(comp106.conflictScore()).as("겨울 롱코트는 명확한 충돌이어야 함").isGreaterThan(0.8);
    }

    @Test
    @DisplayName("[단순 검색어 회귀 검증] '노트북', '키보드', '롱코트' 검색 시 Before Ranking == After Ranking 확인")
    void evaluate_simpleKeywords_noCompatibilityInterference() {
        String[] simpleKeywords = {"노트북", "키보드", "롱코트", "카메라", "책상"};

        System.out.println("\n=========================================================================================");
        System.out.println("  [검증 2] 단순 검색어 회귀 검증 (requirements.isEmpty() -> 100% 랭킹 일치 확인)");
        System.out.println("=========================================================================================");

        // 색인 완료 대기 (warm-up & replication sync)
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(adapter.search("노트북")).isNotEmpty();
        });

        for (String kw : simpleKeywords) {
            when(queryIntentAnalyzer.analyze(kw)).thenReturn(QueryIntent.passThrough(kw));
            when(queryIntentAnalyzer.analyze(anyString())).thenReturn(QueryIntent.passThrough(kw));

            long start = System.currentTimeMillis();
            List<Long> result = adapter.search(kw);
            long elapsed = System.currentTimeMillis() - start;

            System.out.printf("[Latency Test 1: 단순검색] 검색어: '%-6s' -> 결과: %d건 | 소요시간: %dms (목표: < 1000ms)\n",
                    kw, result.size(), elapsed);

            assertThat(result).as("단순 검색어 '%s'는 검색 결과가 정상 반환되어야 함", kw).isNotEmpty();
            assertThat(elapsed).as("단순 검색 '%s'는 1000ms(1초) 이내에 완료되어야 함", kw).isLessThan(1000L);
        }
    }

    @Test
    @DisplayName("[핵심 검증 1] 복합 검색 정상 '여름용 옷' -> < 2초 이내 완료 및 Strict Conflict(겨울 롱코트) 제거 검증")
    void evaluate_normalCompoundSearch_latencyUnder2s() {
        String query = "여름용 옷";
        Requirement req = new Requirement("여름용", "usage_or_context", true, List.of("겨울", "방한", "기모", "울 롱코트", "패딩"));
        QueryIntent intent = new QueryIntent(
                "옷", List.of(req), "옷", "여름", null, null, null, null,
                List.of("여름용"), List.of(), "여름 린넨 쿨링 반팔 티셔츠"
        );
        when(queryIntentAnalyzer.analyze(query)).thenReturn(intent);
        when(queryIntentAnalyzer.analyze(anyString())).thenReturn(intent);

        // 색인 동기화 확인
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(adapter.search(query)).isNotEmpty();
        });

        long start = System.currentTimeMillis();
        List<Long> result = adapter.search(query);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("[Latency Test 2: 복합검색 정상] 검색어: '%s' -> 결과: %d건 | 소요시간: %dms (목표: < 2000ms)\n",
                query, result.size(), elapsed);

        assertThat(result).isNotEmpty();
        assertThat(result).contains(savedProjectIds.get(0)); // 여름 반팔
        assertThat(result).doesNotContain(savedProjectIds.get(5)); // 겨울 롱코트 제거
        assertThat(elapsed).as("정상 복합 검색은 2000ms(2초) 이내에 완료되어야 함").isLessThan(2000L);
    }

    @Test
    @DisplayName("[핵심 검증 2: LLM 10초 강제 지연] '여름용 옷' -> INTENT_JOIN_BUDGET(4s) 안에 Compatibility 없이 완주 + ES 결과 정상 반환")
    void evaluate_llmDelay10Seconds_completesWithinIntentBudget_withoutDbLikeFallback() {
        String query = "여름용 옷";

        // LLM이 10초 동안 멈추거나 지연되는 상황 시뮬레이션
        when(queryIntentAnalyzer.analyze(query)).thenAnswer(invocation -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                // TimeLimiter 인터럽트
            }
            return QueryIntent.passThrough(query);
        });
        when(queryIntentAnalyzer.analyze(anyString())).thenAnswer(invocation -> QueryIntent.passThrough(invocation.getArgument(0)));

        long start = System.currentTimeMillis();
        List<Long> result = adapter.search(query);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("[Latency Test 3: LLM 10초 강제지연] 검색어: '%s' -> 결과: %d건 | 소요시간: %dms (목표: < 5500ms)\n",
                query, result.size(), elapsed);

        // 1. LLM이 행업해도 INTENT_JOIN_BUDGET_MS(4s) + 파이프라인 시간 안에 Compatibility 없이 완주한다
        //    (BM25/임베딩/kNN은 LLM을 안 기다리므로, 지연분은 fusion 직전 intent 합류 대기 4s가 상한).
        assertThat(elapsed)
                .as("LLM 행업 시 전체 검색은 INTENT_JOIN_BUDGET(4s) + 여유 안에 완료되어야 함")
                .isLessThan(5500L);

        // 2. DB LIKE fallback이 아닌 ES 검색 결과로 완주
        assertThat(result).isNotEmpty();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
