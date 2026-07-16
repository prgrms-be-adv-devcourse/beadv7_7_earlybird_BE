package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository projectCategoryRepository;

    @Override
    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        validateCategoryExists(request.categoryId());
        Project project = projectRepository.save(request.toEntity());
        return ProjectResponse.from(project);
    }

    @Override
    public List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort) {
        Specification<Project> specification = buildSpecification(keyword, categoryId, status);
        ProjectSort effectiveSort = sort != null ? sort : ProjectSort.LATEST;
        return projectRepository.findAll(specification, effectiveSort.toSort()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    public ProjectResponse findById(Long projectId) {
        return ProjectResponse.from(getProject(projectId));
    }

    @Override
    @Transactional
    public ProjectResponse update(Long projectId, ProjectUpdateRequest request) {
        Project project = getProject(projectId);
        if (project.isPublished()) {
            if (request.hasPublishOnlyRestrictedField()) {
                throw new IllegalArgumentException(
                        "공개된 프로젝트는 summary/description/thumbnailId만 수정할 수 있습니다. endAt 연장은 관리자 전용 API를 이용하세요.");
            }
            project.updateAfterPublish(request.summary(), request.description(), request.thumbnailId());
        } else {
            if (request.categoryId() != null) {
                validateCategoryExists(request.categoryId());
            }
            project.updateBeforePublish(request.title(), request.categoryId(), request.summary(), request.description(),
                    request.thumbnailId(), request.goalAmount(), request.startAt(), request.endAt());
        }
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional
    public void delete(Long projectId) {
        // TODO(팀): 후원 발생 여부 검증 — 현재는 항상 삭제 가능하다고 가정한다.
        projectRepository.delete(getProject(projectId));
    }

    @Override
    public List<ProjectResponse> findByCreator(Long creatorId) {
        return projectRepository.findByCreatorId(creatorId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    public List<ProjectResponse> findByStatus(ProjectStatus status) {
        return projectRepository.findByStatus(status).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ProjectResponse approve(Long projectId) {
        Project project = getProject(projectId);
        project.approve();
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional
    public ProjectResponse reject(Long projectId, ProjectRejectRequest request) {
        Project project = getProject(projectId);
        project.reject(request.reason());
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional
    public ProjectResponse extendDeadline(Long projectId, ProjectDeadlineExtendRequest request) {
        Project project = getProject(projectId);
        project.extendDeadline(request.endAt());
        return ProjectResponse.from(project);
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
    }

    private void validateCategoryExists(Long categoryId) {
        if (!projectCategoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("존재하지 않는 카테고리입니다. categoryId=" + categoryId);
        }
    }

    private Specification<Project> buildSpecification(String keyword, Long categoryId, ProjectStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("title"), pattern),
                        cb.like(root.get("summary"), pattern)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
