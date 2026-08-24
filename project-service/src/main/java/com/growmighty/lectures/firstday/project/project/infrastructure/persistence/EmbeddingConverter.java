package com.growmighty.lectures.firstday.project.project.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * 사전 계산된 벡터 임베딩을 위한 JPA AttributeConverter (float[] <-> DB 내 JSON 문자열).
 */
@Slf4j
@Converter(autoApply = true)
public class EmbeddingConverter implements AttributeConverter<float[], String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null || attribute.length == 0) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("float[] 임베딩을 JSON 문자열로 변환하는 데 실패했습니다.", e);
            return null;
        }
    }

    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, float[].class);
        } catch (JsonProcessingException e) {
            log.error("JSON 문자열을 float[] 임베딩으로 변환하는 데 실패했습니다.", e);
            return null;
        }
    }
}
