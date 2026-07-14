package com.growmighty.lectures.firstday.user;

import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.RegisterUserCommand;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class UserDataInitializer implements CommandLineRunner {
    private final UserService userService;

    @Override
    public void run(String... args) {
        UserInfo buyer = userService.register(
            new RegisterUserCommand("buyer@growmighty.co.kr", "rawPassword1!", "구매자", "010-1111-1111"));
        userService.register(
            new RegisterUserCommand("seller@growmighty.co.kr", "rawPassword2!", "판매자", "010-2222-2222"));

        System.out.printf("[seed] user-service 준비 완료. 구매자 id=%d%n", buyer.id());
    }
}
