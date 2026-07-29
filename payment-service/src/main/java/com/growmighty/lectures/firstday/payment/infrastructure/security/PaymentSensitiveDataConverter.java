package com.growmighty.lectures.firstday.payment.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class PaymentSensitiveDataConverter implements AttributeConverter<String, String> {

    private final PaymentSensitiveDataCrypto paymentSensitiveDataCrypto;


    @Override
    public String convertToDatabaseColumn(String attribute) {
        return paymentSensitiveDataCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return paymentSensitiveDataCrypto.decrypt(dbData);
    }

}
