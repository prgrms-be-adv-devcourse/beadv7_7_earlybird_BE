package com.growmighty.lectures.firstday.order;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.infrastructure.OrderRepositoryAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(OrderRepositoryAdapter.class)
class OrderRepositoryTests {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("주문 저장 및 조회 테스트")
    void saveAndFindOrderTest() {
        // 서비스 분리 후 userId / productId 는 다른 서비스의 식별자(Long)일 뿐이다.
        Long userId = 1L;
        Long productId = 100L;

        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.create("원목 4인용 식탁", BigDecimal.valueOf(179000), productId, 1));
        Order order = Order.create(userId, items);

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getItems()).hasSize(1);
        OrderItem foundItem = found.getItems().get(0);
        assertThat(foundItem.getQuantity()).isEqualTo(1);
        assertThat(foundItem.getProductId()).isEqualTo(productId);
        assertThat(foundItem.getName()).isEqualTo("원목 4인용 식탁");
        assertThat(foundItem.getPrice().getValue()).isEqualByComparingTo(BigDecimal.valueOf(179000));
    }
}
