package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.YearMonth;

@Converter(autoApply = true)
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth month) {
        return month == null ? null : month.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String month) {
        return month == null ? null : YearMonth.parse(month);
    }
}
