package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import com.growmighty.lectures.firstday.project.reward.presentation.dto.request.RewardQuantityDecreaseRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** ADMIN이 아니면 리워드 관리자 API도 project-service까지 통과하지 못하는지 검증한다. */
class RewardAdminControllerTest {

    private final RewardService rewardService = mock(RewardService.class);
    private final RewardAdminController controller = new RewardAdminController(rewardService);

    @Test
    void decreaseQuantity_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.decreaseQuantity(UserRole.CREATOR, 1L, new RewardQuantityDecreaseRequest(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("관리자만");
        verifyNoInteractions(rewardService);
    }

    @Test
    void deactivate_nonAdmin_rejected() {
        assertThatThrownBy(() -> controller.deactivate(UserRole.BACKER, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rewardService);
    }

    @Test
    void admin_isAllowedThrough() {
        controller.deactivate(UserRole.ADMIN, 1L);
        verify(rewardService).deactivate(1L);
    }
}
