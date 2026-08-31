package com.growmighty.lectures.firstday.project.category.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 카테고리 계층(부모 id 참조)을 한 번 메모리에 올려두고 조상 경로 / 하위 트리를 계산한다.
 * 카테고리 수가 적어 호출 시점에 findAll() 후 인메모리 순회로 충분하다.
 * 부모 순환 참조가 있어도 무한루프에 빠지지 않는다.
 */
public final class CategoryHierarchy {

    private final Map<Long, ProjectCategory> byId;
    private final Map<Long, List<Long>> childIdsByParentId;

    private CategoryHierarchy(List<ProjectCategory> categories) {
        this.byId = categories.stream()
                .collect(Collectors.toMap(ProjectCategory::getId, c -> c, (a, b) -> a));
        this.childIdsByParentId = categories.stream()
                .filter(c -> c.getParentProjectCategoryId() != null)
                .collect(Collectors.groupingBy(ProjectCategory::getParentProjectCategoryId,
                        Collectors.mapping(ProjectCategory::getId, Collectors.toList())));
    }

    public static CategoryHierarchy of(List<ProjectCategory> categories) {
        return new CategoryHierarchy(categories);
    }

    /** 주어진 카테고리들과 그 하위 전체 id (입력 id는 존재하지 않아도 그대로 포함된다). */
    public List<Long> withDescendants(Collection<Long> categoryIds) {
        List<Long> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>(categoryIds);
        while (!toVisit.isEmpty()) {
            Long categoryId = toVisit.poll();
            if (!visited.add(categoryId)) {
                continue;
            }
            result.add(categoryId);
            toVisit.addAll(childIdsByParentId.getOrDefault(categoryId, List.of()));
        }
        return result;
    }

    /** 루트부터 자기 자신까지의 이름 경로 (예: {@code "패션 > 의류 > 상의"}). 모르는 id면 빈 문자열. */
    public String path(Long categoryId) {
        ProjectCategory category = categoryId == null ? null : byId.get(categoryId);
        Deque<String> names = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        while (category != null && visited.add(category.getId())) {
            names.addFirst(category.getName());
            Long parentId = category.getParentProjectCategoryId();
            category = parentId == null ? null : byId.get(parentId);
        }
        return String.join(" > ", names);
    }

    /** 조상 체인 최상단 id. 모르는 id면 그대로 돌려준다. */
    public Long rootId(Long categoryId) {
        ProjectCategory category = categoryId == null ? null : byId.get(categoryId);
        Set<Long> visited = new HashSet<>();
        Long rootId = categoryId;
        while (category != null && visited.add(category.getId())) {
            rootId = category.getId();
            Long parentId = category.getParentProjectCategoryId();
            category = parentId == null ? null : byId.get(parentId);
        }
        return rootId;
    }
}
