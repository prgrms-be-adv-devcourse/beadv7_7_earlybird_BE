package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectSort;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectDeadlineExtendRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectRejectRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectCloseExpiredResponse;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectCreatorResponse;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectReindexResponse;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.exception.ConcurrentUpdateFailedException;
import com.growmighty.lectures.firstday.project.project.infrastructure.kafka.ProjectClosedEvent;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private static final int AUTOCOMPLETE_MAX_RESULTS = 10;
    /** reindexAllProjects() 페이징 단위 — ES bulk 호출/임베딩 벌크 저장 배치 크기와 동일하게 맞춘다. */
    private static final int REINDEX_PAGE_SIZE = 50;

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
    private final ApplicationEventPublisher eventPublisher;
    private final FilePort filePort;
    private final Clock clock;

    /**
     * 조회(findByCreatorIdAndIdempotencyKey)와 삽입(createInternal)을 하나의 @Transactional로 묶지
     * 않는다 — 정확히 같은 순간 겹치는 진짜 동시 요청이 유니크 제약(creatorId, idempotencyKey)에서
     * 충돌하면 DataIntegrityViolationException을 이 메서드(트랜잭션 밖)에서 잡아야 한다. 같은 트랜잭션
     * 안에서 catch하면 flush 실패로 Hibernate가 이미 rollback-only 표시를 해서 커밋 시도가
     * UnexpectedRollbackException으로 변질된다(RewardStockTransactionExecutor.registerStockChange와
     * 동일한 이유).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectResponse create(Long creatorId, ProjectCreateRequest request) {
        Optional<Project> existing = projectRepository.findByCreatorIdAndIdempotencyKey(creatorId, request.idempotencyKey());
        if (existing.isPresent()) {
            return ProjectResponse.from(existing.get());
        }
        validateCategoryExists(request.categoryId());
        try {
            return selfProvider.getObject().createInternal(creatorId, request);
        } catch (DataIntegrityViolationException e) {
            return projectRepository.findByCreatorIdAndIdempotencyKey(creatorId, request.idempotencyKey())
                    .map(ProjectResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    /** create()가 트랜잭션 밖에서 검증을 끝낸 뒤 호출하는 내부용 — 외부에서 직접 부를 일은 없다. */
    @Override
    @Transactional
    public ProjectResponse createInternal(Long creatorId, ProjectCreateRequest request) {
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
        // 정렬을 명시적으로 고르지 않은 키워드 검색은 ES 관련도 순서(candidateProjectIds에 이미 담긴
        // 점수 내림차순)를 그대로 보여준다 — 검색창엔 최신순보다 관련도순이 기본값인 게 일반적인 UX다.
        // 정렬을 명시하면(예: 마감임박순) 그 선택을 그대로 존중해 기존 DB 정렬 경로를 탄다.
        if (candidateProjectIds != null && sort == null) {
            List<Project> projects = projectRepository.findAll(specification);
            return sortByRelevance(projects, candidateProjectIds).stream()
                    .map(ProjectResponse::from)
                    .toList();
        }
        ProjectSort effectiveSort = sort != null ? sort : ProjectSort.LATEST;
        return projectRepository.findAll(specification, effectiveSort.toSort()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    private List<Project> sortByRelevance(List<Project> projects, List<Long> relevanceOrder) {
        Map<Long, Integer> rank = new HashMap<>();
        for (int i = 0; i < relevanceOrder.size(); i++) {
            rank.put(relevanceOrder.get(i), i);
        }
        return projects.stream()
                .sorted(Comparator.comparing(project -> rank.get(project.getProjectId())))
                .toList();
    }

    /**
     * ES는 후보 projectId 인덱스일 뿐, 콘텐츠/가시성의 소스오브트루스가 아니다(findAll과 동일한 원칙) —
     * PENDING_REVIEW/REJECTED 상태로 색인된 문서나, 삭제 실패로 남아있는 stale 문서가 그대로 노출되지
     * 않도록 여기서 MySQL로 다시 걸러 필터링/정렬/제한한다. 제목도 ES 문서가 아니라 DB 엔티티에서
     * 가져오므로 수정 후 색인이 아직 안 따라잡은 상태에서도 최신 제목을 준다.
     */
    @Override
    public List<ProjectSuggestion> autocomplete(String keyword) {
        List<ProjectSuggestion> candidates = searchPort.autocomplete(keyword);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Long> candidateIds = candidates.stream().map(ProjectSuggestion::projectId).toList();
        return projectRepository.findAllById(candidateIds).stream()
                .filter(Project::isPublished)
                .sorted(Comparator.comparing(Project::getProjectId))
                .limit(AUTOCOMPLETE_MAX_RESULTS)
                .map(p -> new ProjectSuggestion(p.getProjectId(), p.getTitle()))
                .toList();
    }

    @Override
    public ProjectResponse findById(Long projectId, Long requesterId, UserRole requesterRole) {
        Project project = getProject(projectId);
        boolean canBypass = requesterRole == UserRole.ADMIN || project.getCreatorId().equals(requesterId);
        if (!project.isPublished() && !canBypass) {
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
     * orderPort.hasOrderedReward()(HTTP 호출)를 트랜잭션 밖에서 먼저 끝내야 그 응답을 기다리는 동안
     * DB 커넥션을 물고 있지 않는다(#196). 존재/소유 확인도 무락 조회(getProject)로 여기서 먼저 끝내고,
     * 실제 삭제(배타 락 선점 포함)는 deleteInternal()에 위임한다.
     *
     * 이 체크는 빠른 실패 경로일 뿐 정합성 보장의 핵심은 아니다 — 여기서 false가 나와도 배타 락이
     * 없는 틈에 새 decreaseStock()이 끼어들어 주문을 완성시킬 수 있다. 진짜 안전장치는
     * deleteInternal()이 배타 락을 잡은 뒤 다시 확인하는 두 번째 체크다(그 주석 참고).
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void delete(Long projectId, Long requesterId) {
        Project project = getProject(projectId);
        validateOwnership(project, requesterId);
        if (orderPort.hasOrderedReward(projectId)) {
            throw new IllegalStateException("후원(주문) 내역이 있는 프로젝트는 삭제할 수 없습니다. projectId=" + projectId);
        }
        selfProvider.getObject().deleteInternal(projectId, requesterId);
    }

    /**
     * 락 순서 역전 데드락 방지를 위해 getProject()(무락 조회) 대신
     * projectRepository.findByIdForDelete()(배타 락)로 Project를 맨 먼저 선점한다 —
     * ProjectRepository.findByIdForDelete() 주석 참고. delete()에서 이미 존재/소유/주문이력을
     * 확인했지만, 락 재획득 사이의 TOCTOU를 방어하기 위해 소유권을 여기서 다시 검증한다.
     *
     * hasOrderedReward()도 배타 락을 잡은 "직후"에 다시 확인해야 한다 — delete()의 사전 체크와 이
     * 락 획득 사이의 틈에 새 decreaseStock()이 공유 락을 얻어 주문을 완성시켰을 수 있기 때문이다.
     * decreaseStock()은 재고를 깎기 전에 항상 Project를 공유 락으로 먼저 잠그므로
     * (RewardStockTransactionExecutor 참고), 이 배타 락을 잡은 시점 이후로는 어떤 decreaseStock()도
     * 끼어들 수 없다. 그리고 order-service의 placeOrder()는 Order row를 재고 차감 HTTP 호출보다
     * 먼저 커밋하므로, 그 이전에 완성된 주문은 이 재확인에 반드시 걸린다 — 그래서 이 시점의 체크만이
     * 진짜 안전장치다.
     */
    @Override
    @Transactional
    public void deleteInternal(Long projectId, Long requesterId) {
        Project project = projectRepository.findByIdForDelete(projectId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 프로젝트입니다. projectId=" + projectId));
        validateOwnership(project, requesterId);
        if (orderPort.hasOrderedReward(projectId)) {
            throw new IllegalStateException("후원(주문) 내역이 있는 프로젝트는 삭제할 수 없습니다. projectId=" + projectId);
        }
        rewardServiceProvider.getObject().deleteAllByProject(projectId);
        projectRepository.delete(project);
        searchPort.remove(projectId);
        filePort.deleteProjectFiles(projectId);
    }
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectResponse cancel(Long projectId, Long requesterId, UserRole requesterRole) {
        Project project = getProject(projectId);
        validateOwnershipOrAdmin(project, requesterId, requesterRole);
        project.cancel();
        deactivateRewards(projectId);
        eventPublisher.publishEvent(new ProjectClosedEvent(projectId));
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

    /**
     * orderPort.getFundedAmount()(HTTP 호출)를 트랜잭션 밖에서 먼저 끝내야 그 응답을 기다리는 동안
     * DB 커넥션을 물고 있지 않는다(#196). 재시도(@Retryable)는 그 결과값만 들고 로컬 갱신만 하는
     * closeEarlyInternal()에 남겨서, 낙관적 락 충돌로 재시도해도 order-service를 다시 호출하지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectResponse closeEarly(Long projectId) {
        BigDecimal fundedAmount = orderPort.getFundedAmount(projectId);
        return selfProvider.getObject().closeEarlyInternal(projectId, fundedAmount);
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public ProjectResponse closeEarlyInternal(Long projectId, BigDecimal fundedAmount) {
        Project project = getProject(projectId);
        project.updateFundedAmount(fundedAmount);
        project.closeEarlyAsSucceeded();
        deactivateRewards(projectId);
        eventPublisher.publishEvent(new ProjectClosedEvent(projectId));
        return ProjectResponse.from(project);
    }

    @Recover
    public ProjectResponse recoverCloseEarlyInternalConflict(RuntimeException e, Long projectId, BigDecimal fundedAmount) {
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
    public ProjectCloseExpiredResponse closeExpiredProjects() {
        List<Project> expired = projectRepository.findByStatusAndEndAtLessThan(ProjectStatus.IN_PROGRESS, LocalDate.now(clock));
        ProjectService self = selfProvider.getObject();
        List<Long> closed = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        for (Project project : expired) {
            try {
                self.closeProjectByDeadline(project.getProjectId());
                closed.add(project.getProjectId());
            } catch (RuntimeException e) {
                // 한 프로젝트 처리 실패가 같은 배치 실행의 나머지 프로젝트까지 롤백시키지 않도록 격리.
                log.warn("프로젝트 마감 처리 실패. projectId={}", project.getProjectId(), e);
                failed.add(project.getProjectId());
            }
        }
        return new ProjectCloseExpiredResponse(closed.size(), closed, failed);
    }

    /**
     * orderPort.getFundedAmount()(HTTP 호출)를 트랜잭션 밖에서 먼저 끝내야 그 응답을 기다리는 동안
     * DB 커넥션을 물고 있지 않는다(#196). 재시도(@Retryable)는 그 결과값만 들고 로컬 갱신만 하는
     * closeProjectByDeadlineInternal()에 남겨서, 낙관적 락 충돌로 재시도해도 order-service를 다시
     * 호출하지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void closeProjectByDeadline(Long projectId) {
        BigDecimal fundedAmount = orderPort.getFundedAmount(projectId);
        selfProvider.getObject().closeProjectByDeadlineInternal(projectId, fundedAmount);
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void closeProjectByDeadlineInternal(Long projectId, BigDecimal fundedAmount) {
        Project project = getProject(projectId);
        project.updateFundedAmount(fundedAmount);
        project.closeByDeadline();
        deactivateRewards(projectId);
        eventPublisher.publishEvent(new ProjectClosedEvent(projectId));
    }

    @Recover
    public void recoverCloseProjectByDeadlineInternalConflict(RuntimeException e, Long projectId, BigDecimal fundedAmount) {
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
     * 클래스 레벨 @Transactional(readOnly=true)을 이 메서드만 NOT_SUPPORTED로 무시한다 — 그러지
     * 않으면 페이지 루프 전체(임베딩 생성용 OpenAI 호출 포함, 프로젝트 수만큼 반복)가 트랜잭션 하나에
     * DB 커넥션을 계속 물고 있게 된다(#196과 같은 문제). NOT_SUPPORTED로 감싸면 findAll(pageable)
     * 호출마다, 그리고 searchPort.bulkIndex() 내부의 임베딩 벌크 저장(ProjectEmbeddingPersister)마다
     * 각자 짧은 트랜잭션을 새로 열고 바로 끝낸다.
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProjectReindexResponse reindexAllProjects() {
        Pageable pageable = PageRequest.of(0, REINDEX_PAGE_SIZE);
        Page<Project> page;
        int totalIndexed = 0;
        do {
            page = projectRepository.findAll(pageable);
            if (!page.isEmpty()) {
                searchPort.bulkIndex(page.getContent());
                totalIndexed += page.getNumberOfElements();
            }
            pageable = pageable.next();
        } while (page.hasNext());
        return new ProjectReindexResponse(totalIndexed);
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

    @Override
    public void reindex(Long projectId) {
        projectRepository.findById(projectId).ifPresent(searchPort::index);
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
