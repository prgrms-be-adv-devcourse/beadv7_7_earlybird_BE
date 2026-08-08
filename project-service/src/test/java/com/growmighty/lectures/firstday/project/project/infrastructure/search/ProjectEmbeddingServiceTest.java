package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectEmbeddingServiceTest {

    private EmbeddingModel embeddingModel;
    private ProjectEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        embeddingService = new ProjectEmbeddingService(embeddingModel);
    }

    private Project sampleProject() {
        Project project = Project.register(1L, null, "친환경 보틀", 1L, "요약 설명", "상세 설명",
                BigDecimal.valueOf(100000), LocalDateTime.now(), LocalDate.now().plusDays(10));
        ReflectionTestUtils.setField(project, "projectId", 100L);
        return project;
    }

    @Test
    @DisplayName("EmbeddingModel이 정상 작동하면 프로젝트 텍스트의 1536차원 벡터를 생성한다")
    void generateEmbeddingForProject_success() {
        float[] dummyVector = new float[1536];
        dummyVector[0] = 0.5f;
        when(embeddingModel.embed("친환경 보틀 요약 설명 상세 설명")).thenReturn(dummyVector);

        float[] result = embeddingService.generateEmbeddingForProject(sampleProject());

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1536);
        assertThat(result[0]).isEqualTo(0.5f);
    }

    @Test
    @DisplayName("EmbeddingModel이 없으면 null을 반환하며 예외를 던지지 않는다")
    void generateEmbedding_modelNull_returnsNull() {
        ProjectEmbeddingService serviceWithoutModel = new ProjectEmbeddingService(null);

        float[] result = serviceWithoutModel.generateEmbedding("키워드");

        assertThat(result).isNull();
        assertThat(serviceWithoutModel.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("EmbeddingModel에서 예외가 발생하면 null을 반환하여 인덱싱을 방해하지 않는다")
    void generateEmbedding_modelThrowsException_returnsNull() {
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("OpenAI API rate limit exceeded"));

        float[] result = embeddingService.generateEmbedding("키워드");

        assertThat(result).isNull();
    }
}
