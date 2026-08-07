// TODO(settlement-plan): Keep only recovery-mode Feign wiring and remove normal-run HTTP mode assertions.
package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import com.growmighty.lectures.firstday.settlement.infrastructure.client.project.ProjectSettlementTargetClientConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.order.ProjectOrderHttpReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.dummy.DummyProjectOrderReader;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;

class ProjectOrderClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ProjectOrderClientConfig.class,
                    DummyProjectOrderReader.class
            );

    @Test
    @DisplayName("기본 운영 모드는 Order HTTP reader 하나만 등록한다")
    void registersHttpReaderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProjectOrderReader.class);
            assertThat(context.getBean(ProjectOrderReader.class))
                    .isInstanceOf(ProjectOrderHttpReader.class);
            assertThat(context).doesNotHaveBean(DummyProjectOrderReader.class);
        });
    }

    @Test
    @DisplayName("dummy 모드는 Order 더미 reader 하나만 등록한다")
    void registersDummyReaderOnlyWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues("settlement.project-order.mode=dummy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProjectOrderReader.class);
                    assertThat(context.getBean(ProjectOrderReader.class))
                            .isInstanceOf(DummyProjectOrderReader.class);
                    assertThat(context).doesNotHaveBean(ProjectOrderHttpReader.class);
                    assertThat(context).doesNotHaveBean("projectOrderRestClient");
                });
    }

    @Test
    @DisplayName("운영 Order HTTP client는 서비스 디스커버리와 호출 제한시간을 구성한다")
    void configuresServiceDiscoveryAndTimeouts() {
        contextRunner
                .withPropertyValues(
                        "settlement.project-order.http.base-url=http://order-service",
                        "settlement.project-order.http.connect-timeout=250ms",
                        "settlement.project-order.http.read-timeout=750ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("projectOrderLoadBalancedRestClientBuilder");
                    assertThat(context).hasBean("projectOrderRestClient");
                    assertThat(context.getBean("projectOrderRestClient"))
                            .isInstanceOf(RestClient.class);
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "projectOrderLoadBalancedRestClientBuilder",
                            LoadBalanced.class
                    )).isNotNull();

                    ProjectOrderClientProperties properties = context.getBean(
                            ProjectOrderClientProperties.class
                    );
                    assertThat(properties.baseUrl().toString())
                            .isEqualTo("http://order-service");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(750));
                });
    }

    @Test
    @DisplayName("Project와 Order HTTP reader를 함께 등록해도 각 전용 client를 사용한다")
    void registersProjectAndOrderHttpReadersTogether() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        ProjectSettlementTargetClientConfig.class,
                        ProjectOrderClientConfig.class
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProjectOutcomeReader.class);
                    assertThat(context).hasSingleBean(ProjectOrderReader.class);
                    assertThat(context).hasBean("projectSettlementTargetRestClient");
                    assertThat(context).hasBean("projectOrderRestClient");
                });
    }
}
