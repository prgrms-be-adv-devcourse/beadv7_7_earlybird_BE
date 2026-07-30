package com.growmighty.lectures.firstday.order;

import com.growmighty.lectures.firstday.order.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import com.growmighty.lectures.firstday.order.infrastructure.OrderRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
// 내장 DB 가 아니면 Boot 가 ddl-auto 를 기본 none 으로 두므로, 테스트 스키마 생성을 명시한다.
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
// JpaAuditingConfig: @DataJpaTest 도 @WebMvcTest 처럼 일반 @Configuration 빈은 스캔에서 걸러내므로
// created_at/updated_at 을 실제로 채우려면 명시적으로 가져와야 한다 (@Import 는 그 필터를 우회한다).
@Import({OrderRepositoryAdapter.class, JpaAuditingConfig.class})
class OrderRepositoryTests {

    // 테스트도 운영과 동일한 MySQL 로 돈다 (로컬 docker-compose 와 동일 버전, Docker 필요)
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Order STATUS 변화 테스트")
    void orderCreationStatusesArePersistedAndLoaded() {
        Order paymentOrder = saveAndReload(order());

        paymentOrder.markPaymentRequested();
        paymentOrder = saveAndReload(paymentOrder);
        assertThat(paymentOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_REQUEST);

        paymentOrder.markPaymentProcessing();
        paymentOrder = saveAndReload(paymentOrder);
        assertThat(paymentOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);

        paymentOrder.markPaymentFailed();
        paymentOrder = saveAndReload(paymentOrder);
        assertThat(paymentOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

        Order stockOrder = order();
        stockOrder.markStockReservationFailed();
        stockOrder = saveAndReload(stockOrder);
        assertThat(stockOrder.getStatus()).isEqualTo(OrderStatus.STOCK_FAILED);
    }

    @Disabled
    @Test
    @DisplayName("주문 저장 및 조회 테스트")
    void saveAndFindOrderTest() {
        Long userId = 1L;
        Long projectId = 100L;
        Long rewardId = 10L;
        Order order = Order.create(null, userId, projectId,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(179000), projectId, rewardId, 1)),
                "Receiver", "010-0000-0000", "Seoul", "06236");

        assertThat(order.getId()).isNull();

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getId()).isInstanceOf(Long.class);
        assertThat(found.getProjectId()).isEqualTo(projectId);
        assertThat(found.getItems()).hasSize(1);
        OrderItem foundItem = found.getItems().get(0);
        assertThat(foundItem.getQuantity()).isEqualTo(1);
        assertThat(foundItem.getProjectId()).isEqualTo(projectId);
        assertThat(foundItem.getRewardId()).isEqualTo(rewardId);
        assertThat(foundItem.getName()).isEqualTo("Reward A");
        assertThat(foundItem.getPrice().getValue()).isEqualByComparingTo(BigDecimal.valueOf(179000));
    }

    @Disabled
    @Test
    @DisplayName("project에 완료된 order 이력 존재 유무 리턴")
    void existsByProjectId() {
        Long existingProjectId = 100L;
        Long otherProjectId = 200L;

        Order order = Order.create(null, 1L, existingProjectId,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), existingProjectId, 10L, 1)),
                "Receiver", "010-0000-0000", "Seoul", "06236");

        orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        assertThat(orderRepository.existsByProjectId(existingProjectId)).isTrue();
        assertThat(orderRepository.existsByProjectId(otherProjectId)).isFalse();
    }

    private Order saveAndReload(Order order) {
        Order saved = orderRepository.saveAndFlush(order);
        entityManager.clear();
        return orderRepository.findById(saved.getId()).orElseThrow();
    }

    private Order order() {
        return Order.create(null, 1L, 100L,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 100L, 10L, 1)),
                "Receiver", "010-0000-0000", "Seoul", "06236");
    }

    private RewardPort rewardPort() {
        return new RewardPort() {
            @Override
            public RewardSnapshot getReward(Long rewardId) {
                return new RewardSnapshot(rewardId, 100L, "Reward A", BigDecimal.valueOf(10_000), 10, true);
            }

            @Override
            public void decreaseStock(Long rewardId, int quantity) {
            }

            @Override
            public void restoreStock(Long rewardId, int quantity) {
            }
        };
    }

    private PaymentPort paymentPort() {
        return new PaymentPort() {
            @Override
            public PaymentResult pay(Long orderId, Long userId, BigDecimal amount) {
                return PaymentResult.unknown(amount);
            }

            @Override
            public RefundResult refund(Long orderId, BigDecimal amount) {
                return RefundResult.success(amount, "refund-reference");
            }

            @Override
            public PaymentResult getPaymentResult(Long orderId) {
                return PaymentResult.unknown(BigDecimal.valueOf(13_000));
            }
        };
    }
}
