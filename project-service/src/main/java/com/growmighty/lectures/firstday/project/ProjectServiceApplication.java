package com.growmighty.lectures.firstday.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
		"com.growmighty.lectures.firstday.project",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
public class ProjectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}
