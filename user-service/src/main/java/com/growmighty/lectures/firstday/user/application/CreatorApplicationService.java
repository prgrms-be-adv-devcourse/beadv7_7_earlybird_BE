package com.growmighty.lectures.firstday.user.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 창작자 전환 신청과 관리자 심사(#448). {@link UserService#registerAsCreator}가 신청 즉시 role을
 * 바꾸는 것과 달리, 여기서는 관리자가 승인해야만 role이 CREATOR로 바뀌고 creator_profiles가 생성된다.
 */
@Service
@RequiredArgsConstructor
public class CreatorApplicationService {
    private final CreatorApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;

    @Transactional
    public CreatorApplicationInfo apply(ApplyCreatorApplicationCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + command.userId()));
        if (user.getRole() == UserRole.CREATOR) {
            throw new IllegalStateException("이미 창작자로 등록되어 있습니다. userId=" + command.userId());
        }
        if (applicationRepository.existsByUserIdAndStatus(command.userId(), CreatorApplicationStatus.PENDING)) {
            throw new IllegalStateException("이미 심사 대기 중인 창작자 전환 신청이 있습니다. userId=" + command.userId());
        }
        CreatorApplication application = CreatorApplication.apply(
                command.userId(), command.creatorName(), command.category(), command.introduction(),
                command.businessNumber(), command.portfolioUrl(),
                command.bankCode(), command.accountNumber(), command.accountHolder());
        return CreatorApplicationInfo.from(applicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public List<CreatorApplicationInfo> findAll(CreatorApplicationStatus status) {
        List<CreatorApplication> applications = status == null
                ? applicationRepository.findAll()
                : applicationRepository.findAllByStatus(status);
        return applications.stream().map(CreatorApplicationInfo::from).toList();
    }

    /** 승인: user role을 CREATOR로 전환하고, 신청서의 정산 계좌 정보로 creator_profiles를 생성한다. */
    @Transactional
    public CreatorApplicationInfo approve(Long applicationId) {
        CreatorApplication application = getApplication(applicationId);
        application.approve();

        User user = userRepository.findById(application.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 유저입니다. userId=" + application.getUserId()));
        if (creatorProfileRepository.existsByUserId(application.getUserId())) {
            throw new IllegalStateException("이미 창작자로 등록되어 있습니다. userId=" + application.getUserId());
        }
        user.becomeCreator();
        creatorProfileRepository.save(CreatorProfile.register(application.getUserId(),
                application.getBankCode(), application.getAccountNumber(), application.getAccountHolder()));

        return CreatorApplicationInfo.from(application);
    }

    @Transactional
    public CreatorApplicationInfo reject(RejectCreatorApplicationCommand command) {
        CreatorApplication application = getApplication(command.applicationId());
        application.reject(command.reason());
        return CreatorApplicationInfo.from(application);
    }

    private CreatorApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "존재하지 않는 창작자 전환 신청입니다. applicationId=" + applicationId));
    }
}
