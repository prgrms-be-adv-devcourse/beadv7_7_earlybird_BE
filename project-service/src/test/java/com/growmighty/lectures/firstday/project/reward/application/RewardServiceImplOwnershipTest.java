package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardCreateRequest;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reward는 자기 creatorId가 없어 부모 프로젝트의 creatorId로 소유권을 검증한다 —
 * register/update/delete 전부 프로젝트 창작자가 아니면 거부하는지 확인한다.
 */
class RewardServiceImplOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final RewardServiceImpl rewardService = new RewardServiceImpl(rewardRepository, projectRepository);

    private Project project;
    private Reward reward;

    @BeforeEach
    void setUp() {
        project = Project.register(OWNER_ID, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project.approve();
        reward = Reward.register(1L, "노트커버", "설명", BigDecimal.valueOf(10_000), 10);

        when(projectRepository.findByIdForStatusCheck(1L)).thenReturn(Optional.of(project));
        when(rewardRepository.findById(1L)).thenReturn(Optional.of(reward));
    }

    @Test
    void register_byNonOwner_rejected() {
        RewardCreateRequest request = new RewardCreateRequest("이름", "설명", BigDecimal.TEN, 5);

        assertThatThrownBy(() -> rewardService.register(1L, OTHER_USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본인이 등록한 프로젝트");
        verify(rewardRepository, never()).save(any());
    }

    @Test
    void register_byOwner_succeeds() {
        RewardCreateRequest request = new RewardCreateRequest("이름", "설명", BigDecimal.TEN, 5);
        when(rewardRepository.save(any(Reward.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rewardService.register(1L, OWNER_ID, request);

        verify(rewardRepository).save(any(Reward.class));
    }

    @Test
    void update_byNonOwner_rejected() {
        RewardUpdateRequest request = new RewardUpdateRequest("새 이름", null, null, null, null);

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
        // 하드 삭제는 공개 전 프로젝트에서만 허용된다 — setUp()의 project는 approve()된 상태라 별도로 준비.
        Project unpublished = Project.register(OWNER_ID, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.findByIdForStatusCheck(1L)).thenReturn(Optional.of(unpublished));

        rewardService.delete(1L, OWNER_ID);

        verify(rewardRepository).delete(reward);
    }
}
