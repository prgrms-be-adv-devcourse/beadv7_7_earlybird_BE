package com.growmighty.lectures.firstday.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SeedUserProperties.class)
public class SeedUserConfig {
}
