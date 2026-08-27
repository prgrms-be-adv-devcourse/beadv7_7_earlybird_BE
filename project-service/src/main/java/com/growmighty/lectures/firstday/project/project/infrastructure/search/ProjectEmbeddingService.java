package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 임베딩 모델(Spring AI EmbeddingModel) 추상화 서비스.
 * 프로젝트 색인 시 5개 핵심 필드(title, summary, description, category, reward)를
 * 프로젝트당 1회의 배치 임베딩 요청(List&lt;String&gt;)으로 묶어 5개 독립 벡터를 동시 생성한다.
 */
@Slf4j
@Service
public class ProjectEmbeddingService {

    private static final int MAX_DESCRIPTION_LENGTH = 4000;
    private static final int MAX_QUERY_LENGTH = 1000;

    private final EmbeddingModel embeddingModel;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public ProjectEmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel,
                                    CircuitBreakerFactory circuitBreakerFactory) {
        this.embeddingModel = embeddingModel;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * 프로젝트의 5개 핵심 필드(title, summary, description, category, rewards)에 대해
     * 프로젝트당 1회의 배치 임베딩 요청으로 5개의 독립 벡터를 일괄 생성한다.
     */
    public ProjectFieldVectors generateFieldVectors(Project project, String categoryHierarchy, List<String> rewardNames) {
        if (project == null || embeddingModel == null) {
            return ProjectFieldVectors.empty();
        }

        String titleText = safeText(project.getTitle(), "");
        if (titleText.isBlank()) {
            return ProjectFieldVectors.empty();
        }
        String summaryText = safeText(project.getSummary(), "");
        String descText = safeDescriptionText(project.getDescription(), summaryText);
        String categoryText = safeText(categoryHierarchy, "기타");
        String rewardText = safeText(rewardNames != null && !rewardNames.isEmpty() ? String.join(", ", rewardNames) : "", "");

        // Index-time Search Context: 카테고리, 상품명, 특징, 리워드를 자동 결합하여 임베딩 의미 밀도 극대화
        String enrichedTitle = (categoryText + " " + titleText + " " + summaryText).trim();
        String enrichedSummary = (categoryText + " " + summaryText + " " + descText).trim();
        String enrichedDesc = (categoryText + " " + descText).trim();
        String enrichedCategory = categoryText;
        String enrichedReward = (categoryText + " " + titleText + " " + rewardText).trim();

        List<String> textsToEmbed = List.of(enrichedTitle, enrichedSummary, enrichedDesc, enrichedCategory, enrichedReward);

        List<float[]> vectors = circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_EMBEDDING_ID).run(
                () -> embedBatch(textsToEmbed),
                cause -> {
                    log.warn("프로젝트 필드별 AI 임베딩 일괄 생성 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });

        if (vectors == null || vectors.size() < 5) {
            return ProjectFieldVectors.empty();
        }

        return new ProjectFieldVectors(vectors.get(0), vectors.get(1), vectors.get(2), vectors.get(3), vectors.get(4));
    }

    public record ProjectEmbeddingTarget(Project project, String categoryHierarchy, List<String> rewardNames) {}

    /**
     * N개 프로젝트의 모든 필드 텍스트(N * 5개)를 단 1회의 OpenAI Batch API 요청으로 일괄 전송하여
     * 프로젝트별 5개 벡터 셋을 생성한다. (네트워크 라운드트립 N회 -> 1회 단축)
     */
    public Map<Long, ProjectFieldVectors> generateFieldVectorsBulk(List<ProjectEmbeddingTarget> targets) {
        if (targets == null || targets.isEmpty() || embeddingModel == null) {
            return Map.of();
        }

        List<String> allTextsToEmbed = new ArrayList<>();
        List<Long> projectOrder = new ArrayList<>();

        for (ProjectEmbeddingTarget target : targets) {
            Project project = target.project();
            if (project == null) continue;

            String titleText = safeText(project.getTitle(), "");
            if (titleText.isBlank()) continue;

            String summaryText = safeText(project.getSummary(), "");
            String descText = safeDescriptionText(project.getDescription(), summaryText);
            String categoryText = safeText(target.categoryHierarchy(), "기타");
            String rewardText = safeText(target.rewardNames() != null && !target.rewardNames().isEmpty() ? String.join(", ", target.rewardNames()) : "", "");

            String enrichedTitle = (categoryText + " " + titleText + " " + summaryText).trim();
            String enrichedSummary = (categoryText + " " + summaryText + " " + descText).trim();
            String enrichedDesc = (categoryText + " " + descText).trim();
            String enrichedCategory = categoryText;
            String enrichedReward = (categoryText + " " + titleText + " " + rewardText).trim();

            allTextsToEmbed.addAll(List.of(enrichedTitle, enrichedSummary, enrichedDesc, enrichedCategory, enrichedReward));
            projectOrder.add(project.getProjectId());
        }

        if (allTextsToEmbed.isEmpty()) {
            return Map.of();
        }

        List<float[]> allVectors = circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_BULK_INDEX_ID).run(
                () -> embedBatch(allTextsToEmbed),
                cause -> {
                    log.warn("프로젝트 N건 AI 임베딩 단일 배치 일괄 생성 실패. 대상 수={}, 원인: {}", targets.size(), cause);
                    return null;
                });

        if (allVectors == null || allVectors.size() < projectOrder.size() * 5) {
            return Map.of();
        }

        Map<Long, ProjectFieldVectors> result = new HashMap<>();
        for (int i = 0; i < projectOrder.size(); i++) {
            Long projectId = projectOrder.get(i);
            int baseIdx = i * 5;
            result.put(projectId, new ProjectFieldVectors(
                    allVectors.get(baseIdx),
                    allVectors.get(baseIdx + 1),
                    allVectors.get(baseIdx + 2),
                    allVectors.get(baseIdx + 3),
                    allVectors.get(baseIdx + 4)
            ));
        }

        return result;
    }
    public float[] generateEmbedding(String text) {
        if (embeddingModel == null) {
            log.debug("EmbeddingModel 빈이 설정되지 않아 임베딩 생성을 건너뜁니다.");
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        String targetText = text.trim();
        if (targetText.length() > MAX_QUERY_LENGTH) {
            targetText = targetText.substring(0, MAX_QUERY_LENGTH);
        }
        String finalTargetText = targetText;
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_EMBEDDING_ID).run(
                () -> embeddingModel.embed(finalTargetText),
                cause -> {
                    log.warn("AI 쿼리 임베딩 생성 실패. text_length={}, 원인: {}", finalTargetText.length(), cause.getMessage());
                    return null;
                });
    }

    public boolean isAvailable() {
        return embeddingModel != null;
    }

    private List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        if (response == null || response.getResults() == null) {
            return List.of();
        }
        return response.getResults().stream()
                .map(Embedding::getOutput)
                .toList();
    }

    private String safeText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text.trim();
    }

    private String safeDescriptionText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            return trimmed.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return trimmed;
    }
}
