package com.growmighty.lectures.firstday.common.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * spring-kafka가 없는 서비스(board/user/file/notification)에서도 common을 스캔하므로,
 * 그 서비스들에서는 이 설정 전체를 건너뛰도록 가드한다 — 없으면 KafkaTemplate 클래스를
 * 로드하려다 NoClassDefFoundError로 기동이 죽는다.
 */
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaErrorHandlingConfig {

    /**
     * 리스너에서 예외가 나면 1초 간격으로 3번 재시도하고, 그래도 실패하면
     * "<원본 토픽>.DLT"(같은 파티션)로 보낸다. - init-topic.sh가 만드는 실제 DLT 토픽.
     * kafkaTopics의 *.DLT 상수와 이름이 일치해야 한다. destination resolver를 안넘기면
     * Spring 기본값이 "-dlt" 접미사를 쓰기 때문에 명시적으로 지정한다.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
