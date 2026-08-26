package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 카테고리 계층 정보를 임베딩하여 메모리에 캐싱하고,
 * 사용자 질의 벡터와의 Cosine Similarity를 직접 계산하여 의도(Category Intent)를 추론한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CategoryIntentResolver {

    /** 카테고리 의도 판별 최소 유사도 하한 */
    private static final float INTENT_CONFIDENCE_THRESHOLD = 0.40f;
    /** 다른 루트 카테고리군과의 최소 격차 (모호한 쿼리의 Narrow Scoping 방지) */
    private static final float MIN_CONFIDENCE_GAP = 0.05f;

    private final ProjectCategoryRepository categoryRepository;
    private final ProjectEmbeddingService embeddingService;

    // CategoryId -> Embedding Vector 캐시
    private final Map<Long, float[]> categoryVectorCache = new ConcurrentHashMap<>();

    public record CategoryMatch(Long categoryId, Long rootCategoryId, String categoryName, double similarity) {}

    /**
     * 기본 임계값(threshold=0.40, gap=0.05)을 사용하여 카테고리 의도를 판별한다.
     */
    public List<Long> resolveCategoryIntent(float[] queryVector) {
        return resolveCategoryIntent(queryVector, INTENT_CONFIDENCE_THRESHOLD, MIN_CONFIDENCE_GAP);
    }

    /**
     * 지정된 threshold와 gap을 사용하여 카테고리 의도를 판별한다. (파라미터 튜닝 및 테스트용)
     */
    public List<Long> resolveCategoryIntent(float[] queryVector, float threshold, float minGap) {
        if (queryVector == null || queryVector.length == 0) {
            return List.of();
        }

        ensureCategoryVectorsCached();
        if (categoryVectorCache.isEmpty()) {
            return List.of();
        }

        List<ProjectCategory> allCategories = categoryRepository.findAll();
        Map<Long, ProjectCategory> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(ProjectCategory::getId, c -> c, (a, b) -> a));

        List<CategoryMatch> matches = new ArrayList<>();
        for (Map.Entry<Long, float[]> entry : categoryVectorCache.entrySet()) {
            Long catId = entry.getKey();
            float[] catVector = entry.getValue();
            double sim = cosineSimilarity(queryVector, catVector);
            Long rootId = findRootCategoryId(catId, categoryMap);
            String name = categoryMap.containsKey(catId) ? categoryMap.get(catId).getName() : "카테고리#" + catId;
            matches.add(new CategoryMatch(catId, rootId, name, sim));
        }

        matches.sort(Comparator.comparingDouble(CategoryMatch::similarity).reversed());

        if (matches.isEmpty()) {
            return List.of();
        }

        CategoryMatch top1 = matches.get(0);
        
        double topOtherRootSim = matches.stream()
                .filter(m -> !m.rootCategoryId().equals(top1.rootCategoryId()))
                .mapToDouble(CategoryMatch::similarity)
                .findFirst()
                .orElse(0.0);

        double gap = top1.similarity() - topOtherRootSim;

        if (top1.similarity() >= threshold && gap >= minGap) {
            log.info("[CategoryIntent] 명확한 카테고리 의도 감지: '{}' (유사도={:.4f}, 타 루트군과 gap={:.4f})",
                    top1.categoryName(), top1.similarity(), gap);
            return withDescendants(List.of(top1.categoryId()), allCategories);
        }

        log.debug("[CategoryIntent] 카테고리 의도 모호/임계값 미달 (Top1: '{}' sim={:.4f}, 타 루트군 gap={:.4f}) -> 전체 검색 폴백",
                top1.categoryName(), top1.similarity(), gap);
        return List.of();
    }

    private Long findRootCategoryId(Long categoryId, Map<Long, ProjectCategory> categoryMap) {
        ProjectCategory cur = categoryMap.get(categoryId);
        Set<Long> visited = new HashSet<>();
        while (cur != null && cur.getParentProjectCategoryId() != null && !visited.contains(cur.getId())) {
            visited.add(cur.getId());
            cur = categoryMap.get(cur.getParentProjectCategoryId());
        }
        return cur != null ? cur.getId() : categoryId;
    }

    private void ensureCategoryVectorsCached() {
        if (!categoryVectorCache.isEmpty()) {
            return;
        }
        List<ProjectCategory> categories = categoryRepository.findAll();
        Map<Long, ProjectCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProjectCategory::getId, c -> c, (a, b) -> a));

        for (ProjectCategory cat : categories) {
            String hierarchy = buildHierarchy(cat, categoryMap);
            float[] vector = embeddingService.generateEmbedding(hierarchy);
            if (vector != null) {
                categoryVectorCache.put(cat.getId(), vector);
            }
        }
    }

    private String buildHierarchy(ProjectCategory category, Map<Long, ProjectCategory> map) {
        if (category.getParentProjectCategoryId() != null) {
            ProjectCategory parent = map.get(category.getParentProjectCategoryId());
            if (parent != null) {
                return parent.getName() + " > " + category.getName();
            }
        }
        return category.getName();
    }

    private List<Long> withDescendants(List<Long> rootIds, List<ProjectCategory> allCategories) {
        Map<Long, List<Long>> childMap = allCategories.stream()
                .filter(c -> c.getParentProjectCategoryId() != null)
                .collect(Collectors.groupingBy(ProjectCategory::getParentProjectCategoryId,
                        Collectors.mapping(ProjectCategory::getId, Collectors.toList())));

        List<Long> result = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>(rootIds);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            result.add(id);
            queue.addAll(childMap.getOrDefault(id, List.of()));
        }
        return result;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
