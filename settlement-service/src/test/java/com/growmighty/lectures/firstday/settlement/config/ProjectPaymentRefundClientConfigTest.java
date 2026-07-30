package com.growmighty.lectures.firstday.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.payment.ProjectPaymentRefundHttpGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.dummy.DummyProjectPaymentCancellationGateway;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;

class ProjectPaymentRefundClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ProjectPaymentRefundClientConfig.class,
                    DummyProjectPaymentCancellationGateway.class
            );

    @Test
    @DisplayName("기본 운영 모드는 Payment HTTP 환불 gateway 하나만 등록한다")
    void registersHttpGatewayByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProjectPaymentCancellationGateway.class);
            assertThat(context.getBean(ProjectPaymentCancellationGateway.class))
                    .isInstanceOf(ProjectPaymentRefundHttpGateway.class);
            assertThat(context).doesNotHaveBean(DummyProjectPaymentCancellationGateway.class);
        });
    }

    @Test
    @DisplayName("dummy 모드는 Payment 더미 환불 gateway 하나만 등록한다")
    void registersDummyGatewayOnlyWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues("settlement.payment-cancellation.mode=dummy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasSingleBean(ProjectPaymentCancellationGateway.class);
                    assertThat(context.getBean(ProjectPaymentCancellationGateway.class))
                            .isInstanceOf(DummyProjectPaymentCancellationGateway.class);
                    assertThat(context).doesNotHaveBean(ProjectPaymentRefundHttpGateway.class);
                    assertThat(context).doesNotHaveBean("projectPaymentRefundRestClient");
                });
    }

    @Test
    @DisplayName("운영 Payment HTTP client는 서비스 디스커버리와 호출 제한시간을 구성한다")
    void configuresServiceDiscoveryAndTimeouts() {
        contextRunner
                .withPropertyValues(
                        "settlement.payment-cancellation.http.base-url=http://payment-service",
                        "settlement.payment-cancellation.http.connect-timeout=250ms",
                        "settlement.payment-cancellation.http.read-timeout=750ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context)
                            .hasBean("projectPaymentRefundLoadBalancedRestClientBuilder");
                    assertThat(context).hasBean("projectPaymentRefundRestClient");
                    assertThat(context.getBean("projectPaymentRefundRestClient"))
                            .isInstanceOf(RestClient.class);
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "projectPaymentRefundLoadBalancedRestClientBuilder",
                            LoadBalanced.class
                    )).isNotNull();

                    ProjectPaymentRefundClientProperties properties = context.getBean(
                            ProjectPaymentRefundClientProperties.class
                    );
                    assertThat(properties.baseUrl().toString())
                            .isEqualTo("http://payment-service");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(750));
                });
    }

    @Test
    @DisplayName("Project·Order·Payment HTTP adapter가 각 전용 client로 함께 등록된다")
    void registersAllExternalHttpAdaptersTogether() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ProjectSettlementTargetClientConfig.class,
                        ProjectOrderClientConfig.class,
                        ProjectPaymentRefundClientConfig.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProjectOutcomeReader.class);
                    assertThat(context).hasSingleBean(ProjectOrderReader.class);
                    assertThat(context)
                            .hasSingleBean(ProjectPaymentCancellationGateway.class);
                    assertThat(context).hasBean("projectSettlementTargetRestClient");
                    assertThat(context).hasBean("projectOrderRestClient");
                    assertThat(context).hasBean("projectPaymentRefundRestClient");
                });
    }
}
