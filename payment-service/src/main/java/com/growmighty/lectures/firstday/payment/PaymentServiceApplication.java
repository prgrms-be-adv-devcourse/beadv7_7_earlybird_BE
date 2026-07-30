package com.growmighty.lectures.firstday.payment;

import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties(PaymentRecoveryProperties.class)
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.payment",
    "com.growmighty.lectures.firstday.refund",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
