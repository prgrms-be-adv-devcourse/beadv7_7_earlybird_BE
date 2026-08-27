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
 * LLM(ChatModel)을 사용해 사용자 검색어를 Target + Requirements 구조의 {@link QueryIntent}로 분석한다.
 */
@Slf4j
@Component
public class QueryIntentAnalyzer {

    private static final int MIN_MEANINGFUL_WORDS = 2;
    private static final int CACHE_MAX_SIZE = 500;

    /**
     * 슬림 프롬프트: 파이프라인이 실제로 쓰는 3가지(target·requirements·enrichedQuery)만 뽑는다.
     * 과거 스키마의 season/color/purpose/material/hardConstraints 등은 requirements로 일반화됐고,
     * 파서가 누락 필드를 null/빈리스트로 안전 처리하므로 프롬프트에서 제거해 입출력 토큰을 줄인다
     * (gpt-4o-mini + 이 프롬프트로 응답 지연 8~13초 → 1~2초).
     */
    private static final String SYSTEM_PROMPT = """
            당신은 크라우드펀딩 상품 검색어 분석기입니다. 사용자의 자연어 검색어에서 아래 3가지를 뽑아 JSON만 출력합니다.

            1. target: 찾는 핵심 품목 (예: "옷", "가방", "키보드", "카메라", "책상"). 불명확하면 null.
            2. requirements: 검색어가 요구하는 조건 목록. 각 항목:
               - text: 조건 (예: "여름용", "조용한", "초보자용", "원룸용", "휴대용")
               - isStrict: 그 조건을 어기면 상품이 명백히 부적합해지는 필수 조건이면 true, 단순 선호면 false
               - polarOpposites: 그 조건과 명백히 충돌하는 상품 설명 표현 목록
                 예) "여름용" → ["겨울", "방한", "기모", "롱코트", "패딩"]
                 예) "조용한" → ["시끄러운", "청축", "타건음이 큰"]
                 예) "초보자용" → ["전문가용", "상급자", "하이엔드"]
                 예) "가벼운"/"휴대용" → ["무거운", "헤비", "거치형", "대용량"]
            3. enrichedQuery: 검색어를 상품 카탈로그 표현으로 재구성 (10단어 이내).
               예) "여름에 입기 좋은 옷" → "여름용 시원한 통기성 반팔 의류"

            JSON 외 다른 텍스트를 절대 포함하지 않습니다:
            {"target": string|null, "requirements": [{"text": string, "isStrict": boolean, "polarOpposites": [string]}], "enrichedQuery": string}
            """;

    private final ChatModel chatModel;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, QueryIntent> intentCache = new ConcurrentHashMap<>();

    public QueryIntentAnalyzer(
            @Autowired(required = false) ChatModel chatModel,
            CircuitBreakerFactory circuitBreakerFactory,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.objectMapper = objectMapper;
    }

    public QueryIntent analyze(String query) {
        if (query == null || query.isBlank()) {
            return QueryIntent.passThrough(query != null ? query : "");
        }
        String trimmed = query.trim();

        QueryIntent cached = intentCache.get(trimmed);
        if (cached != null) {
            log.debug("[QueryIntent] 캐시 히트: '{}'", trimmed);
            return cached;
        }

        if (shouldSkipLlm(trimmed)) {
            log.debug("[QueryIntent] LLM 스킵 (단일키워드·짧은쿼리): '{}'", trimmed);
            return QueryIntent.passThrough(trimmed);
        }

        if (chatModel == null) {
            log.debug("[QueryIntent] ChatModel 미설정 → passThrough: '{}'", trimmed);
            return QueryIntent.passThrough(trimmed);
        }

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

    private boolean shouldSkipLlm(String query) {
        // 단일 키워드(공백 없음)는 LLM 불필요 -> passThrough ("여름", "노트북", "키보드" 등 회귀 보장)
        if (!query.contains(" ")) {
            return true;
        }
        // 공백으로 분리된 어절이 2개 이상이면 복합 의도 쿼리로 간주하여 LLM 호출 허용 ("여름용 옷", "가죽 백", "차 키" 등)
        long wordCount = Arrays.stream(query.split("\\s+"))
                .filter(word -> !word.isBlank())
                .count();
        return wordCount < MIN_MEANINGFUL_WORDS;
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

    private QueryIntent parseResponse(String response, String originalQuery) {
        if (response == null || response.isBlank()) {
            log.warn("[QueryIntent] LLM 응답 비어있음 → passThrough. query='{}'", originalQuery);
            return QueryIntent.passThrough(originalQuery);
        }

        String jsonText = extractJson(response);
        try {
            JsonNode node = objectMapper.readTree(jsonText);

            String target = textOrNull(node, "target");
            String productType = textOrNull(node, "productType");
            if (target == null && productType != null) {
                target = productType;
            }

            String season = textOrNull(node, "season");
            String color = textOrNull(node, "color");
            String purpose = textOrNull(node, "purpose");
            String targetUser = textOrNull(node, "targetUser");
            String material = textOrNull(node, "material");
            List<String> hardConstraints = stringList(node, "hardConstraints");
            List<String> softPreferences = stringList(node, "softPreferences");
            String enrichedQuery = textOrNull(node, "enrichedQuery");

            List<Requirement> requirements = parseRequirements(node);

            if (enrichedQuery == null || enrichedQuery.isBlank()
                    || enrichedQuery.length() < originalQuery.length() / 3) {
                enrichedQuery = originalQuery;
            }

            QueryIntent intent = new QueryIntent(
                    target,
                    requirements,
                    productType != null ? productType : target,
                    season, color, purpose, targetUser, material,
                    hardConstraints, softPreferences, enrichedQuery);

            log.info("[QueryIntent] 분석 완료: query='{}' | target='{}', requirementsCount={}, enrichedQuery='{}'",
                    originalQuery, target, requirements.size(), enrichedQuery);

            return intent;

        } catch (JsonProcessingException e) {
            log.warn("[QueryIntent] JSON 파싱 실패 → passThrough. query='{}', response='{}'",
                    originalQuery, response, e);
            return QueryIntent.passThrough(originalQuery);
        }
    }

    private List<Requirement> parseRequirements(JsonNode node) {
        JsonNode reqArray = node.get("requirements");
        if (reqArray == null || !reqArray.isArray()) {
            return List.of();
        }
        List<Requirement> list = new ArrayList<>();
        for (JsonNode item : reqArray) {
            String text = textOrNull(item, "text");
            if (text == null || text.isBlank()) continue;
            String type = textOrNull(item, "type");
            boolean isStrict = item.has("isStrict") && item.get("isStrict").asBoolean();
            List<String> polarOpposites = stringList(item, "polarOpposites");
            list.add(new Requirement(text, type != null ? type : "general", isStrict, polarOpposites));
        }
        return List.copyOf(list);
    }

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
