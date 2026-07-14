package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PayBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentApiData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentHttpClient implements PaymentPort {

    private final PaymentFeignClient paymentFeignClient;         // RestClient → Feign, 갈아 끼운 건 이 줄뿐
    private final CircuitBreakerFactory circuitBreakerFactory;   // 문지기는 그대로

    @Override
    public PaymentResult pay(BigDecimal amount) {
        // Step 5 와 완전히 동일 — 차단기는 "호출을 감싸는" 물건이라,
        // 안에서 RestClient 가 뛰든 Feign 이 뛰든 관심이 없다. 추상화의 힘.
        return circuitBreakerFactory.create("payment").run(
            () -> callPay(amount),   // ① 본 호출 (평소엔 이게 실행된다)
            this::payFallback);      // ② 실패했거나, 차단기가 OPEN 이라 거부됐을 때 (plan B)
    }

    // 몸통이 6줄 → 2줄. 하는 일은 완전히 같다.
    private PaymentResult callPay(BigDecimal amount) {
        PaymentApiData data = paymentFeignClient.pay(new PayBody(amount)).data();
        return new PaymentResult(data.paymentId(), data.amount(), data.status());
    }

    private PaymentResult payFallback(Throwable cause) {
        // 여기 들어오는 경로는 두 가지다.
        // ① 결제 호출이 실제로 실패/타임아웃 났다 → 차단기가 이 실패를 '기록'한다
        // ② 차단기가 OPEN 이라 호출 자체가 거부됐다 (CallNotPermittedException)
        //    → payment 에는 요청이 "가지도 않는다". 스레드도 안 잡힌다. 이것이 빠른 실패.
        log.warn("결제 호출 실패 → fallback 실행. 원인: {}", cause.toString());

        // 결제는 '가짜 성공'을 만들 수 없는 핵심 동작이다. 대충 성공시키면 돈이 증발한다.
        // 그래서 결제의 우아한 실패란 — 빠르고(스레드를 안 잡고), 정직하고(503), 친절한(사람의 언어) 실패다.
        throw new ServiceUnavailableException("결제 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Override
    public void cancel(Long paymentId) {
        // 🏋️ Step 5 과제 1을 했다면: 차단기째로 Feign 위에 얹어 보세요. run() 안의 한 줄만 바뀝니다.
        paymentFeignClient.cancel(paymentId);
    }
}
