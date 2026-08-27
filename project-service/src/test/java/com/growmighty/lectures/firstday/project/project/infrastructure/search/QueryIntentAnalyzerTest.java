package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link QueryIntentAnalyzer} 단위 테스트.
 *
 * <p>실제 LLM 호출 없이 ChatModel Mock을 사용하여
 * JSON 파싱, Fallback 동작, Skip 조건, 캐싱, enrichedQuery 생성을 검증한다.
 *
 * <h3>핵심 설계 검증 항목</h3>
 * <ul>
 *   <li>LLM은 검색어 전처리(enrichedQuery 생성)에만 사용, 검색 결과 평가에 사용하지 않음</li>
 *   <li>단일 키워드·짧은 쿼리 → LLM 호출 없이 passThrough</li>
 *   <li>동일 쿼리 반복 → 캐시 히트로 LLM 1회만 호출</li>
 *   <li>계절 등 하드 조건이 enrichedQuery에 반영되어 충돌 상품 벡터 거리 확대</li>
 * </ul>
 */
class QueryIntentAnalyzerTest {

    private static final String CIRCUIT_BREAKER_ID = "projectQueryIntent";

    private final ChatModel chatModel = mock(ChatModel.class);
    private final CircuitBreakerFactory cbFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private QueryIntentAnalyzer analyzer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(cbFactory.create(CIRCUIT_BREAKER_ID)).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(inv -> {
            Supplier<Object> supplier = inv.getArgument(0);
            Function<Throwable, Object> fallback = inv.getArgument(1);
            try {
                return supplier.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
        analyzer = new QueryIntentAnalyzer(chatModel, cbFactory, objectMapper);
    }

    // ── 1. 기본 속성 추출 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("자연어 쿼리에서 상품 유형·계절·용도·대상 등 핵심 속성을 구조화하여 enrichedQuery를 생성한다")
    void analyze_naturalLanguageQuery_extractsStructuredIntent() {
        givenLlmReturns("""
                {
                  "productType": "셔츠",
                  "season": "여름",
                  "color": null,
                  "purpose": null,
                  "targetUser": "여성",
                  "material": null,
                  "hardConstraints": ["여름용", "여성용"],
                  "softPreferences": ["시원한"],
                  "enrichedQuery": "여름 쿨링 여성 반팔 셔츠 의류"
                }
                """);

        QueryIntent intent = analyzer.analyze("여름에 입기 좋은 시원한 여성용 셔츠");

        assertThat(intent.productType()).isEqualTo("셔츠");
        assertThat(intent.season()).isEqualTo("여름");
        assertThat(intent.targetUser()).isEqualTo("여성");
        assertThat(intent.hardConstraints()).containsExactly("여름용", "여성용");
        assertThat(intent.softPreferences()).containsExactly("시원한");
        assertThat(intent.enrichedQuery()).isEqualTo("여름 쿨링 여성 반팔 셔츠 의류");
        assertThat(intent.hasStructuredIntent()).isTrue();
    }

    @Test
    @DisplayName("상황 기반 쿼리('비 올 때 강아지 산책용 가방')에서 방수 속성·대상·용도를 추출하고 enrichedQuery를 생성한다")
    void analyze_situationQuery_extractsPurposeAndAttributeCorrectly() {
        givenLlmReturns("""
                {
                  "productType": "가방",
                  "season": null,
                  "color": null,
                  "purpose": "산책",
                  "targetUser": "강아지",
                  "material": "방수",
                  "hardConstraints": ["방수"],
                  "softPreferences": [],
                  "enrichedQuery": "강아지 산책용 방수 가방 백팩"
                }
                """);

        QueryIntent intent = analyzer.analyze("비 올 때 강아지 산책하면서 쓸 가방");

        assertThat(intent.productType()).isEqualTo("가방");
        assertThat(intent.purpose()).isEqualTo("산책");
        assertThat(intent.targetUser()).isEqualTo("강아지");
        assertThat(intent.material()).isEqualTo("방수");
        assertThat(intent.hardConstraints()).containsExactly("방수");
        assertThat(intent.enrichedQuery()).isEqualTo("강아지 산책용 방수 가방 백팩");
    }

    // ── 2. 계절 충돌 방지 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("여름 조건 쿼리의 enrichedQuery는 '여름'을 명시하여 롱코트 같은 충돌 상품과 코사인 거리가 멀어진다")
    void analyze_summerQuery_enrichedQueryContainsSeasonConstraint() {
        givenLlmReturns("""
                {
                  "productType": "옷",
                  "season": "여름",
                  "color": null,
                  "purpose": null,
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": ["여름용"],
                  "softPreferences": ["시원한"],
                  "enrichedQuery": "여름 쿨링 반팔 시원한 의류"
                }
                """);

        QueryIntent intent = analyzer.analyze("여름에 시원하게 입을 옷");

        // enrichedQuery에 "여름"이 포함되어야
        // 상품 임베딩("겨울 울 롱코트")과 코사인 거리가 멀어진다.
        assertThat(intent.season()).isEqualTo("여름");
        assertThat(intent.hardConstraints()).contains("여름용");
        assertThat(intent.enrichedQuery()).contains("여름");
        assertThat(intent.enrichedQuery()).doesNotContain("겨울").doesNotContain("롱코트");
    }

    @Test
    @DisplayName("겨울 조건 쿼리의 enrichedQuery는 '겨울'을 명시하여 반팔 같은 충돌 상품과 코사인 거리가 멀어진다")
    void analyze_winterQuery_enrichedQueryContainsWinterConstraint() {
        givenLlmReturns("""
                {
                  "productType": "옷",
                  "season": "겨울",
                  "color": null,
                  "purpose": null,
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": ["겨울용"],
                  "softPreferences": ["따뜻한"],
                  "enrichedQuery": "겨울 방한 따뜻한 코트 니트 의류"
                }
                """);

        QueryIntent intent = analyzer.analyze("겨울에 따뜻하게 입을 옷");

        assertThat(intent.season()).isEqualTo("겨울");
        assertThat(intent.hardConstraints()).contains("겨울용");
        assertThat(intent.enrichedQuery()).contains("겨울");
    }

    // ── 3. 정보 없는 필드는 null ──────────────────────────────────────────────

    @Test
    @DisplayName("검색어에 명시되지 않은 속성은 null로 두고 임의로 채우지 않는다")
    void analyze_missingAttributes_returnedAsNull() {
        givenLlmReturns("""
                {
                  "productType": "커피머신",
                  "season": null,
                  "color": null,
                  "purpose": "홈카페",
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": [],
                  "softPreferences": [],
                  "enrichedQuery": "홈카페 커피머신 에스프레소 원두"
                }
                """);

        QueryIntent intent = analyzer.analyze("카페 안 가고 집에서 커피 마시기");

        assertThat(intent.season()).isNull();
        assertThat(intent.color()).isNull();
        assertThat(intent.targetUser()).isNull();
        assertThat(intent.material()).isNull();
        assertThat(intent.productType()).isEqualTo("커피머신");
        assertThat(intent.purpose()).isEqualTo("홈카페");
    }

    // ── 4. Skip 조건 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("공백 없는 단일 키워드는 길이에 상관없이 LLM을 호출하지 않는다 (강아지, 공기청정기 등)")
    void analyze_singleKeywordNoSpace_skipsLlm() {
        QueryIntent intent1 = analyzer.analyze("강아지");   // 3자
        QueryIntent intent2 = analyzer.analyze("공기청정기"); // 6자 — 5자 초과이지만 공백 없음

        verifyNoInteractions(chatModel);
        assertThat(intent1.enrichedQuery()).isEqualTo("강아지");
        assertThat(intent2.enrichedQuery()).isEqualTo("공기청정기");
        assertThat(intent1.hasStructuredIntent()).isFalse();
        assertThat(intent2.hasStructuredIntent()).isFalse();
    }

    @Test
    @DisplayName("짧아도 2어절 이상이면 복합 의도 쿼리로 보고 LLM을 호출한다 (가죽 백, 차 키 등)")
    void analyze_shortButMultiWordQuery_callsLlm() {
        givenLlmReturns("""
                {
                  "target": "가방",
                  "requirements": [],
                  "productType": "가방",
                  "season": null,
                  "color": null,
                  "purpose": null,
                  "targetUser": null,
                  "material": "가죽",
                  "hardConstraints": [],
                  "softPreferences": [],
                  "enrichedQuery": "가죽 가방 백"
                }
                """);

        QueryIntent intent = analyzer.analyze("가죽 백");

        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(intent.target()).isEqualTo("가방");
    }

    @Test
    @DisplayName("8자 이상이고 2자 이상 단어가 2개 이상인 복합 쿼리는 LLM을 호출한다")
    void analyze_longComplexQuery_callsLlm() {
        givenLlmReturns("""
                {
                  "productType": "옷",
                  "season": "여름",
                  "color": null,
                  "purpose": null,
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": ["여름용"],
                  "softPreferences": [],
                  "enrichedQuery": "여름 의류 반팔 시원한"
                }
                """);

        // "여름에 시원한 옷" = 9자, 의미 단어 2개(여름에, 시원한) → LLM 호출
        QueryIntent intent = analyzer.analyze("여름에 시원한 옷");

        verify(chatModel, times(1)).call(any(Prompt.class));
        assertThat(intent.season()).isEqualTo("여름");
    }

    // ── 5. 인메모리 캐시 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("동일 쿼리를 N번 호출해도 LLM은 최초 1회만 호출된다 — 캐시 히트")
    void analyze_sameQueryMultipleTimes_llmCalledOnce() {
        givenLlmReturns("""
                {
                  "productType": "빔프로젝터",
                  "season": null,
                  "color": null,
                  "purpose": "홈시네마",
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": [],
                  "softPreferences": [],
                  "enrichedQuery": "홈시네마 빔프로젝터 영상 기기"
                }
                """);

        String query = "집에서 영화볼 때 필요한 기계";
        QueryIntent first  = analyzer.analyze(query);
        QueryIntent second = analyzer.analyze(query);
        QueryIntent third  = analyzer.analyze(query);

        // LLM은 정확히 1번만 호출됨
        verify(chatModel, times(1)).call(any(Prompt.class));
        // 캐시된 결과가 반환됨
        assertThat(first.enrichedQuery()).isEqualTo("홈시네마 빔프로젝터 영상 기기");
        assertThat(second.enrichedQuery()).isEqualTo(first.enrichedQuery());
        assertThat(third.enrichedQuery()).isEqualTo(first.enrichedQuery());
    }

    @Test
    @DisplayName("서로 다른 쿼리는 각각 LLM을 호출한다")
    void analyze_differentQueries_llmCalledEachTime() {
        givenLlmReturns("""
                {"productType": null, "season": "여름", "color": null,
                 "purpose": null, "targetUser": null, "material": null,
                 "hardConstraints": ["여름용"], "softPreferences": [],
                 "enrichedQuery": "여름 의류"}
                """);

        analyzer.analyze("여름에 시원한 옷");
        analyzer.analyze("겨울에 따뜻한 코트 고르기");

        verify(chatModel, times(2)).call(any(Prompt.class));
    }

    // ── 6. ChatModel 없는 환경 ────────────────────────────────────────────────

    @Test
    @DisplayName("ChatModel이 null이면 LLM을 호출하지 않고 passThrough를 반환한다 — 기존 검색 동작 유지")
    void analyze_noChatModel_returnsPassThrough() {
        QueryIntentAnalyzer analyzerWithoutLlm =
                new QueryIntentAnalyzer(null, cbFactory, objectMapper);

        QueryIntent intent = analyzerWithoutLlm.analyze("여름에 시원하게 입을 옷");

        verifyNoInteractions(chatModel);
        assertThat(intent.enrichedQuery()).isEqualTo("여름에 시원하게 입을 옷");
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    // ── 7. LLM 실패 Fallback ─────────────────────────────────────────────────

    @Test
    @DisplayName("LLM이 예외를 던지면 passThrough를 반환하여 기존 검색 동작이 유지된다")
    void analyze_llmThrowsException_returnsPassThrough() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("OpenAI timeout"));

        QueryIntent intent = analyzer.analyze("비 올 때 강아지 산책용 가방");

        assertThat(intent.enrichedQuery()).isEqualTo("비 올 때 강아지 산책용 가방");
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    @Test
    @DisplayName("LLM이 빈 응답을 반환하면 passThrough를 반환한다")
    void analyze_llmReturnsBlankResponse_returnsPassThrough() {
        givenLlmReturns("   ");

        QueryIntent intent = analyzer.analyze("캠핑 갈 때 쓰는 조명 추천");

        assertThat(intent.enrichedQuery()).isEqualTo("캠핑 갈 때 쓰는 조명 추천");
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    @Test
    @DisplayName("LLM 응답이 유효하지 않은 JSON이면 passThrough를 반환한다")
    void analyze_llmReturnsInvalidJson_returnsPassThrough() {
        givenLlmReturns("이것은 JSON이 아닙니다");

        QueryIntent intent = analyzer.analyze("집에서 영화볼 때 필요한 기계");

        assertThat(intent.enrichedQuery()).isEqualTo("집에서 영화볼 때 필요한 기계");
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    // ── 8. 마크다운 코드 펜스 제거 ───────────────────────────────────────────

    @Test
    @DisplayName("LLM 응답에 마크다운 코드 펜스가 붙어 오더라도 정상 파싱된다")
    void analyze_llmReturnsMarkdownWrappedJson_parsedCorrectly() {
        givenLlmReturns("""
                ```json
                {
                  "productType": "빔프로젝터",
                  "season": null,
                  "color": null,
                  "purpose": "홈시네마",
                  "targetUser": null,
                  "material": null,
                  "hardConstraints": [],
                  "softPreferences": [],
                  "enrichedQuery": "홈시네마 빔프로젝터 영상 기기"
                }
                ```
                """);

        QueryIntent intent = analyzer.analyze("집에서 영화볼 때 필요한 기계");

        assertThat(intent.productType()).isEqualTo("빔프로젝터");
        assertThat(intent.purpose()).isEqualTo("홈시네마");
        assertThat(intent.enrichedQuery()).isEqualTo("홈시네마 빔프로젝터 영상 기기");
    }

    // ── 9. null / 빈 쿼리 ────────────────────────────────────────────────────

    @Test
    @DisplayName("null 쿼리는 빈 enrichedQuery의 passThrough를 반환한다")
    void analyze_nullQuery_returnsPassThrough() {
        QueryIntent intent = analyzer.analyze(null);

        verifyNoInteractions(chatModel);
        assertThat(intent.enrichedQuery()).isEqualTo("");
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    @Test
    @DisplayName("blank 쿼리는 원본 그대로의 passThrough를 반환한다")
    void analyze_blankQuery_returnsPassThrough() {
        QueryIntent intent = analyzer.analyze("   ");

        verifyNoInteractions(chatModel);
        assertThat(intent.hasStructuredIntent()).isFalse();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void givenLlmReturns(String jsonResponse) {
        AssistantMessage output = new AssistantMessage(jsonResponse);
        var generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(output);
        var response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(generation);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
