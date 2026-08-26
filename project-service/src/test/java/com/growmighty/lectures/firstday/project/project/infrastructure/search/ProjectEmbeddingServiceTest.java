package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.UUID;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private ProjectEmbeddingService embeddingService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
        CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
        when(circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_EMBEDDING_ID)).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<Object> toRun = invocation.getArgument(0);
            Function<Throwable, Object> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
        embeddingService = new ProjectEmbeddingService(embeddingModel, circuitBreakerFactory);
    }

    private Project sampleProject() {
        Project project = Project.register(1L, UUID.randomUUID(), null, "친환경 보틀", 1L, "요약 설명", "상세 설명",
                BigDecimal.valueOf(100000), LocalDateTime.now(), LocalDate.now().plusDays(10));
        ReflectionTestUtils.setField(project, "projectId", 100L);
        return project;
    }

    @Test
    @DisplayName("EmbeddingModel이 정상 작동하면 5개 필드의 1536차원 벡터를 1회의 배치 호출로 일괄 생성한다")
    void generateFieldVectors_success() {
        float[] v1 = new float[1536]; v1[0] = 0.1f;
        float[] v2 = new float[1536]; v2[0] = 0.2f;
        float[] v3 = new float[1536]; v3[0] = 0.3f;
        float[] v4 = new float[1536]; v4[0] = 0.4f;
        float[] v5 = new float[1536]; v5[0] = 0.5f;

        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(
                new Embedding(v1, 0),
                new Embedding(v2, 1),
                new Embedding(v3, 2),
                new Embedding(v4, 3),
                new Embedding(v5, 4)
        ));

        when(embeddingModel.embedForResponse(anyList())).thenReturn(mockResponse);

        ProjectFieldVectors result = embeddingService.generateFieldVectors(sampleProject(), "생활용품 > 텀블러", List.of("기본 세트", "풀패키지"));

        assertThat(result).isNotNull();
        assertThat(result.hasAnyVector()).isTrue();
        assertThat(result.titleVector()[0]).isEqualTo(0.1f);
        assertThat(result.summaryVector()[0]).isEqualTo(0.2f);
        assertThat(result.descriptionVector()[0]).isEqualTo(0.3f);
        assertThat(result.categoryVector()[0]).isEqualTo(0.4f);
        assertThat(result.rewardVector()[0]).isEqualTo(0.5f);
    }

    @Test
    @DisplayName("EmbeddingModel이 없으면 null 대신 empty ProjectFieldVectors를 반환한다")
    void generateFieldVectors_modelNull_returnsEmpty() {
        ProjectEmbeddingService serviceWithoutModel = new ProjectEmbeddingService(null, mock(CircuitBreakerFactory.class));

        ProjectFieldVectors result = serviceWithoutModel.generateFieldVectors(sampleProject(), "카테고리", List.of());

        assertThat(result).isNotNull();
        assertThat(result.hasAnyVector()).isFalse();
        assertThat(serviceWithoutModel.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("EmbeddingModel에서 예외가 발생하면 예외를 던지지 않고 empty ProjectFieldVectors를 반환한다")
    void generateFieldVectors_modelThrowsException_returnsEmpty() {
        when(embeddingModel.embedForResponse(anyList())).thenThrow(new RuntimeException("OpenAI API rate limit exceeded"));

        ProjectFieldVectors result = embeddingService.generateFieldVectors(sampleProject(), "카테고리", List.of());

        assertThat(result).isNotNull();
        assertThat(result.hasAnyVector()).isFalse();
    }

    @Test
    @DisplayName("단일 쿼리 임베딩 생성이 정상 작동한다")
    void generateEmbedding_success() {
        float[] dummy = new float[1536];
        dummy[0] = 0.9f;
        when(embeddingModel.embed("검색어")).thenReturn(dummy);

        float[] result = embeddingService.generateEmbedding("검색어");

        assertThat(result).isNotNull();
        assertThat(result[0]).isEqualTo(0.9f);
    }
}
