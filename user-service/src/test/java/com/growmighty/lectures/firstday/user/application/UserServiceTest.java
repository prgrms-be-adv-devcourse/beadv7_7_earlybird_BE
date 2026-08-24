package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.user.application.dto.ChangeRoleCommand;
import com.growmighty.lectures.firstday.user.application.dto.CreatorProfileInfo;
import com.growmighty.lectures.firstday.user.application.dto.LoginCommand;
import com.growmighty.lectures.firstday.user.application.dto.RegisterCreatorCommand;
import com.growmighty.lectures.firstday.user.application.dto.RegisterUserCommand;
import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorProfileRepository creatorProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, creatorProfileRepository, passwordEncoder);
    }

    private static User backer() {
        return User.register("hana@example.com", "encoded-old", "김하나한", "010-0000-0000");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 가입하면 예외가 발생한다")
    void register_withDuplicateEmail_throws() {
        when(userRepository.existsByEmail("hana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(
                new RegisterUserCommand("hana@example.com", "rawPassword1!", "김하나한", "010-0000-0000")))
                .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 시 비밀번호를 인코딩해서 저장한다")
    void register_encodesPasswordBeforeSaving() {
        when(userRepository.existsByEmail("hana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword1!")).thenReturn("encoded-password");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        UserInfo result = userService.register(
                new RegisterUserCommand("hana@example.com", "rawPassword1!", "김하나한", "010-0000-0000"));

        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(result.email()).isEqualTo("hana@example.com");
        assertThat(result.role()).isEqualTo(UserRole.BACKER);
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 예외가 발생한다")
    void authenticate_withUnknownEmail_throws() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.authenticate(new LoginCommand("nobody@example.com", "rawPassword1!")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 예외가 발생한다")
    void authenticate_withWrongPassword_throws() {
        User user = backer();
        when(userRepository.findByEmail("hana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword!", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.authenticate(new LoginCommand("hana@example.com", "wrongPassword!")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 사용자 정보를 반환한다")
    void authenticate_withCorrectCredentials_returnsUserInfo() {
        User user = backer();
        when(userRepository.findByEmail("hana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawPassword1!", "encoded-old")).thenReturn(true);

        UserInfo result = userService.authenticate(new LoginCommand("hana@example.com", "rawPassword1!"));

        assertThat(result.email()).isEqualTo("hana@example.com");
    }

    @Test
    @DisplayName("존재하지 않는 유저를 조회하면 예외가 발생한다")
    void getUser_withUnknownId_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(999L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 유저의 프로필을 수정하면 예외가 발생한다")
    void updateProfile_withUnknownId_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileCommand(999L, "새이름", "010-1111-1111", null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("비밀번호 필드 없이 프로필을 수정하면 이름과 전화번호만 갱신되고 비밀번호는 그대로 유지된다")
    void updateProfile_withoutPasswordFields_updatesNameAndPhoneNumberOnly() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo result = userService.updateProfile(
                new UpdateProfileCommand(1L, "새이름", "010-1111-1111", null, null));

        assertThat(result.name()).isEqualTo("새이름");
        assertThat(result.phoneNumber()).isEqualTo("010-1111-1111");
        assertThat(user.getPassword()).isEqualTo("encoded-old");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 프로필 수정 시 예외가 발생한다")
    void updateProfile_withWrongCurrentPassword_throws() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword!", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateProfile(
                new UpdateProfileCommand(1L, "새이름", "010-1111-1111", "wrongPassword!", "newPassword1!")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 프로필과 비밀번호가 함께 갱신된다")
    void updateProfile_withValidPasswordFields_updatesProfileAndPassword() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawPassword1!", "encoded-old")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1!")).thenReturn("encoded-new");

        UserInfo result = userService.updateProfile(
                new UpdateProfileCommand(1L, "새이름", "010-1111-1111", "rawPassword1!", "newPassword1!"));

        assertThat(result.name()).isEqualTo("새이름");
        assertThat(result.phoneNumber()).isEqualTo("010-1111-1111");
        assertThat(user.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    @DisplayName("존재하지 않는 유저를 판매자로 등록하면 예외가 발생한다")
    void registerAsCreator_withUnknownId_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerAsCreator(
                new RegisterCreatorCommand(999L, "88", "110-123-456789", "창작자")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("이미 판매자로 등록된 유저를 다시 등록하면 예외가 발생한다")
    void registerAsCreator_whenAlreadyRegistered_throws() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.registerAsCreator(
                new RegisterCreatorCommand(1L, "88", "110-123-456789", "창작자")))
                .isInstanceOf(IllegalStateException.class);

        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("지원하지 않는 은행 코드로 판매자 등록을 요청하면 예외가 발생한다")
    void registerAsCreator_withUnknownBankCode_throws() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.existsByUserId(1L)).thenReturn(false);

        assertThatThrownBy(() -> userService.registerAsCreator(
                new RegisterCreatorCommand(1L, "99", "110-123-456789", "창작자")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(creatorProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("판매자로 등록하면 role 이 CREATOR 로 바뀌고 정산 계좌 정보가 저장된다")
    void registerAsCreator_switchesRoleAndSavesProfile() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.existsByUserId(1L)).thenReturn(false);
        ArgumentCaptor<CreatorProfile> captor = ArgumentCaptor.forClass(CreatorProfile.class);

        UserInfo result = userService.registerAsCreator(
                new RegisterCreatorCommand(1L, "88", "110-123-456789", "창작자"));

        assertThat(result.role()).isEqualTo(UserRole.CREATOR);
        verify(creatorProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getBankName()).isEqualTo("신한은행");
        assertThat(captor.getValue().getBankCode()).isEqualTo("88");
    }

    @Test
    @DisplayName("존재하지 않는 유저의 창작자 정보를 조회하면 예외가 발생한다")
    void getCreatorProfile_withUnknownUserId_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCreatorProfile(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 유저의 role을 바꾸면 예외가 발생한다")
    void changeRole_withUnknownId_throws() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changeRole(new ChangeRoleCommand(999L, UserRole.ADMIN)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("판매자로 등록되지 않은 유저의 창작자 정보를 조회하면 예외가 발생한다")
    void getCreatorProfile_withoutCreatorProfile_throws() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCreatorProfile(1L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("창작자 정보를 조회하면 이름과 정산 계좌 정보를 반환한다")
    void getCreatorProfile_returnsNameAndBankInfo() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(creatorProfileRepository.findByUserId(1L)).thenReturn(
                Optional.of(CreatorProfile.register(1L, "88", "110-123-456789", "창작자")));

        CreatorProfileInfo result = userService.getCreatorProfile(1L);

        assertThat(result.name()).isEqualTo("김하나한");
        assertThat(result.bankName()).isEqualTo("신한은행");
        assertThat(result.bankCode()).isEqualTo("88");
        assertThat(result.accountHolder()).isEqualTo("창작자");
    }

    @Test
    @DisplayName("role을 바꾸면 정산 계좌 등록 없이 role만 갱신된다")
    void changeRole_updatesRoleOnly() {
        User user = backer();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserInfo result = userService.changeRole(new ChangeRoleCommand(1L, UserRole.ADMIN));

        assertThat(result.role()).isEqualTo(UserRole.ADMIN);
        verify(creatorProfileRepository, never()).save(any());
    }
}
