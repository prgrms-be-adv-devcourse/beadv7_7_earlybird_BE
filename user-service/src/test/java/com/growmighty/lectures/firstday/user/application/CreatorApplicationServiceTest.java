package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.user.application.dto.ApplyCreatorApplicationCommand;
import com.growmighty.lectures.firstday.user.application.dto.CreatorApplicationInfo;
import com.growmighty.lectures.firstday.user.application.dto.RejectCreatorApplicationCommand;
import com.growmighty.lectures.firstday.user.domain.CreatorApplication;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationRepository;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;
import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import com.growmighty.lectures.firstday.user.domain.CreatorProfileRepository;
import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorApplicationServiceTest {

    @Mock
    private CreatorApplicationRepository applicationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorProfileRepository creatorProfileRepository;

    private CreatorApplicationService creatorApplicationService;

    @BeforeEach
    void setUp() {
        creatorApplicationService = new CreatorApplicationService(
                applicationRepository, userRepository, creatorProfileRepository);
    }

    private static User backer() {
        return User.register("hana@example.com", "encoded", "김하나한", "010-0000-0000");
    }

    private static ApplyCreatorApplicationCommand applyCommand(Long userId) {
        return new ApplyCreatorApplicationCommand(userId, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");
    }

    @Test
    @DisplayName("존재하지 않는 유저가 신청하면 예외가 발생한다")
    void apply_withUnknownUser_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorApplicationService.apply(applyCommand(999L)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 창작자인 유저가 신청하면 예외가 발생한다 (creator_profiles 유무와 무관하게 role로 판단)")
    void apply_whenAlreadyCreator_throws() {
        User creator = backer();
        creator.becomeCreator();
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> creatorApplicationService.apply(applyCommand(1L)))
                .isInstanceOf(IllegalStateException.class);

        verify(creatorProfileRepository, never()).existsByUserId(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 심사 대기 중인 신청이 있으면 다시 신청할 수 없다")
    void apply_withExistingPendingApplication_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(backer()));
        when(applicationRepository.existsByUserIdAndStatus(1L, CreatorApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> creatorApplicationService.apply(applyCommand(1L)))
                .isInstanceOf(IllegalStateException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 신청하면 PENDING 상태로 저장된다")
    void apply_withValidCommand_savesPendingApplication() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(backer()));
        when(applicationRepository.existsByUserIdAndStatus(1L, CreatorApplicationStatus.PENDING)).thenReturn(false);
        ArgumentCaptor<CreatorApplication> captor = ArgumentCaptor.forClass(CreatorApplication.class);
        when(applicationRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        CreatorApplicationInfo result = creatorApplicationService.apply(applyCommand(1L));

        assertThat(result.status()).isEqualTo(CreatorApplicationStatus.PENDING);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 신청을 승인하면 예외가 발생한다")
    void approve_withUnknownApplication_throws() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorApplicationService.approve(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("승인하면 유저 role이 CREATOR로 바뀌고 정산 계좌 정보가 저장된다")
    void approve_switchesRoleAndSavesProfile() {
        CreatorApplication application = CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");
        User user = backer();
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(application));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.existsByUserId(1L)).thenReturn(false);
        ArgumentCaptor<CreatorProfile> captor = ArgumentCaptor.forClass(CreatorProfile.class);

        CreatorApplicationInfo result = creatorApplicationService.approve(10L);

        assertThat(result.status()).isEqualTo(CreatorApplicationStatus.APPROVED);
        assertThat(user.getRole()).isEqualTo(UserRole.CREATOR);
        verify(creatorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getBankName()).isEqualTo("신한은행");
        assertThat(captor.getValue().getBankCode()).isEqualTo("88");
    }

    @Test
    @DisplayName("이미 처리된 신청을 승인하면 예외가 발생하고 role은 바뀌지 않는다")
    void approve_whenAlreadyProcessed_throws() {
        CreatorApplication application = CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");
        application.reject("사유");
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> creatorApplicationService.approve(10L))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).findById(any());
        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 신청을 반려하면 예외가 발생한다")
    void reject_withUnknownApplication_throws() {
        when(applicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creatorApplicationService.reject(new RejectCreatorApplicationCommand(999L, "사유")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("반려하면 REJECTED 상태와 사유가 저장된다")
    void reject_withValidCommand_savesRejectedApplication() {
        CreatorApplication application = CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");
        when(applicationRepository.findById(10L)).thenReturn(Optional.of(application));

        CreatorApplicationInfo result = creatorApplicationService.reject(
                new RejectCreatorApplicationCommand(10L, "서류 미비"));

        assertThat(result.status()).isEqualTo(CreatorApplicationStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo("서류 미비");
    }

    @Test
    @DisplayName("status 필터 없이 조회하면 전체 목록을 반환한다")
    void findAll_withoutStatus_returnsAllApplications() {
        CreatorApplication application = CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");
        when(applicationRepository.findAll()).thenReturn(List.of(application));

        List<CreatorApplicationInfo> result = creatorApplicationService.findAll(null);

        assertThat(result).hasSize(1);
        verify(applicationRepository, never()).findAllByStatus(any());
    }

    @Test
    @DisplayName("status 필터로 조회하면 해당 상태만 반환한다")
    void findAll_withStatus_returnsFilteredApplications() {
        when(applicationRepository.findAllByStatus(CreatorApplicationStatus.PENDING)).thenReturn(List.of());

        creatorApplicationService.findAll(CreatorApplicationStatus.PENDING);

        verify(applicationRepository).findAllByStatus(CreatorApplicationStatus.PENDING);
        verify(applicationRepository, never()).findAll();
    }
}
