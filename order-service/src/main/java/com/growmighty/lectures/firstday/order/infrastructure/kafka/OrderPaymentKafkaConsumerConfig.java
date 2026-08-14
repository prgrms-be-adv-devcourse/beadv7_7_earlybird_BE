package com.growmighty.lectures.firstday.order.infrastructure.kafka;

import com.growmighty.lectures.firstday.order.infrastructure.kafka.dto.PaymentSingleResultEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
public class OrderPaymentKafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSingleResultEvent>
            orderPaymentKafkaListenerContainerFactory(KafkaProperties kafkaProperties,
                                                       @Qualifier("errorHandler") DefaultErrorHandler errorHandler) {
        JacksonJsonDeserializer<PaymentSingleResultEvent> eventDeserializer =
                new JacksonJsonDeserializer<>(PaymentSingleResultEvent.class, false);
        var consumerFactory = new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(eventDeserializer));

        var factory = new ConcurrentKafkaListenerContainerFactory<String, PaymentSingleResultEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
