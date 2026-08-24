package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 임베딩 모델(Spring AI EmbeddingModel) 추상화 서비스.
 * OpenAI 등 외부 임베딩 API 호출을 담당하며, API 키 누락이나 장애 발생 시 예외를 던지지 않고
 * null을 반환하여 인덱싱 및 키워드 검색의 연속성을 보장한다. 실제 호출은 전용 서킷브레이커
 * (projectEmbedding)로 감싼다 — 재색인 한 페이지당 최대 50번 호출되는데, 감싸지 않으면 OpenAI
 * 장애 시에도 매번 풀타임아웃을 기다린 뒤에야 null로 강등되기 때문이다(ProjectSearchCircuitBreakerConfig
 * .PROJECT_EMBEDDING_ID 설명 참고). 서킷이 열리면 CallNotPermittedException도 그대로 여기서
 * 삼켜 null을 반환하므로, 이 메서드가 예외를 던지지 않는다는 계약 자체는 바뀌지 않는다.
 */
@Slf4j
@Service
public class ProjectEmbeddingService {

    private static final int MAX_TEXT_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public ProjectEmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel,
                                    CircuitBreakerFactory circuitBreakerFactory) {
        this.embeddingModel = embeddingModel;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /**
     * 프로젝트 본문(title + summary + description) 조합 텍스트의 1536차원 임베딩 생성.
     */
    public float[] generateEmbeddingForProject(Project project) {
        if (project == null) {
            return null;
        }
        String textToEmbed = buildTextToEmbed(project);
        return generateEmbedding(textToEmbed);
    }

    /**
     * 검색 키워드 또는 텍스트의 1536차원 임베딩 생성.
     * 텍스트 길이가 너무 긴 경우 토큰 초과 방지를 위해 MAX_TEXT_LENGTH(2000자)로 절단한다.
     */
    public float[] generateEmbedding(String text) {
        if (embeddingModel == null) {
            log.debug("EmbeddingModel 빈이 설정되지 않아 임베딩 생성을 건너뜁니다.");
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        String targetText = text.trim();
        if (targetText.length() > MAX_TEXT_LENGTH) {
            targetText = targetText.substring(0, MAX_TEXT_LENGTH);
        }
        String finalTargetText = targetText;
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_EMBEDDING_ID).run(
                () -> embeddingModel.embed(finalTargetText),
                cause -> {
                    log.warn("AI 임베딩 생성 실패. text_length={}, 원인: {}", finalTargetText.length(), cause.getMessage());
                    return null;
                });
    }

    public boolean isAvailable() {
        return embeddingModel != null;
    }

    private String buildTextToEmbed(Project project) {
        StringBuilder sb = new StringBuilder();
        if (project.getTitle() != null && !project.getTitle().isBlank()) {
            sb.append(project.getTitle()).append(" ");
        }
        if (project.getSummary() != null && !project.getSummary().isBlank()) {
            sb.append(project.getSummary()).append(" ");
        }
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            sb.append(project.getDescription());
        }
        return sb.toString().trim();
    }
}
