package com.growmighty.lectures.firstday.project.project.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA AttributeConverter for pre-computed vector embedding (float[] <-> JSON String in DB).
 */
@Slf4j
@Converter
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
            log.error("Failed to convert float[] embedding to JSON string", e);
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
            log.error("Failed to convert JSON string to float[] embedding", e);
            return null;
        }
    }
}
