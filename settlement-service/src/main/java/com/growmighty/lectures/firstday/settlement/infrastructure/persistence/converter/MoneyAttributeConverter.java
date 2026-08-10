// TODO(settlement-plan): Reject fractional KRW explicitly and keep null handling consistent with domain invariants.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.converter;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter(autoApply = true)
public class MoneyAttributeConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal amount) {
        return amount == null ? null : Money.wons(amount);
    }
}
