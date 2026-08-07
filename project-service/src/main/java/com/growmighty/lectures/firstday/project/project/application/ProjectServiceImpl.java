package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectCreatorResponse;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository projectCategoryRepository;
    // closeExpiredProjects()가 같은 빈의 @Retryable 메서드를 self-invocation으로 부르면 프록시를
    // 안 거쳐서 재시도가 아예 발동 안 한다 — ObjectProvider로 프록시 인스턴스를 지연 조회해 우회한다.
    private final ObjectProvider<ProjectService> selfProvider;
    // RewardService가 반대로 ProjectService(ObjectProvider<ProjectService>)를 필요로 해서 생기는
    // 순환 빈 의존을 ObjectProvider로 지연 조회해 끊는다 — 생성자 주입 그대로면 Spring이 컨테이너
    // 기동 시점에 서로를 먼저 완성해야 해서 순환 참조 예외가 난다.
    private final ObjectProvider<RewardService> rewardServiceProvider;

    private final OrderPort orderPort;
    private final ProjectSearchPort searchPort;

    @Override
    @Transactional
    public ProjectResponse create(Long creatorId, ProjectCreateRequest request) {
        validateCategoryExists(request.categoryId());
        Project project = projectRepository.save(request.toEntity(creatorId));
        searchPort.index(project);
        return ProjectResponse.from(project);
    }

    @Override
    public List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort, UserRole requesterRole) {
        List<Long> candidateProjectIds = null;
        if (keyword != null && !keyword.isBlank()) {
            candidateProjectIds = searchPort.search(keyword);
            if (candidateProjectIds.isEmpty()) {
                return List.of();
            }
        }
        Specification<Project> specification = buildSpecification(candidateProjectIds, categoryId, status, requesterRole);
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
        searchPort.index(project);
        return ProjectResponse.from(project);
    }

    /**
     * 락 순서 역전 데드락 방지를 위해 getProject()(무락 조회) 대신
     * projectRepository.findByIdForDelete()(배타 락)로 Project를 맨 먼저 선점한다 —
     * ProjectRepository.findByIdForDelete() 주석 참고.
     */
    @Override
    @Transactional
    public void delete(Long projectId, Long requesterId) {
        Project project = projectRepository.findByIdForDelete(projectId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
        validateOwnership(project, requesterId);
        if (orderPort.hasOrderedReward(projectId)) {
            throw new IllegalStateException("후원(주문) 내역이 있는 프로젝트는 삭제할 수 없습니다. projectId=" + projectId);
        }
        rewardServiceProvider.getObject().deleteAllByProject(projectId);
        projectRepository.delete(project);
        searchPort.remove(projectId);
    }
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectResponse cancel(Long projectId, Long requesterId, UserRole requesterRole) {
        Project project = getProject(projectId);
        validateOwnershipOrAdmin(project, requesterId, requesterRole);
        project.cancel();
        deactivateRewards(projectId);
        return ProjectResponse.from(project);
    }

    @Recover
    public ProjectResponse recoverCancelConflict(RuntimeException e, Long projectId, Long requesterId, UserRole requesterRole) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "취소 처리 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
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
    public ProjectCreatorResponse getCreator(Long projectId) {
        return ProjectCreatorResponse.from(getProject(projectId));
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
        project.updateFundedAmount(orderPort.getFundedAmount(projectId));
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
     *
     * NOT_SUPPORTED로 이 메서드 자체는 트랜잭션을 열지 않는다 — 애노테이션을 안 달면 클래스 레벨
     * @Transactional(readOnly=true)를 그대로 상속해서, self.closeProjectByDeadline()이 새 트랜잭션을
     * 여는 대신 그 readOnly 트랜잭션에 합류해버린다(REQUIRED 기본값). 그러면 Hibernate가 그 세션에서
     * 로드한 엔티티를 dirty-checking 대상에서 빼버려서, status 변경이 예외 없이 조용히 커밋 안 된다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void closeExpiredProjects() {
        List<Project> expired = projectRepository.findByStatusAndEndAtLessThan(ProjectStatus.IN_PROGRESS, LocalDate.now());
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
        Project project = getProject(projectId);
        project.updateFundedAmount(orderPort.getFundedAmount(projectId));
        project.closeByDeadline();
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

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void updateFundedAmount(Long projectId, BigDecimal fundedAmount) {
        getProject(projectId).updateFundedAmount(fundedAmount);
    }

    @Recover
    public void recoverUpdateFundedAmountConflict(RuntimeException e, Long projectId, BigDecimal fundedAmount) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "모금액 갱신 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    /**
     * closeExpiredProjects()와 같은 이유로 한 트랜잭션으로 묶지 않는다 — 프로젝트 하나의 pull 실패나
     * 락 충돌이 같은 배치 실행의 나머지 프로젝트까지 막으면 안 된다. selfProvider로 프록시를 거쳐
     * updateFundedAmount() 한 건마다 독립된 트랜잭션 + 재시도를 갖게 한다.
     *
     * closeExpiredProjects()와 같은 이유로 NOT_SUPPORTED — 안 그러면 클래스 레벨 readOnly 트랜잭션에
     * self.updateFundedAmount()가 합류해서 fundedAmount 갱신이 조용히 커밋되지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcileFundedAmounts() {
        List<Project> inProgress = projectRepository.findByStatus(ProjectStatus.IN_PROGRESS);
        ProjectService self = selfProvider.getObject();
        for (Project project : inProgress) {
            try {
                BigDecimal fundedAmount = orderPort.getFundedAmount(project.getProjectId());
                self.updateFundedAmount(project.getProjectId(), fundedAmount);
            } catch (RuntimeException e) {
                log.warn("모금액 보정 실패. projectId={}", project.getProjectId(), e);
            }
        }
    }

    /**
     * 프로젝트가 마감(성공/실패/조기종료)되면 그 리워드들도 비활성화한다. Reward.isOrderable()이
     * 부모 프로젝트 상태를 모르고 자기 active/재고만 보기 때문에, 여기서 안 꺼주면 이미 마감된
     * 프로젝트의 리워드가 RewardResponse.orderable=true로 잘못 응답한다(실제 주문은 Project.isOpen()이
     * 막아주지만, 조회 응답은 그대로 거짓 정보를 준다).
     */
    private void deactivateRewards(Long projectId) {
        rewardServiceProvider.getObject().deactivateAllByProject(projectId);
    }

    @Override
    public Optional<ProjectStatusView> findStatusView(Long projectId) {
        return projectRepository.findByIdForStatusCheck(projectId)
                .map(project -> new ProjectStatusView(
                        project.isPublished(), project.isClosed(), project.isOpen(), project.getStatus().name(),
                        project.getCreatorId()));
    }

    /** board-service ProjectNotice.validateOwnership과 동일한 관례 — ADMIN이면 통과, 아니면 본인 확인. */
    private void validateOwnershipOrAdmin(Project project, Long requesterId, UserRole requesterRole) {
        if (requesterRole == UserRole.ADMIN) {
            return;
        }
        if (!project.getCreatorId().equals(requesterId)) {
            throw new IllegalArgumentException("본인이 등록한 프로젝트만 취소할 수 있습니다. projectId=" + project.getProjectId());
        }
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

    private Specification<Project> buildSpecification(List<Long> candidateProjectIds, Long categoryId, ProjectStatus status, UserRole requesterRole) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 공개 목록 조회에서는 심사 대기/반려 프로젝트를 항상 제외한다(status 파라미터로 요청해도 결과 없음).
            // ADMIN은 심사 대기/반려 프로젝트도 봐야 하므로 이 제외를 적용하지 않는다.
            // 창작자 본인의 심사 대기/반려 프로젝트는 findByCreator(/me)로 확인한다.
            if (requesterRole != UserRole.ADMIN) {
                predicates.add(cb.and(
                        cb.notEqual(root.get("status"), ProjectStatus.PENDING_REVIEW),
                        cb.notEqual(root.get("status"), ProjectStatus.REJECTED)));
            }
            if (candidateProjectIds != null) {
                predicates.add(root.get("projectId").in(candidateProjectIds));
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
