package com.growmighty.lectures.firstday.settlement.batch;

/**
 * [Step3] 의도적으로 주입한 "정산 장애".
 *
 * <p>배치가 50% 지점에서 죽는 시나리오를 재현하기 위해 {@link OrderToSettlementProcessor}
 * 가 던지는 예외다. 진짜 버그가 아니라 <b>실습용 폭탄</b>이라는 걸 타입 이름으로 드러낸다.
 * 이 예외가 Step 을 실패(FAILED)시키고, 그 직전까지 커밋된 chunk 들은 그대로 남는다.
 */
public class SettlementFaultException extends RuntimeException {
    public SettlementFaultException(String message) {
        super(message);
    }
}
