package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Configuration(proxyBeanMethods = false)
public class SettlementKafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> settlementKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            ObjectProvider<DefaultErrorHandler> errorHandlerProvider
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        errorHandlerProvider.ifAvailable(factory::setCommonErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
