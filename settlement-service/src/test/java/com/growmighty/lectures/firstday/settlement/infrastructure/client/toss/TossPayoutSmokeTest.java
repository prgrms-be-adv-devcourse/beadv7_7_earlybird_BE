package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.config.TossPayoutClientConfig;
import com.growmighty.lectures.firstday.settlement.config.TossPayoutProperties;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Tag("toss-payout-smoke")
class TossPayoutSmokeTest {

    private static final String SECRET_KEY_ENV = "TOSS_PAYOUT_SMOKE_SECRET_KEY";
    private static final String SECURITY_KEY_ENV = "TOSS_PAYOUT_SMOKE_SECURITY_KEY";
    private static final String SELLER_ID_ENV = "TOSS_PAYOUT_SMOKE_SELLER_ID";
    private static final String PAYOUT_DATE_ENV = "TOSS_PAYOUT_SMOKE_PAYOUT_DATE";
    private static final String AMOUNT_ENV = "TOSS_PAYOUT_SMOKE_AMOUNT";

    @Test
    @DisplayName("토스 테스트 셀러의 예약 지급 요청이 접수되고 외부 식별자가 반환된다")
    void acceptsScheduledPayoutInTossTestEnvironment() {
        String secretKey = requiredEnvironment(SECRET_KEY_ENV);
        String securityKey = requiredEnvironment(SECURITY_KEY_ENV);
        String sellerId = requiredEnvironment(SELLER_ID_ENV);
        LocalDate payoutDate = payoutDate();
        long amount = payoutAmount();
        String requestToken = UUID.randomUUID().toString().replace("-", "");
        TossPayoutProperties properties = new TossPayoutProperties(
                true,
                secretKey,
                securityKey,
                null,
                null,
                null
        );
        RestClient restClient = new TossPayoutClientConfig().tossPayoutRestClient(properties);
        TossPayoutGateway gateway = new TossPayoutGateway(
                restClient,
                new TossPayoutJweCodec(properties.securityKeyBytes(), Clock.systemDefaultZone()),
                new ObjectMapper(),
                properties.secretKey()
        );
        ScheduledPayoutRequest request = new ScheduledPayoutRequest(
                "earlybird-smoke-" + requestToken,
                sellerId,
                payoutDate,
                Money.wons(amount),
                "얼리버드",
                UUID.randomUUID().toString()
        );

        PayoutGatewayResult result = gateway.requestScheduledPayout(request);

        assertThat(result).isInstanceOfSatisfying(
                PayoutGatewayResult.Accepted.class,
                accepted -> {
                    assertThat(accepted.payoutId()).isNotBlank();
                    assertThat(accepted.status()).isEqualTo(PayoutAttemptStatus.REQUESTED);
                }
        );
    }

    private static LocalDate payoutDate() {
        LocalDate payoutDate = LocalDate.parse(requiredEnvironment(PAYOUT_DATE_ENV));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        if (!payoutDate.isAfter(today) || payoutDate.isAfter(today.plusYears(1))) {
            throw new IllegalArgumentException(
                    PAYOUT_DATE_ENV + "는 오늘 이후 1년 이내의 영업일이어야 합니다."
            );
        }
        return payoutDate;
    }

    private static long payoutAmount() {
        long amount = Long.parseLong(requiredEnvironment(AMOUNT_ENV));
        if (amount <= 0) {
            throw new IllegalArgumentException(AMOUNT_ENV + "는 양수여야 합니다.");
        }
        return amount;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("필수 smoke test 환경 변수가 없습니다: " + name);
        }
        return value;
    }
}
