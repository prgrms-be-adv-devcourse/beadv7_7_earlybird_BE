package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectCategoryServiceImpl implements ProjectCategoryService {

    private final ProjectCategoryRepository projectCategoryRepository;

    @Override
    @Transactional
    public ProjectCategoryResponse create(ProjectCategoryCreateRequest request) {
        validateParentExists(request.parentProjectCategoryId());
        ProjectCategory projectCategory = projectCategoryRepository.save(request.toEntity());
        return ProjectCategoryResponse.leaf(projectCategory);
    }
    // flat한 형태의 db데이터를 findAll로 한번에 불러와서 자식-부모로 트리구조를 만듬(N+1문제 방지)
    @Override
    public List<ProjectCategoryResponse> findAllAsTree() {
        List<ProjectCategory> projectCategories = projectCategoryRepository.findAll();
        Map<Long, List<ProjectCategory>> childrenByParentId = projectCategories.stream()
                .filter(projectCategory -> !projectCategory.isRoot())
                .collect(Collectors.groupingBy(ProjectCategory::getParentProjectCategoryId));

        return projectCategories.stream()
                .filter(ProjectCategory::isRoot)
                .map(root -> toTree(root, childrenByParentId))
                .toList();
    }

    @Override
    public ProjectCategoryResponse findById(Long projectCategoryId) {
        return ProjectCategoryResponse.leaf(getProjectCategory(projectCategoryId));
    }

    @Override
    @Transactional
    public ProjectCategoryResponse update(Long projectCategoryId, ProjectCategoryUpdateRequest request) {
        ProjectCategory projectCategory = getProjectCategory(projectCategoryId);
        if (!Objects.equals(projectCategory.getParentProjectCategoryId(), request.parentProjectCategoryId())) {
            validateParentExists(request.parentProjectCategoryId());
            validateNotSelfOrDescendant(projectCategoryId, request.parentProjectCategoryId());
            projectCategory.changeParent(request.parentProjectCategoryId());
        }
        projectCategory.rename(request.name());
        return ProjectCategoryResponse.leaf(projectCategory);
    }

    @Override
    @Transactional
    public void delete(Long projectCategoryId) {
        projectCategoryRepository.delete(getProjectCategory(projectCategoryId));
    }

    private ProjectCategoryResponse toTree(ProjectCategory projectCategory, Map<Long, List<ProjectCategory>> childrenByParentId) {
        List<ProjectCategoryResponse> children = childrenByParentId
                .getOrDefault(projectCategory.getId(), List.of())
                .stream()
                .map(child -> toTree(child, childrenByParentId))
                .toList();
        return ProjectCategoryResponse.of(projectCategory, children);
    }

    private ProjectCategory getProjectCategory(Long projectCategoryId) {
        return projectCategoryRepository.findById(projectCategoryId)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다. projectCategoryId=" + projectCategoryId));
    }

    private void validateParentExists(Long parentProjectCategoryId) {
        if (parentProjectCategoryId != null && !projectCategoryRepository.existsById(parentProjectCategoryId)) {
            throw new EntityNotFoundException("상위 카테고리를 찾을 수 없습니다. parentProjectCategoryId=" + parentProjectCategoryId);
        }
    }

    /**
     * 자기 자신이나 자손을 부모로 설정하는 것을 막는다 (순환 참조 방지).
     * newParentProjectCategoryId부터 부모를 따라 루트까지 거슬러 올라가면서 projectCategoryId가 나오는지 확인한다.
     * 나오면 newParentProjectCategoryId가 projectCategoryId의 자손이라는 뜻이므로 순환이 생긴다.
     */
    private void validateNotSelfOrDescendant(Long projectCategoryId, Long newParentProjectCategoryId) {
        if (newParentProjectCategoryId == null) {
            return;
        }
        if (projectCategoryId.equals(newParentProjectCategoryId)) {
            throw new IllegalArgumentException("자기 자신을 상위 카테고리로 설정할 수 없습니다.");
        }
        Long cursor = newParentProjectCategoryId;
        while (cursor != null) {
            if (projectCategoryId.equals(cursor)) {
                throw new IllegalArgumentException("자손 카테고리를 상위 카테고리로 설정할 수 없습니다.");
            }
            cursor = projectCategoryRepository.findById(cursor)
                    .map(ProjectCategory::getParentProjectCategoryId)
                    .orElse(null);
        }
    }
}
