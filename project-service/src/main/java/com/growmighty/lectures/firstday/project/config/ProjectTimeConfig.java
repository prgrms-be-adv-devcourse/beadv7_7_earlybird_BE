package com.growmighty.lectures.firstday.project.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectTimeConfig {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock projectClock() {
        return Clock.system(SEOUL);
    }
}
