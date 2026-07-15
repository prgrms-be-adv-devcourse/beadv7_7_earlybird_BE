package com.growmighty.lectures.firstday.order;

import com.growmighty.lectures.firstday.order.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.infrastructure.OrderRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    @Test
    @DisplayName("주문 저장 및 조회 테스트")
    void saveAndFindOrderTest() {
        // 서비스 분리 후 userId / projectId 는 다른 서비스의 식별자(Long)일 뿐이다.
        Long userId = 1L;
        Long projectId = 100L;
        Long rewardId = 10L;

        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.create("원목 4인용 식탁", BigDecimal.valueOf(179000), projectId, rewardId, 1));
        Order order = Order.create(userId, items, "김하나한", "010-0000-0000", "서울시 강남구", "06236");

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getItems()).hasSize(1);
        OrderItem foundItem = found.getItems().get(0);
        assertThat(foundItem.getQuantity()).isEqualTo(1);
        assertThat(foundItem.getProjectId()).isEqualTo(projectId);
        assertThat(foundItem.getRewardId()).isEqualTo(rewardId);
        assertThat(foundItem.getName()).isEqualTo("원목 4인용 식탁");
        assertThat(foundItem.getPrice().getValue()).isEqualByComparingTo(BigDecimal.valueOf(179000));
    }
}
