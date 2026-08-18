package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = true)
@RequiredArgsConstructor
public class SensitiveValueConverter implements AttributeConverter<SensitiveValue, String> {

    private final PaymentSensitiveDataCrypto paymentSensitiveDataCrypto;

    // 추가 : JPA 저장 시 민감 값을 암호화한다.
    @Override
    public String convertToDatabaseColumn(SensitiveValue attribute) {
        return attribute == null ? null : paymentSensitiveDataCrypto.encrypt(attribute.value());
    }

    // 추가 : JPA 조회 시 암호문을 민감 값으로 복호화한다.
    @Override
    public SensitiveValue convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new SensitiveValue(paymentSensitiveDataCrypto.decrypt(dbData));
    }
}
