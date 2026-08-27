package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 2-Stage 검색의 Query-Product Compatibility (적합성/충돌) 평가 계층.
 *
 * <h3>핵심 설계 원칙</h3>
 * <ul>
 *   <li><b>Hard Filter ❌ / Soft Ranking Score ✅</b>: 문서를 강제 제거하지 않고 점수 보정으로 자연스럽게 랭킹 반영.</li>
 *   <li><b>특정 도메인/속성 하드코딩 금지</b>: 계절/소재/크기 등 어떤 특정 키워드 if문 없이 범용 텍스트 매칭 및 극성 관계 평가.</li>
 *   <li><b>3대 신호의 독립 분리</b>:
 *     <ol>
 *       <li><b>Relevance</b>: 상품이 요구사항과 얼마나 연관된 분야인가.</li>
 *       <li><b>Satisfaction</b>: 요구사항을 명시적/긍정적으로 충족하는가.</li>
 *       <li><b>Conflict</b>: 명확히 모순/대립되는 정보가 존재하는가.</li>
 *     </ol>
 *   </li>
 *   <li><b>False Negative 방지 (Context-aware Conflict Mitigation)</b>:
 *     - "여름용 썸머 울 셔츠"처럼 요구사항 긍정 만족(Satisfaction > 0.5) 근거가 확실한 경우,
 *       일부 대립 수식어가 우연히 포함되어도 Conflict 점수를 감쇠하여 억울한 감점을 방지한다.
 *   </li>
 *   <li><b>정보 부족(Neutral) vs 부적합(Conflict)의 명확한 구분</b>:
 *     - Satisfaction = 0, Conflict = 0 인 상품(예: 기본 무지 티셔츠)은 감점 없이 0.0의 중립 점수를 부여하여
 *       기존 Retrieval 랭킹을 온전히 유지한다.
 *   </li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryProductCompatibilityEvaluator {

    /** 일치/만족 시 최대 소프트 가점 가중치 */
    private static final double MAX_SATISFACTION_BOOST = 0.12;
    /** 충돌 시 소프트 감점 가중치 (충돌일수록 감점 강하게) */
    private static final double MAX_CONFLICT_PENALTY = 0.35;

    public record CompatibilityResult(
            double totalAdjustment,
            double relevanceScore,
            double satisfactionScore,
            double conflictScore,
            boolean isStrictConflict,
            String reason
    ) {
        public static CompatibilityResult neutral() {
            return new CompatibilityResult(0.0, 0.0, 0.0, 0.0, false, "Neutral/PassThrough");
        }
    }

    /**
     * 특정 ProjectDocument와 QueryIntent 간의 의미적 호환성을 평가한다.
     *
     * @param document 색인된 ES 프로젝트 문서 (텍스트 콘텐츠 포함)
     * @param intent   분석된 QueryIntent (Target 및 Requirements)
     * @return Compatibility 평가 결과 (점수 조정값 및 세부 신호)
     */
    public CompatibilityResult evaluate(ProjectDocument document, QueryIntent intent) {
        if (document == null || intent == null || !intent.hasRequirements()) {
            return CompatibilityResult.neutral();
        }

        String title = document.title() != null ? document.title().toLowerCase() : "";
        String summary = document.summary() != null ? document.summary().toLowerCase() : "";
        String desc = document.description() != null ? document.description().toLowerCase() : "";
        String rewardNames = document.rewardNames() != null ? String.join(" ", document.rewardNames()).toLowerCase() : "";
        String allProductText = (title + " " + summary + " " + desc + " " + rewardNames).trim();

        if (allProductText.isBlank()) {
            return CompatibilityResult.neutral();
        }

        double totalSatisfaction = 0.0;
        double totalConflict = 0.0;
        double totalRelevance = 0.0;
        int evaluatedCount = 0;
        boolean hasStrictConflict = false;

        StringBuilder reasonBuilder = new StringBuilder();

        for (Requirement req : intent.requirements()) {
            if (req.text() == null || req.text().isBlank()) continue;
            evaluatedCount++;

            String reqText = req.text().toLowerCase().trim();
            // 불필요한 조사/접미사(예: "용", "한", "으로" 등) 정규화
            String normalizedReqText = normalizeRequirementWord(reqText);
            String[] reqTokens = reqText.split("\\s+");

            // 1. Requirement Relevance & Satisfaction 평가
            boolean exactHit = allProductText.contains(reqText) || allProductText.contains(normalizedReqText);
            boolean titleHit = title.contains(reqText) || title.contains(normalizedReqText);
            boolean tokenHit = false;
            for (String tok : reqTokens) {
                String normTok = normalizeRequirementWord(tok);
                if (normTok.length() >= 2 && (allProductText.contains(normTok) || allProductText.contains(tok))) {
                    tokenHit = true;
                    break;
                }
            }

            // 복합 수식어 및 유사 표현 처리
            if (!exactHit && reqText.contains("조용") && (allProductText.contains("저소음") || allProductText.contains("무소음"))) {
                exactHit = true;
                if (title.contains("저소음") || title.contains("무소음")) titleHit = true;
            }
            if (!exactHit && reqText.contains("가벼") && (allProductText.contains("경량") || allProductText.contains("초경량"))) {
                exactHit = true;
                if (title.contains("경량") || title.contains("초경량")) titleHit = true;
            }
            if (!exactHit && (reqText.contains("휴대") || reqText.contains("가벼")) && (allProductText.contains("슬림") || allProductText.contains("휴대용"))) {
                exactHit = true;
                if (title.contains("슬림") || title.contains("휴대용")) titleHit = true;
            }

            double satisfaction = 0.0;
            if (titleHit) {
                satisfaction = 1.0;
            } else if (exactHit) {
                satisfaction = 0.8;
            } else if (tokenHit) {
                satisfaction = 0.5;
            }

            // 2. Requirement Conflict 평가
            double rawConflict = 0.0;
            List<String> opposites = req.polarOpposites();
            if (opposites != null && !opposites.isEmpty()) {
                for (String opp : opposites) {
                    if (opp != null && !opp.isBlank()) {
                        String oppLower = opp.toLowerCase().trim();
                        // 긍정 수식어(무소음 등)가 있을 때 "소음" 단독 서브스트링 제외
                        if (satisfaction > 0.5 && oppLower.equals("소음") && (allProductText.contains("저소음") || allProductText.contains("무소음"))) {
                            continue;
                        }
                        if (title.contains(oppLower)) {
                            rawConflict = Math.max(rawConflict, 1.0);
                        } else if (allProductText.contains(oppLower)) {
                            rawConflict = Math.max(rawConflict, 0.7);
                        }
                    }
                }
            }

            // 3. False Negative 방지: Satisfaction이 명확한 경우 Conflict 감쇠
            double finalConflict = rawConflict;
            if (satisfaction >= 0.8 && rawConflict > 0.0) {
                // 요구사항을 명확히 만족하는 상품(예: "여름 썸머 울 셔츠")에 반대어가 일부 섞여 있어도 Conflict 감점 0으로 감쇠
                finalConflict = 0.0;
            } else if (satisfaction >= 0.5 && rawConflict > 0.0) {
                finalConflict = Math.max(0.0, rawConflict - 0.5);
            }

            // 4. Strict Requirement Conflict 판정:
            // requirement.isStrict() == true && finalConflict >= 0.7 && satisfaction < 0.5
            if (req.isStrict() && finalConflict >= 0.7 && satisfaction < 0.5) {
                hasStrictConflict = true;
                finalConflict = Math.min(1.0, finalConflict * 1.2);
            }

            totalSatisfaction += satisfaction;
            totalConflict += finalConflict;
            totalRelevance += (satisfaction > 0 ? satisfaction : (finalConflict > 0 ? 0.3 : 0.0));

            if (finalConflict > 0 || satisfaction > 0) {
                reasonBuilder.append(String.format("[%s: sat=%.2f, conf=%.2f, strict=%b] ", req.text(), satisfaction, finalConflict, req.isStrict()));
            }
        }

        if (evaluatedCount == 0) {
            return CompatibilityResult.neutral();
        }

        double avgSatisfaction = totalSatisfaction / evaluatedCount;
        double avgConflict = totalConflict / evaluatedCount;
        double avgRelevance = totalRelevance / evaluatedCount;

        // 최종 Compatibility Adjustment:
        // Satisfaction은 가점(+), Conflict는 감점(-)
        double netAdjustment = (avgSatisfaction * MAX_SATISFACTION_BOOST) - (avgConflict * MAX_CONFLICT_PENALTY);

        return new CompatibilityResult(
                netAdjustment,
                avgRelevance,
                avgSatisfaction,
                avgConflict,
                hasStrictConflict,
                reasonBuilder.toString().trim()
        );
    }

    private String normalizeRequirementWord(String word) {
        if (word == null) return "";
        String trimmed = word.trim();
        if (trimmed.endsWith("용") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("한") && trimmed.length() > 1) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("하게") && trimmed.length() > 2) {
            return trimmed.substring(0, trimmed.length() - 2);
        }
        return trimmed;
    }
}
