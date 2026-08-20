package com.growmighty.lectures.firstday.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * TODO(조우진): 벡터DB 연동, 문서 검색·추천 로직, 실제 사용할 모델 제공자 확정
 */
@EnableFeignClients
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.ai",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
@EnableAsync
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
