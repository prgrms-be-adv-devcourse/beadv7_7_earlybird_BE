package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;

class UserCreatorInformationClientConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(UserCreatorInformationClientConfig.class);

    @Test
    void configuresUserReaderWithTimeoutsAndResiliencePolicies() {
        contextRunner.withPropertyValues(
                        "settlement.creator-information.http.base-url=http://user-service",
                        "settlement.creator-information.http.connect-timeout=250ms",
                        "settlement.creator-information.http.read-timeout=750ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CreatorInformationReader.class);
                    assertThat(context.getBean(CreatorInformationReader.class)).isInstanceOf(UserCreatorInformationHttpReader.class);
                    assertThat(context.getBean("creatorInformationRestClient")).isInstanceOf(RestClient.class);
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "creatorInformationLoadBalancedRestClientBuilder", LoadBalanced.class)).isNotNull();
                    UserCreatorInformationClientProperties properties = context.getBean(UserCreatorInformationClientProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(750));
                });
    }
}
