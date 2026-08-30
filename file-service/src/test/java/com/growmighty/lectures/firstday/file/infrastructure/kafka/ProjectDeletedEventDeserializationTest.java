package com.growmighty.lectures.firstday.file.infrastructure.kafka;

import com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * project-service가 {@code project.deleted.v1}에 실어 보내는 메시지를 file-service가 실제로
 * 역직렬화할 수 있는지 고정한다 (#764 "선행 확인 필요").
 *
 * <p>두 서비스는 같은 모양의 record를 각자 자기 패키지에 두고 있어 FQCN이 다르다. producer는
 * {@code __TypeId__} 헤더에 타입 식별자를 싣고 {@code JsonDeserializer}는 그 헤더를
 * {@code value.default.type}보다 <b>우선</b>하므로, 헤더에 무엇이 실려 오느냐가 전부다.
 *
 * <ul>
 *   <li>별칭({@code projectDeleted})이 오면 → consumer의 {@code type.mapping}이 자기 클래스로 해석</li>
 *   <li>project-service FQCN이 오면 → 클래스패스에 없어 전량 실패(고치기 전 상태)</li>
 * </ul>
 *
 * 그래서 별칭 문자열은 producer({@code project-service.yml})와 consumer
 * ({@link ProjectDeletedKafkaListener})가 정확히 맞춰야 하는 서비스 간 계약이다.
 */
class ProjectDeletedEventDeserializationTest {

    private static final String TOPIC = "project.deleted.v1";
    private static final String TYPE_HEADER = "__TypeId__";
    /** producer/consumer가 맞춰야 하는 별칭. 한 글자만 어긋나도 조용히 DLT로 간다. */
    private static final String ALIAS = "projectDeleted";
    /** 별칭이 없을 때 실려 오던 값 — file-service 클래스패스에 없는 클래스다. */
    private static final String PRODUCER_FQCN =
        "com.growmighty.lectures.firstday.project.project.infrastructure.kafka.dto.ProjectDeletedEvent";
    private static final String CONSUMER_FQCN =
        "com.growmighty.lectures.firstday.file.infrastructure.kafka.dto.ProjectDeletedEvent";

    @Test
    @DisplayName("producer가 projectDeleted 별칭을 실어 보내면 file-service가 정상 역직렬화한다")
    void aliasFromProducerIsResolvedByConsumer() {
        Headers headers = new RecordHeaders();
        byte[] payload = serializeWithTypeHeader(headers, ALIAS);

        try (JsonDeserializer<Object> deserializer = listenerDeserializer()) {
            Object result = deserializer.deserialize(TOPIC, headers, payload);

            assertThat(result).isInstanceOf(ProjectDeletedEvent.class);
            assertThat(((ProjectDeletedEvent) result).payload().projectId()).isEqualTo(100L);
        }
    }

    @Test
    @DisplayName("별칭 없이 producer FQCN이 실려 오면 클래스를 못 찾아 실패한다 — 별칭이 필요한 이유")
    void rawProducerFqcnStillFails() {
        Headers headers = new RecordHeaders();
        byte[] payload = serializeWithTypeHeader(headers, PRODUCER_FQCN);

        try (JsonDeserializer<Object> deserializer = listenerDeserializer()) {
            assertThatThrownBy(() -> deserializer.deserialize(TOPIC, headers, payload))
                .hasMessageContaining("failed to resolve class name")
                .hasMessageContaining(PRODUCER_FQCN);
        }
    }

    @Test
    @DisplayName("타입 헤더가 아예 없으면 value.default.type 폴백으로 역직렬화된다")
    void fallsBackToDefaultTypeWithoutHeader() {
        Headers headers = new RecordHeaders();
        byte[] payload = serializeWithTypeHeader(headers, null);

        try (JsonDeserializer<Object> deserializer = listenerDeserializer()) {
            assertThat(deserializer.deserialize(TOPIC, headers, payload))
                .isInstanceOf(ProjectDeletedEvent.class);
        }
    }

    /** 공용 application.yml의 producer(맨 JsonSerializer)로 직렬화하고, 타입 헤더를 지정한 값으로 맞춘다. */
    private byte[] serializeWithTypeHeader(Headers headers, String typeHeaderValue) {
        byte[] payload;
        try (JsonSerializer<Object> serializer = new JsonSerializer<>()) {
            serializer.configure(new HashMap<>(), false);
            payload = serializer.serialize(TOPIC, headers, ProjectDeletedEvent.of(100L));
        }
        headers.remove(TYPE_HEADER);
        if (typeHeaderValue != null) {
            headers.add(TYPE_HEADER, typeHeaderValue.getBytes(StandardCharsets.UTF_8));
        }
        return payload;
    }

    /** {@link ProjectDeletedKafkaListener}의 properties + 공용 consumer 설정 그대로. */
    private JsonDeserializer<Object> listenerDeserializer() {
        Map<String, Object> props = new HashMap<>();
        props.put(JsonDeserializer.TYPE_MAPPINGS, ALIAS + ":" + CONSUMER_FQCN);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CONSUMER_FQCN);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.configure(props, false);
        return deserializer;
    }
}
