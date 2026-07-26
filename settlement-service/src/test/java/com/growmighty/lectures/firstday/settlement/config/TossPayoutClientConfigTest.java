package com.growmighty.lectures.firstday.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class TossPayoutClientConfigTest {

    private static final String SECURITY_KEY = "01".repeat(32);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(
                    SettlementTimeConfig.class,
                    TossPayoutSecurityConfig.class,
                    TossPayoutClientConfig.class
            );

    @Test
    @DisplayName("지급대행이 비활성화되면 외부 호출 어댑터를 등록하지 않는다")
    void keepsPayoutGatewayDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PayoutGateway.class);
            assertThat(context).doesNotHaveBean("tossPayoutRestClient");
        });
    }

    @Test
    @DisplayName("테스트 자격 증명이 설정되면 토스 지급대행 어댑터를 등록한다")
    void registersPayoutGateway() {
        contextRunner
                .withPropertyValues(
                        "settlement.toss-payout.enabled=true",
                        "settlement.toss-payout.secret-key=test_sk_example",
                        "settlement.toss-payout.security-key=" + SECURITY_KEY,
                        "settlement.toss-payout.base-url=http://localhost:18086"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PayoutGateway.class);
                    assertThat(context).getBean("tossPayoutRestClient")
                            .isInstanceOf(RestClient.class);
                });
    }
}
