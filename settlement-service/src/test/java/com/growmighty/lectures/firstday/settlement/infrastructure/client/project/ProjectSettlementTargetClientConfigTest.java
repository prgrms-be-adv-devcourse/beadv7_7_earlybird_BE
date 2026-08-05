package com.growmighty.lectures.firstday.settlement.infrastructure.client.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.project.ProjectSettlementTargetHttpReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.dummy.DummyProjectSettlementTargetReader;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;

class ProjectSettlementTargetClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ProjectSettlementTargetClientConfig.class,
                    DummyProjectSettlementTargetReader.class
            );

    @Test
    @DisplayName("기본 운영 모드는 Project HTTP 정산 대상 reader 하나만 등록한다")
    void registersHttpReaderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProjectOutcomeReader.class);
            assertThat(context.getBean(ProjectOutcomeReader.class))
                    .isInstanceOf(ProjectSettlementTargetHttpReader.class);
            assertThat(context).doesNotHaveBean(DummyProjectSettlementTargetReader.class);
        });
    }

    @Test
    @DisplayName("dummy 모드는 Project 더미 정산 대상 reader 하나만 등록한다")
    void registersDummyReaderOnlyWhenExplicitlySelected() {
        contextRunner
                .withPropertyValues("settlement.project-target.mode=dummy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProjectOutcomeReader.class);
                    assertThat(context.getBean(ProjectOutcomeReader.class))
                            .isInstanceOf(DummyProjectSettlementTargetReader.class);
                    assertThat(context).doesNotHaveBean(ProjectSettlementTargetHttpReader.class);
                    assertThat(context).doesNotHaveBean("projectSettlementTargetRestClient");
                });
    }

    @Test
    @DisplayName("운영 HTTP client는 서비스 디스커버리와 호출 제한시간을 구성한다")
    void configuresServiceDiscoveryAndTimeouts() {
        contextRunner
                .withPropertyValues(
                        "settlement.project-target.http.base-url=http://project-service",
                        "settlement.project-target.http.connect-timeout=250ms",
                        "settlement.project-target.http.read-timeout=750ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("projectSettlementTargetLoadBalancedRestClientBuilder");
                    assertThat(context).hasBean("projectSettlementTargetRestClient");
                    assertThat(context.getBean("projectSettlementTargetRestClient"))
                            .isInstanceOf(RestClient.class);
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "projectSettlementTargetLoadBalancedRestClientBuilder",
                            LoadBalanced.class
                    )).isNotNull();

                    ProjectSettlementTargetClientProperties properties = context.getBean(
                            ProjectSettlementTargetClientProperties.class
                    );
                    assertThat(properties.baseUrl().toString())
                            .isEqualTo("http://project-service");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(750));
                });
    }
}
