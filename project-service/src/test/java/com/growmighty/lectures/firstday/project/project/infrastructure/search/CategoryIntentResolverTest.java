package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryIntentResolverTest {

    private final ProjectCategoryRepository categoryRepository = mock(ProjectCategoryRepository.class);
    private final ProjectEmbeddingService embeddingService = mock(ProjectEmbeddingService.class);
    private CategoryIntentResolver resolver;

    private ProjectCategory category(Long id, Long parentId, String name) {
        ProjectCategory cat = ProjectCategory.create(parentId, name);
        ReflectionTestUtils.setField(cat, "id", id);
        return cat;
    }

    @BeforeEach
    void setUp() {
        resolver = new CategoryIntentResolver(categoryRepository, embeddingService);
    }

    @Test
    @DisplayName("질의 벡터와 1위 카테고리의 유사도가 임계값(0.40) 이상이고 격차(0.05)가 충분하면 해당 카테고리와 하위 카테고리를 반환한다")
    void resolveCategoryIntent_clearIntent_returnsCategoryWithDescendants() {
        ProjectCategory fashion = category(1L, null, "패션/의류");
        ProjectCategory top = category(2L, 1L, "상의");
        ProjectCategory tech = category(3L, null, "전자기기");

        when(categoryRepository.findAll()).thenReturn(List.of(fashion, top, tech));

        // fashion: [1.0, 0.0], top: [0.9, 0.1], tech: [0.0, 1.0]
        when(embeddingService.generateEmbedding("패션/의류")).thenReturn(new float[]{1.0f, 0.0f});
        when(embeddingService.generateEmbedding("패션/의류 > 상의")).thenReturn(new float[]{0.9f, 0.1f});
        when(embeddingService.generateEmbedding("전자기기")).thenReturn(new float[]{0.0f, 1.0f});

        // 쿼리 벡터: [0.95, 0.05] (패션/의류와 cosine > 0.99, 전자기기와 cosine < 0.1)
        float[] queryVector = new float[]{0.95f, 0.05f};

        List<Long> result = resolver.resolveCategoryIntent(queryVector);

        assertThat(result).contains(1L, 2L); // fashion(1L)과 그 자식 top(2L) 포함
        assertThat(result).doesNotContain(3L);
    }

    @Test
    @DisplayName("모든 카테고리와의 유사도가 임계값 미만이면 빈 리스트(NO_SCOPE)를 반환한다")
    void resolveCategoryIntent_belowThreshold_returnsEmpty() {
        ProjectCategory tech = category(1L, null, "전자기기");
        when(categoryRepository.findAll()).thenReturn(List.of(tech));
        when(embeddingService.generateEmbedding("전자기기")).thenReturn(new float[]{1.0f, 0.0f});

        // 쿼리 벡터: [0.0, 1.0] (tech와 cosine = 0.0)
        float[] queryVector = new float[]{0.0f, 1.0f};

        List<Long> result = resolver.resolveCategoryIntent(queryVector);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("임계값(threshold)이나 Gap을 엄격하게 올렸을 때 모호한 질의는 NO_SCOPE로 안전하게 차단된다")
    void resolveCategoryIntent_parameterTuning_safety() {
        ProjectCategory fashion = category(1L, null, "패션/의류");
        ProjectCategory living = category(2L, null, "홈/리빙");
        when(categoryRepository.findAll()).thenReturn(List.of(fashion, living));

        // fashion: [1.0, 0.0], living: [0.707, 0.707] (cosine = 0.707, gap = 0.293)
        when(embeddingService.generateEmbedding("패션/의류")).thenReturn(new float[]{1.0f, 0.0f});
        when(embeddingService.generateEmbedding("홈/리빙")).thenReturn(new float[]{0.707f, 0.707f});

        float[] queryVector = new float[]{1.0f, 0.0f};

        // 1) threshold 0.40, gap 0.05 -> gap(0.293) >= 0.05 이므로 통과
        List<Long> result1 = resolver.resolveCategoryIntent(queryVector, 0.40f, 0.05f);
        assertThat(result1).contains(1L);

        // 2) gap을 0.35로 올림 -> gap(0.293) < 0.35 이므로 NO_SCOPE
        List<Long> result2 = resolver.resolveCategoryIntent(queryVector, 0.40f, 0.35f);
        assertThat(result2).isEmpty();

        // 3) queryVector와 fashion의 cosine(1.0)보다 높은 threshold(1.05) 설정 -> 임계값 미달로 NO_SCOPE
        List<Long> result3 = resolver.resolveCategoryIntent(queryVector, 1.05f, 0.01f);
        assertThat(result3).isEmpty();
    }
}
