package com.growmighty.lectures.firstday.user;

import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.RegisterUserCommand;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.user.config.SeedUserProperties;
import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class UserDataInitializer implements CommandLineRunner {
    private final UserService userService;
    private final UserRepository userRepository;
    private final SeedUserProperties seedUserProperties;

    @Override
    public void run(String... args) {
        UserInfo buyer = registerIfAbsent(toCommand(seedUserProperties.buyer()));
        registerIfAbsent(toCommand(seedUserProperties.seller()));
        registerAdminIfAbsent(toCommand(seedUserProperties.admin()));

        System.out.printf("[seed] user-service 준비 완료. 구매자 id=%d%n", buyer.id());
    }

    private static RegisterUserCommand toCommand(SeedUserProperties.Account account) {
        return new RegisterUserCommand(account.email(), account.password(), account.name(), account.phone());
    }

    private UserInfo registerIfAbsent(RegisterUserCommand command) {
        return userRepository.findByEmail(command.email())
            .map(UserInfo::from)
            .orElseGet(() -> userService.register(command));
    }

    /** API로 승격 엔드포인트가 없어(§운영 절차 전용) 시드 데이터에서만 role을 직접 ADMIN으로 전환한다. */
    private UserInfo registerAdminIfAbsent(RegisterUserCommand command) {
        return userRepository.findByEmail(command.email())
            .map(UserInfo::from)
            .orElseGet(() -> {
                userService.register(command);
                User admin = userRepository.findByEmail(command.email()).orElseThrow();
                admin.grantAdminRole();
                return UserInfo.from(userRepository.save(admin));
            });
    }
}
