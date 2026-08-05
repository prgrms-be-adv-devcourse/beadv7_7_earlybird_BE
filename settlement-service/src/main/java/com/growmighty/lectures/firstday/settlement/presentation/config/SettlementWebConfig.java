package com.growmighty.lectures.firstday.settlement.presentation.config;

import com.growmighty.lectures.firstday.common.exception.GlobalExceptionHandler;
import com.growmighty.lectures.firstday.common.response.ApiResponseWrappingAdvice;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import({
        GlobalExceptionHandler.class,
        ApiResponseWrappingAdvice.class
})
public class SettlementWebConfig {
}
