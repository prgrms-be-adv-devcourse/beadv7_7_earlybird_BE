package com.growmighty.lectures.firstday.user.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// autoApply=false: bankName/accountHolder 등 다른 String 필드까지 암호화되지 않도록,
// CreatorProfile.accountNumber 에만 @Convert 로 명시 적용한다.
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class AccountNumberConverter implements AttributeConverter<String, String> {

    private final UserSensitiveDataCrypto userSensitiveDataCrypto;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return userSensitiveDataCrypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return userSensitiveDataCrypto.decrypt(dbData);
    }
}
