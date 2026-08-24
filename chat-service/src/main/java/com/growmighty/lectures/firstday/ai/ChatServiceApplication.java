package com.growmighty.lectures.firstday.ai;

import io.micrometer.context.ContextRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.context.request.RequestAttributesThreadLocalAccessor;
import reactor.core.publisher.Hooks;

@EnableFeignClients
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.ai",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
@EnableAsync
public class ChatServiceApplication {
    public static void main(String[] args) {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new RequestAttributesThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
