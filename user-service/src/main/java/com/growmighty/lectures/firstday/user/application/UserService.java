package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.user.application.dto.ChangeRoleCommand;
import com.growmighty.lectures.firstday.user.application.dto.CreatorProfileInfo;
import com.growmighty.lectures.firstday.user.application.dto.LoginCommand;
import com.growmighty.lectures.firstday.user.application.dto.RegisterCreatorCommand;
import com.growmighty.lectures.firstday.user.application.dto.RegisterUserCommand;
import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import com.growmighty.lectures.firstday.user.domain.CreatorProfileRepository;
import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserInfo register(RegisterUserCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalStateException("이미 가입된 이메일입니다. email=" + command.email());
        }
        String encoded = passwordEncoder.encode(command.rawPassword());
        User user = User.register(command.email(), encoded, command.name(), command.phoneNumber());
        return UserInfo.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserInfo authenticate(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(command.rawPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return UserInfo.from(user);
    }

    @Transactional(readOnly = true)
    public UserInfo getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + userId));
        return UserInfo.from(user);
    }

    @Transactional
    public UserInfo updateProfile(UpdateProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + command.userId()));
        user.updateProfile(command.name(), command.phoneNumber());
        if (command.newPassword() != null) {
            if (!passwordEncoder.matches(command.currentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
            }
            user.changePassword(passwordEncoder.encode(command.newPassword()));
        }
        return UserInfo.from(user);
    }

    /** 판매자(창작자) 등록: role 전환 + 정산 계좌 정보(creator_profiles) 생성을 한 트랜잭션으로 처리한다. */
    @Transactional
    public UserInfo registerAsCreator(RegisterCreatorCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + command.userId()));
        if (creatorProfileRepository.existsByUserId(command.userId())) {
            throw new IllegalStateException("이미 판매자로 등록되어 있습니다. userId=" + command.userId());
        }
        user.becomeCreator();
        creatorProfileRepository.save(CreatorProfile.register(
                command.userId(), command.bankCode(), command.accountNumber(), command.accountHolder()));
        return UserInfo.from(user);
    }

    /** 관리자용 창작자 단건 조회 — 정산 내역 조회 화면에 표기할 창작자 신원/정산 계좌 정보. */
    @Transactional(readOnly = true)
    public CreatorProfileInfo getCreatorProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + userId));
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("판매자로 등록되지 않은 유저입니다. userId=" + userId));
        return CreatorProfileInfo.of(user, profile);
    }

    /** 발표/시연 편의용 role 전환(#430). 정식 창작자 등록(정산 계좌 등)과 무관하게 role만 바꾼다. */
    @Transactional
    public UserInfo changeRole(ChangeRoleCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + command.userId()));
        user.switchRoleForDemo(command.role());
        return UserInfo.from(user);
    }
}
