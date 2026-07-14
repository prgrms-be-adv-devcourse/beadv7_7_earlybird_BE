package com.growmighty.lectures.firstday.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients   // ← @FeignClient 인터페이스를 찾아 프록시(구현체)를 만들어라. JPA는 부트가 켜 주지만 Feign은 우리가 직접.
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.order",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
