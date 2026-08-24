package com.growmighty.lectures.firstday.project.reward.application;

import java.util.UUID;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reward는 자기 creatorId가 없어 부모 프로젝트의 creatorId(ProjectStatusView 경유)로
 * 소유권을 검증한다 — register/update/delete 전부 프로젝트 창작자가 아니면 거부하는지 확인한다.
 */
class RewardServiceImplOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final StockChangeLogRepository stockChangeLogRepository = mock(StockChangeLogRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> projectServiceProvider = mock(ObjectProvider.class);
    private final RewardStockTransactionExecutor rewardStockTransactionExecutor =
            new RewardStockTransactionExecutor(rewardRepository, projectServiceProvider, stockChangeLogRepository);
    private final RewardServiceImpl rewardService =
            new RewardServiceImpl(rewardRepository, projectServiceProvider, rewardStockTransactionExecutor);

    private Reward reward;

    @BeforeEach
    void setUp() {
        ProjectStatusView published = new ProjectStatusView(true, false, true, "IN_PROGRESS", OWNER_ID);
        reward = Reward.register(1L, UUID.randomUUID(), "노트커버", "설명", BigDecimal.valueOf(10_000), 10);

        when(projectServiceProvider.getObject()).thenReturn(projectService);
        when(projectService.findStatusView(1L)).thenReturn(Optional.of(published));
        when(rewardRepository.findById(1L)).thenReturn(Optional.of(reward));
    }

    @Test
    void register_byNonOwner_rejected() {
        RewardCreateRequest request = new RewardCreateRequest("이름", "설명", BigDecimal.TEN, 5, UUID.randomUUID());

        assertThatThrownBy(() -> rewardService.register(1L, OTHER_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트");
        verify(rewardRepository, never()).save(any());
    }

    @Test
    void register_byOwner_succeeds() {
        RewardCreateRequest request = new RewardCreateRequest("이름", "설명", BigDecimal.TEN, 5, UUID.randomUUID());
        when(rewardRepository.save(any(Reward.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rewardService.register(1L, OWNER_ID, request);

        verify(rewardRepository).save(any(Reward.class));
    }

    @Test
    void register_duplicateIdempotencyKey_returnsExistingWithoutSaving() {
        UUID key = UUID.randomUUID();
        when(rewardRepository.findByProjectIdAndIdempotencyKey(1L, key)).thenReturn(Optional.of(reward));
        RewardCreateRequest request = new RewardCreateRequest("이름", "설명", BigDecimal.TEN, 5, key);

        rewardService.register(1L, OWNER_ID, request);

        verify(rewardRepository, never()).save(any());
    }

    @Test
    void update_byNonOwner_rejected() {
        RewardUpdateRequest request = new RewardUpdateRequest("새 이름", null, null, null, false, null);

        assertThatThrownBy(() -> rewardService.update(1L, OTHER_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트");
    }

    @Test
    void delete_byNonOwner_rejected() {
        assertThatThrownBy(() -> rewardService.delete(1L, OTHER_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트");
        verify(rewardRepository, never()).delete(any());
    }

    @Test
    void delete_byOwner_succeeds() {
        // 하드 삭제는 공개 전 프로젝트에서만 허용된다 — setUp()의 published는 승인된 상태라 별도로 준비.
        ProjectStatusView unpublished = new ProjectStatusView(false, false, false, "PENDING_REVIEW", OWNER_ID);
        when(projectService.findStatusView(1L)).thenReturn(Optional.of(unpublished));

        rewardService.delete(1L, OWNER_ID);

        verify(rewardRepository).delete(reward);
    }
}
