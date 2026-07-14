package com.growmighty.lectures.firstday.settlement.read;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 정산 read-model 전용 금액 값 타입. 읽기만 하므로 getValue() 만 있으면 충분하다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {
    private BigDecimal value;
}
