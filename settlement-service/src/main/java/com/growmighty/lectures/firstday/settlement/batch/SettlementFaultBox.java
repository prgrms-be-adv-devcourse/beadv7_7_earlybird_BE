package com.growmighty.lectures.firstday.settlement.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * [Step3] "정산 장애 스위치" — 배치를 일부러 50% 에서 터뜨리기 위한 런타임 토글.
 *
 * <p>핵심 설계 포인트: 장애 조건을 <b>JobParameters 가 아니라 이 싱글톤 빈</b>에 둔다.
 * 그 이유는 <b>재시작(Restartability)</b> 때문이다.
 * <ul>
 *   <li>Spring Batch 의 네이티브 재시작은 <b>같은 JobParameters</b>로 다시 실행할 때 일어난다.
 *       (같은 파라미터 = 같은 JobInstance = "이어서 하기")</li>
 *   <li>만약 장애 조건을 파라미터로 넘기면, 재시작 때도 똑같은 파라미터라 장애가 또 발동한다.</li>
 *   <li>그래서 장애는 파라미터 밖(이 스위치)에 두고, 실패 1회 → 스위치를 끄고 → 같은 파라미터로
 *       재시작 → 남은 50% 완주, 라는 시나리오를 깔끔히 만든다.</li>
 * </ul>
 *
 * <p>{@link #failAfter} 는 "이만큼 정산을 만든 직후 터져라"는 절대 건수다 (0 이면 비활성).
 */
@Slf4j
@Component
public class SettlementFaultBox {

    /** 이 건수를 초과해 정산을 만들려는 순간 장애 발생. 0 = 비활성(정상 동작). */
    private volatile long failAfter = 0;

    /** 지정한 건수만큼 정산한 뒤 터지도록 장전한다. */
    public void arm(long failAfter) {
        this.failAfter = failAfter;
        log.warn("[FAULT] 장애 장전: {}건 정산 후 강제 실패", failAfter);
    }

    /** 장애를 해제한다. (재시작 직전에 호출 → 남은 작업이 정상 완주) */
    public void disarm() {
        if (failAfter > 0) {
            log.warn("[FAULT] 장애 해제 — 이제부터는 정상 처리");
        }
        this.failAfter = 0;
    }

    public boolean armed() {
        return failAfter > 0;
    }

    public long failAfter() {
        return failAfter;
    }
}
