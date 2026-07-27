package com.growmighty.lectures.firstday.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.infrastructure.client.toss.TossPayoutJweCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TossPayoutSecurityConfigTest {

    private static final String SECURITY_KEY = "01".repeat(32);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SettlementTimeConfig.class, TossPayoutSecurityConfig.class);

    @Test
    @DisplayName("지급대행이 비활성화되면 자격 증명과 JWE 모듈을 요구하지 않는다")
    void keepsPayoutSecurityDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TossPayoutJweCodec.class);
        });
    }

    @Test
    @DisplayName("테스트 자격 증명이 유효하면 JWE 모듈을 등록한다")
    void registersJweCodecWithTestCredentials() {
        contextRunner
                .withPropertyValues(
                        "settlement.toss-payout.enabled=true",
                        "settlement.toss-payout.secret-key=test_sk_example",
                        "settlement.toss-payout.security-key=" + SECURITY_KEY
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TossPayoutJweCodec.class);
                });
    }

    @Test
    @DisplayName("라이브 키가 주입되면 애플리케이션 컨텍스트 시작을 거부한다")
    void rejectsLiveCredentialsAtStartup() {
        contextRunner
                .withPropertyValues(
                        "settlement.toss-payout.enabled=true",
                        "settlement.toss-payout.secret-key=live_sk_example",
                        "settlement.toss-payout.security-key=" + SECURITY_KEY
                )
                .run(context -> assertThat(context).hasFailed());
    }
}
