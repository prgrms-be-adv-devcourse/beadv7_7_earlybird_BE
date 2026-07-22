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
import com.growmighty.lectures.firstday.project.reward.application.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository projectCategoryRepository;
    private final RewardRepository rewardRepository;
    // closeExpiredProjects()가 같은 빈의 @Retryable 메서드를 self-invocation으로 부르면 프록시를
    // 안 거쳐서 재시도가 아예 발동 안 한다 — ObjectProvider로 프록시 인스턴스를 지연 조회해 우회한다.
    private final ObjectProvider<ProjectService> selfProvider;

    @Override
    @Transactional
    public ProjectResponse create(Long creatorId, ProjectCreateRequest request) {
        validateCategoryExists(request.categoryId());
        Project project = projectRepository.save(request.toEntity(creatorId));
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
    // TODO(팀): 소유자/관리자는 자기 PENDING_REVIEW·REJECTED 프로젝트를 이 엔드포인트로
    //           조회할 방법이 없다 (인증 도입 전이라 창작자는 /me 목록으로만 확인 가능).
    //           인증 도입 후 creatorId/관리자 여부를 받아 그 경우엔 이 제한을 우회하도록 보강 필요.
    public ProjectResponse findById(Long projectId) {
        Project project = getProject(projectId);
        if (!project.isPublished()) {
            throw new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId);
        }
        return ProjectResponse.from(project);
    }

    @Override
    @Transactional
    public ProjectResponse update(Long projectId, Long requesterId, ProjectUpdateRequest request) {
        Project project = getProject(projectId);
        validateOwnership(project, requesterId);
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
    public void delete(Long projectId, Long requesterId) {
        // TODO(팀): 후원 발생 여부 검증 — 현재는 항상 삭제 가능하다고 가정한다.
        Project project = getProject(projectId);
        validateOwnership(project, requesterId);
        rewardRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
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
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectResponse extendDeadline(Long projectId, ProjectDeadlineExtendRequest request) {
        Project project = getProject(projectId);
        project.extendDeadline(request.endAt());
        return ProjectResponse.from(project);
    }

    @Recover
    public ProjectResponse recoverExtendDeadlineConflict(RuntimeException e, Long projectId, ProjectDeadlineExtendRequest request) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "마감일 연장 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectResponse closeEarly(Long projectId) {
        Project project = getProject(projectId);
        project.closeEarlyAsSucceeded();
        deactivateRewards(projectId);
        return ProjectResponse.from(project);
    }

    @Recover
    public ProjectResponse recoverCloseEarlyConflict(RuntimeException e, Long projectId) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "조기 마감 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    /**
     * closeExpiredProjects()는 한 트랜잭션으로 묶이면 안 된다 — 그러면 개별 재시도가 같은
     * 영속성 컨텍스트를 재사용해 엔티티를 새로 못 읽어오고, try/catch도 flush가 커밋 시점까지
     * 미뤄져서 실제로는 격리가 안 된다. 그래서 조회만 하고, 실제 처리는 프로젝트 하나당
     * closeProjectByDeadline() 호출로 위임해 각자 독립된 트랜잭션 + 재시도를 갖게 한다.
     */
    @Override
    public void closeExpiredProjects() {
        List<Project> expired = projectRepository.findByStatusAndEndAtLessThanEqual(ProjectStatus.IN_PROGRESS, LocalDate.now());
        ProjectService self = selfProvider.getObject();
        for (Project project : expired) {
            try {
                self.closeProjectByDeadline(project.getProjectId());
            } catch (RuntimeException e) {
                // 한 프로젝트 처리 실패가 같은 배치 실행의 나머지 프로젝트까지 롤백시키지 않도록 격리.
                log.warn("프로젝트 마감 처리 실패. projectId={}", project.getProjectId(), e);
            }
        }
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void closeProjectByDeadline(Long projectId) {
        getProject(projectId).closeByDeadline();
        deactivateRewards(projectId);
    }

    @Recover
    public void recoverCloseProjectByDeadlineConflict(RuntimeException e, Long projectId) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "프로젝트 마감 처리 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    /**
     * 프로젝트가 마감(성공/실패/조기종료)되면 그 리워드들도 비활성화한다. Reward.isOrderable()이
     * 부모 프로젝트 상태를 모르고 자기 active/재고만 보기 때문에, 여기서 안 꺼주면 이미 마감된
     * 프로젝트의 리워드가 RewardResponse.orderable=true로 잘못 응답한다(실제 주문은 Project.isOpen()이
     * 막아주지만, 조회 응답은 그대로 거짓 정보를 준다).
     */
    private void deactivateRewards(Long projectId) {
        rewardRepository.findByProjectId(projectId).forEach(Reward::deactivate);
    }

    private Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
    }

    /** board-service ProjectNotice.validateOwnership과 동일한 관례 — 소유자 불일치는 IllegalArgumentException(400). */
    private void validateOwnership(Project project, Long requesterId) {
        if (!project.getCreatorId().equals(requesterId)) {
            throw new IllegalArgumentException(
                "본인이 등록한 프로젝트만 수정/삭제할 수 있습니다. projectId=" + project.getProjectId());
        }
    }

    private void validateCategoryExists(Long categoryId) {
        if (!projectCategoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("존재하지 않는 카테고리입니다. categoryId=" + categoryId);
        }
    }

    private Specification<Project> buildSpecification(String keyword, Long categoryId, ProjectStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 공개 목록 조회에서는 심사 대기/반려 프로젝트를 항상 제외한다 (status 파라미터로 요청해도 결과 없음).
            // 창작자 본인의 심사 대기/반려 프로젝트는 findByCreator(/me)로 확인한다.
            predicates.add(cb.and(
                    cb.notEqual(root.get("status"), ProjectStatus.PENDING_REVIEW),
                    cb.notEqual(root.get("status"), ProjectStatus.REJECTED)));
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
