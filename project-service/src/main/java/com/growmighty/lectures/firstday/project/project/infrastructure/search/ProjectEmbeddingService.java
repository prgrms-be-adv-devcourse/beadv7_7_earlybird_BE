package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI 임베딩 모델(Spring AI EmbeddingModel) 추상화 서비스.
 * OpenAI 등 외부 임베딩 API 호출을 담당하며, API 키 누락이나 장애 발생 시 예외를 던지지 않고
 * null을 반환하여 인덱싱 및 키워드 검색의 연속성을 보장한다.
 */
@Slf4j
@Service
public class ProjectEmbeddingService {

    private static final int MAX_TEXT_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;

    public ProjectEmbeddingService(@Autowired(required = false) EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
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
        try {
            return embeddingModel.embed(targetText);
        } catch (Exception e) {
            log.warn("AI 임베딩 생성 실패. text_length={}, 원인: {}", targetText.length(), e.getMessage());
            return null;
        }
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
