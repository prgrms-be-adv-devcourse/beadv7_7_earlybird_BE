package com.growmighty.lectures.firstday.project.category.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryCreateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.request.ProjectCategoryUpdateRequest;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryResponse;
import com.growmighty.lectures.firstday.project.category.presentation.dto.response.ProjectCategoryTreeResponse;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final ProjectRepository projectRepository;
    private final ObjectProvider<ProjectCategoryService> selfProvider;

    /**
     * create/update/delete는 전부 카테고리 트리(부모-자식 관계)를 read-then-write로 검증한다 —
     * 예를 들어 동시에 두 카테고리가 서로를 부모로 설정하면(A→B, B→A) 둘 다 상대방의 커밋 전
     * 상태를 보고 통과해버려 순환이 생길 수 있고, update()와 delete()가 같은 카테고리를
     * 동시에 건드리면 방금 부모로 지정하려는 카테고리가 그 사이에 삭제돼 존재하지 않는
     * categoryId를 가리키게 될 수도 있다(참조무결성 체크와 실제 삭제 사이의 TOCTOU).
     * 세 메서드 다 이 트리 전체에 대해 서로 배타적으로 실행돼야 이런 경합을 막을 수 있어서
     * 셋 다 같은 락으로 직렬화한다. 대안(DB 낙관적/비관적 락)도 가능하지만, 카테고리 변경은
     * 관리자 전용의 드문 작업이라 JVM 레벨 직렬화로 충분하다고 판단했다(단일 인스턴스 전제
     * — 다중 인스턴스로 확장하면 분산 락으로 교체 필요).
     */
    private final Object treeLock = new Object();

    @Override
    // update()와 같은 이유로 트랜잭션 자체를 열지 않는다 — 아래 3개 메서드(create/update/delete)
    // 전부 이 패턴을 따른다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectCategoryResponse create(ProjectCategoryCreateRequest request) {
        synchronized (treeLock) {
            return selfProvider.getObject().createTransactional(request);
        }
    }

    @Override
    @Transactional
    public ProjectCategoryResponse createTransactional(ProjectCategoryCreateRequest request) {
        validateParentExists(request.parentProjectCategoryId());
        ProjectCategory projectCategory = projectCategoryRepository.save(request.toEntity());
        return ProjectCategoryResponse.from(projectCategory);
    }

    @Override
    public List<ProjectCategoryTreeResponse> findAllAsTree() {
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
        return ProjectCategoryResponse.from(getProjectCategory(projectCategoryId));
    }

    @Override
    // 클래스 레벨 @Transactional(readOnly = true)를 그대로 물려받으면 안 된다 — 그러면 이
    // 메서드가 먼저 읽기전용 트랜잭션을 열어버리고, updateTransactional()이 REQUIRED 전파로
    // 그 트랜잭션에 합류해서 변경사항이 커밋 시 플러시 안 되고 조용히 사라진다.
    // NOT_SUPPORTED로 트랜잭션 자체를 안 열어야 updateTransactional()이 자기 트랜잭션을 새로 연다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectCategoryResponse update(Long projectCategoryId, ProjectCategoryUpdateRequest request) {
        // synchronized 블록이 트랜잭션 커밋까지 감싸야 해서(그래야 다음 스레드가 항상 커밋된
        // 최신 상태를 본다), self-invocation을 피해 selfProvider로 프록시를 거쳐 호출한다 —
        // ProjectServiceImpl.closeExpiredProjects()와 같은 이유.
        synchronized (treeLock) {
            return selfProvider.getObject().updateTransactional(projectCategoryId, request);
        }
    }

    @Override
    @Transactional
    public ProjectCategoryResponse updateTransactional(Long projectCategoryId, ProjectCategoryUpdateRequest request) {
        ProjectCategory projectCategory = getProjectCategory(projectCategoryId);
        if (!Objects.equals(projectCategory.getParentProjectCategoryId(), request.parentProjectCategoryId())) {
            validateParentExists(request.parentProjectCategoryId());
            validateNotSelfOrDescendant(projectCategoryId, request.parentProjectCategoryId());
            projectCategory.changeParent(request.parentProjectCategoryId());
        }
        // ES 색인은 categoryName이 아니라 categoryId를 저장하므로(ProjectDocument 참고), 이름만
        // 바뀌는 개명은 소속 프로젝트를 재색인할 필요가 없다 — id는 그대로다.
        projectCategory.rename(request.name());
        return ProjectCategoryResponse.from(projectCategory);
    }

    /**
     * 하위 카테고리나 이 카테고리를 참조하는 프로젝트가 있으면 삭제를 거부한다 — FK가 없어서
     * 체크 없이 지우면 자식 카테고리는 트리에서 조용히 증발하고, 프로젝트는 존재하지 않는
     * categoryId를 가리키게 된다. 이 체크와 실제 삭제 사이에 동시에 들어온 create()/update()가
     * 끼어들 수 있어(TOCTOU) treeLock으로 그것들과 서로 배타적으로 실행되게 한다 — treeLock
     * 필드 주석 참고.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(Long projectCategoryId) {
        synchronized (treeLock) {
            selfProvider.getObject().deleteTransactional(projectCategoryId);
        }
    }

    @Override
    @Transactional
    public void deleteTransactional(Long projectCategoryId) {
        ProjectCategory projectCategory = getProjectCategory(projectCategoryId);
        if (projectCategoryRepository.existsByParentProjectCategoryId(projectCategoryId)) {
            throw new IllegalStateException("하위 카테고리가 있어 삭제할 수 없습니다. 하위 카테고리를 먼저 삭제해주세요.");
        }
        if (projectRepository.existsByCategoryId(projectCategoryId)) {
            throw new IllegalStateException("이 카테고리를 사용 중인 프로젝트가 있어 삭제할 수 없습니다.");
        }
        projectCategoryRepository.delete(projectCategory);
    }

    private ProjectCategoryTreeResponse toTree(ProjectCategory projectCategory, Map<Long, List<ProjectCategory>> childrenByParentId) {
        List<ProjectCategoryTreeResponse> children = childrenByParentId
                .getOrDefault(projectCategory.getId(), List.of())
                .stream()
                .map(child -> toTree(child, childrenByParentId))
                .toList();
        return ProjectCategoryTreeResponse.of(projectCategory, children);
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
