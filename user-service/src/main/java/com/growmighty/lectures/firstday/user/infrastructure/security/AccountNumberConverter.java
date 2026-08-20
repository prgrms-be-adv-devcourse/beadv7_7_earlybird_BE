package com.growmighty.lectures.firstday.user.infrastructure.security;

import com.growmighty.lectures.firstday.user.domain.vo.AccountNumber;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// autoApply=true: AccountNumber 타입 필드에만 적용되므로 bankName/accountHolder 등
// String 필드는 영향받지 않는다. CreatorProfile은 이 컨버터를 import하지 않는다.
@Component
@Converter(autoApply = true)
@RequiredArgsConstructor
public class AccountNumberConverter implements AttributeConverter<AccountNumber, String> {

    private final UserSensitiveDataCrypto userSensitiveDataCrypto;

    @Override
    public String convertToDatabaseColumn(AccountNumber attribute) {
        return userSensitiveDataCrypto.encrypt(attribute.value());
    }

    @Override
    public AccountNumber convertToEntityAttribute(String dbData) {
        return new AccountNumber(userSensitiveDataCrypto.decrypt(dbData));
    }
}
