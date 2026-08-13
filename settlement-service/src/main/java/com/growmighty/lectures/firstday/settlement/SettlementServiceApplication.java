// TODO(settlement-plan): Keep bootstrap thin; wire Kafka consumers and the monthly scheduler through dedicated configuration.
package com.growmighty.lectures.firstday.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.growmighty.lectures.firstday.settlement",
        "com.growmighty.lectures.firstday.common"
})
@EnableScheduling
public class SettlementServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
