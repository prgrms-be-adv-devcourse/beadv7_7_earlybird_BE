package com.growmighty.lectures.firstday.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.payment",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
