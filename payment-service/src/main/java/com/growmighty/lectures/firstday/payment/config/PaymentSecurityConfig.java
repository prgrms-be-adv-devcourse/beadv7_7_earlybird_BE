package com.growmighty.lectures.firstday.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentSecurityProperties.class)
public class PaymentSecurityConfig {
}
