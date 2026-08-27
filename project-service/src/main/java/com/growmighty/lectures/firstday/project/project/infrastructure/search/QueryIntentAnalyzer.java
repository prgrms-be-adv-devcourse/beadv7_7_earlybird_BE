package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM(ChatModel)을 사용해 사용자 검색어를 구조화된 {@link QueryIntent}로 분석한다.
 *
 * <h3>역할</h3>
 * <ul>
 *   <li>원본 검색어에서 상품 유형, 계절, 색상, 용도, 대상, 소재 등 검색에 영향을 주는 속성을 추출한다.</li>
 *   <li>자연어를 상품 설명에 가까운 {@code enrichedQuery}로 재구성하여 Vector Search의 의미 이해력을 높인다.</li>
 *   <li><b>BM25는 원본 쿼리를 그대로 사용</b>하므로 이 클래스는 Vector Search 임베딩 경로에만 영향을 준다.</li>
 *   <li><b>검색 결과를 LLM에게 보내 relevance를 평가하지 않는다.</b> LLM은 검색어 전처리 단계에서만 사용한다.</li>
 * </ul>
 *
 * <h3>Latency 최적화</h3>
 * <ul>
 *   <li><b>인메모리 캐시</b>: 동일 쿼리는 LLM 재호출 없이 즉시 반환한다. 실시간 입력 환경에서 반복 쿼리 비용 0.</li>
 *   <li><b>Skip 조건</b>: 공백 없는 단일 키워드 / 8자 미만 쿼리 / 의미 있는 단어 2개 미만인 쿼리는
 *       LLM 분석 없이 패스스루를 반환한다. 타이핑 중간 상태 쿼리에 LLM이 호출되지 않는다.</li>
 * </ul>
 *
 * <h3>Fallback</h3>
 * LLM 호출 실패(타임아웃, 파싱 오류, Circuit Breaker Open 등) 시
 * {@link QueryIntent#passThrough(String)}를 반환하여 기존 검색 동작이 그대로 유지된다.
 */
@Slf4j
@Component
public class QueryIntentAnalyzer {

    /**
     * LLM 분석 대상 최소 쿼리 길이(글자 수).
     * 8자 미만이면 타이핑 중간 상태로 간주하고 LLM 호출을 건너뛴다.
     */
    private static final int MIN_LLM_QUERY_LENGTH = 8;

    /**
     * LLM 분석을 활성화하기 위한 최소 의미 있는 단어 수(2자 이상 단어 기준).
     * 복합 자연어 쿼리에서만 LLM이 의미 있는 구조화를 할 수 있다.
     */
    private static final int MIN_MEANINGFUL_WORDS = 2;

    /**
     * QueryIntent 캐시 최대 항목 수.
     * 사이즈 초과 시 신규 항목을 캐싱하지 않는다 — 단순 바운드 정책.
     */
    private static final int CACHE_MAX_SIZE = 500;

    private static final String SYSTEM_PROMPT = """
            당신은 온라인 크라우드펀딩 상품 검색 시스템의 쿼리 분석기입니다.
            사용자의 검색어를 분석하여 JSON 형식으로만 응답합니다.
            
            [분석 규칙]
            1. 검색어에 명확히 명시되거나 의미상 강하게 추론 가능한 정보만 채웁니다.
            2. 불확실하거나 추측에 해당하는 정보는 null로 둡니다. 없는 정보를 만들어 내지 마세요.
            3. hardConstraints: 검색어에 명확히 표현된 필수 조건만 포함합니다.
               예) "여름에 입는 옷" → hardConstraints: ["여름용"]
               예) "검은색 백팩" → hardConstraints: ["검정색", "백팩"]
               예) "편한 신발" → hardConstraints: [] (편함은 정량적 필수 조건이 아니라 선호)
            4. softPreferences: 선호/의도에 가까운 표현만 포함합니다.
               예) "편한", "가벼운", "감성적인", "저렴한"
            5. enrichedQuery: 원본 자연어를 상품 카탈로그 설명에 가까운 구체적 표현으로 재구성합니다.
               - 상품명, 카테고리, 핵심 속성을 간결하게 나열하세요 (10단어 이내).
               - 조사, 부사, 감탄사는 제거하세요.
               - hardConstraints에 포함된 핵심 속성을 반드시 포함시키세요.
               - 계절, 소재, 용도, 대상이 있으면 함께 포함하세요.
               - 계절 조건(여름/겨울 등)이 있으면 enrichedQuery에 반드시 명시하세요.
                 예) "여름에 입기 좋은 시원한 여성용 셔츠" → "여름 쿨링 여성 반팔 셔츠 의류"
                 (겨울 롱코트처럼 계절이 충돌하는 상품과 코사인 거리가 멀어지도록)
               예) "비 올 때 강아지 산책하면서 쓸 가방" → "강아지 산책용 방수 가방 백팩"
               예) "집에서 영화볼 때 필요한 기계" → "홈시네마 빔프로젝터 영상 기기"
               예) "카페 안 가고 집에서 커피 마시기" → "홈카페 커피머신 에스프레소 원두"
            
            [응답 형식] - JSON만 출력하며 다른 텍스트는 절대 포함하지 않습니다:
            {
              "productType": "string or null",
              "season": "string or null (여름/겨울/봄/가을 중 하나 또는 null)",
              "color": "string or null",
              "purpose": "string or null",
              "targetUser": "string or null",
              "material": "string or null",
              "hardConstraints": ["string", ...],
              "softPreferences": ["string", ...],
              "enrichedQuery": "string"
            }
            """;

    private final ChatModel chatModel;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ObjectMapper objectMapper;

    /**
     * QueryIntent 인메모리 캐시.
     * 실시간 입력 환경에서 동일 쿼리가 반복 입력될 때 LLM 재호출 없이 즉시 반환한다.
     * 캐시 키: 원본 쿼리 문자열(trimmed).
     */
    private final ConcurrentHashMap<String, QueryIntent> intentCache = new ConcurrentHashMap<>();

    public QueryIntentAnalyzer(
            @Autowired(required = false) ChatModel chatModel,
            CircuitBreakerFactory circuitBreakerFactory,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 검색어를 분석하여 {@link QueryIntent}를 반환한다.
     *
     * <p>실시간 검색 환경을 고려한 처리 우선순위:
     * <ol>
     *   <li>캐시 히트 → 즉시 반환 (LLM 호출 없음)</li>
     *   <li>Skip 조건 해당 → passThrough 반환 (LLM 호출 없음)</li>
     *   <li>ChatModel 미설정 → passThrough 반환</li>
     *   <li>LLM 호출 → 성공 시 캐시 저장 후 반환, 실패 시 passThrough 반환</li>
     * </ol>
     *
     * <p>passThrough 반환 시 {@code enrichedQuery == 원본 쿼리}이므로
     * 기존 Vector Search 동작과 완전히 동일하게 작동한다.
     *
     * @param query 사용자 원본 검색어
     * @return 구조화된 QueryIntent (실패·스킵 시 passThrough)
     */
    public QueryIntent analyze(String query) {
        if (query == null || query.isBlank()) {
            return QueryIntent.passThrough(query != null ? query : "");
        }
        String trimmed = query.trim();

        // ① 캐시 히트: 동일 쿼리는 LLM 없이 즉시 반환
        QueryIntent cached = intentCache.get(trimmed);
        if (cached != null) {
            log.debug("[QueryIntent] 캐시 히트: '{}'", trimmed);
            return cached;
        }

        // ② Skip 조건: 단일 키워드·짧은 쿼리·미완성 쿼리는 passThrough
        if (shouldSkipLlm(trimmed)) {
            log.debug("[QueryIntent] LLM 스킵 (단일키워드·짧은쿼리): '{}'", trimmed);
            return QueryIntent.passThrough(trimmed);
        }

        // ③ ChatModel 미설정 환경 (테스트·ChatModel 비활성화)
        if (chatModel == null) {
            log.debug("[QueryIntent] ChatModel 미설정 → passThrough: '{}'", trimmed);
            return QueryIntent.passThrough(trimmed);
        }

        // ④ LLM 호출 — 성공 시 캐시 저장
        return circuitBreakerFactory
                .create(ProjectSearchCircuitBreakerConfig.PROJECT_QUERY_INTENT_ID)
                .run(
                        () -> {
                            QueryIntent result = callLlmAndParse(trimmed);
                            cacheIfRoom(trimmed, result);
                            return result;
                        },
                        cause -> {
                            log.warn("[QueryIntent] LLM 호출 실패, passThrough 폴백. query='{}', cause={}",
                                    trimmed, cause.getMessage());
                            return QueryIntent.passThrough(trimmed);
                        });
    }

    /**
     * LLM 분석을 건너뛸 조건.
     *
     * <ul>
     *   <li>공백이 없으면 단일 키워드 → 임베딩 모델 자체가 잘 처리하므로 LLM 불필요</li>
     *   <li>총 길이 8자 미만 → 타이핑 중간 상태로 간주 (실시간 키 입력 대응)</li>
     *   <li>2자 이상 단어가 2개 미만 → 의미 있는 복합 표현이 아직 완성되지 않은 상태</li>
     * </ul>
     *
     * <p>예시:
     * <ul>
     *   <li>"강아지" → 공백 없음 → 스킵 ✓</li>
     *   <li>"공기청정기" → 공백 없음 → 스킵 ✓</li>
     *   <li>"여름 옷" → 5자, 8자 미만 → 스킵 ✓ (타이핑 중)</li>
     *   <li>"여름에 시원한 옷" → 9자, 의미 단어 2개 → LLM 호출 ✓</li>
     *   <li>"비 올 때 강아지 산책용 가방" → 15자, 의미 단어 4개 → LLM 호출 ✓</li>
     * </ul>
     */
    private boolean shouldSkipLlm(String query) {
        // 단일 키워드(공백 없음): 임베딩 모델이 직접 처리 가능, LLM 불필요
        if (!query.contains(" ")) {
            return true;
        }
        // 타이핑 중간 상태: 아직 쿼리가 완성되지 않은 것으로 간주
        if (query.length() < MIN_LLM_QUERY_LENGTH) {
            return true;
        }
        // 의미 있는 단어(2자 이상)가 최소 2개여야 자연어 의도를 구조화할 수 있음
        long meaningfulWordCount = Arrays.stream(query.split("\\s+"))
                .filter(word -> word.length() >= 2)
                .count();
        return meaningfulWordCount < MIN_MEANINGFUL_WORDS;
    }

    private void cacheIfRoom(String key, QueryIntent intent) {
        if (intentCache.size() < CACHE_MAX_SIZE) {
            intentCache.put(key, intent);
        }
    }

    private QueryIntent callLlmAndParse(String query) {
        String userMessage = "검색어: \"" + query + "\"";
        String response = chatModel.call(new Prompt(
                List.of(
                        new org.springframework.ai.chat.messages.SystemMessage(SYSTEM_PROMPT),
                        new org.springframework.ai.chat.messages.UserMessage(userMessage)
                )
        )).getResult().getOutput().getText();

        return parseResponse(response, query);
    }

    /**
     * LLM JSON 응답을 파싱하여 {@link QueryIntent}로 변환한다.
     * 파싱 실패 시 passThrough를 반환한다.
     */
    private QueryIntent parseResponse(String response, String originalQuery) {
        if (response == null || response.isBlank()) {
            log.warn("[QueryIntent] LLM 응답 비어있음 → passThrough. query='{}'", originalQuery);
            return QueryIntent.passThrough(originalQuery);
        }

        String jsonText = extractJson(response);
        try {
            JsonNode node = objectMapper.readTree(jsonText);

            String productType = textOrNull(node, "productType");
            String season = textOrNull(node, "season");
            String color = textOrNull(node, "color");
            String purpose = textOrNull(node, "purpose");
            String targetUser = textOrNull(node, "targetUser");
            String material = textOrNull(node, "material");
            List<String> hardConstraints = stringList(node, "hardConstraints");
            List<String> softPreferences = stringList(node, "softPreferences");
            String enrichedQuery = textOrNull(node, "enrichedQuery");

            // enrichedQuery가 비거나 원본보다 지나치게 짧으면 원본을 그대로 사용
            if (enrichedQuery == null || enrichedQuery.isBlank()
                    || enrichedQuery.length() < originalQuery.length() / 3) {
                enrichedQuery = originalQuery;
            }

            QueryIntent intent = new QueryIntent(
                    productType, season, color, purpose, targetUser, material,
                    hardConstraints, softPreferences, enrichedQuery);

            log.info("[QueryIntent] 분석 완료: query='{}' | productType={}, season={}, purpose={}, "
                            + "targetUser={}, material={}, hardConstraints={}, enrichedQuery='{}'",
                    originalQuery, productType, season, purpose,
                    targetUser, material, hardConstraints, enrichedQuery);

            return intent;

        } catch (JsonProcessingException e) {
            log.warn("[QueryIntent] JSON 파싱 실패 → passThrough. query='{}', response='{}'",
                    originalQuery, response, e);
            return QueryIntent.passThrough(originalQuery);
        }
    }

    /**
     * LLM 응답에 마크다운 코드 펜스(```json ... ```)가 붙어 오는 경우를 처리한다.
     */
    private String extractJson(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        return trimmed;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            String text = item.asText();
            if (!text.isBlank()) {
                result.add(text.trim());
            }
        }
        return List.copyOf(result);
    }
}
