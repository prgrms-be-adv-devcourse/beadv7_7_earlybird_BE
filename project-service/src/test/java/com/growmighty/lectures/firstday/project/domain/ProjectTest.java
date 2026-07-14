package com.growmighty.lectures.firstday.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

    private Project project(int stock) {
        return Project.register(1L, "원목 식탁", BigDecimal.valueOf(259000), stock, "설명");
    }

    @Test
    @DisplayName("재고가 있으면 판매중, 0이면 품절 상태로 등록된다")
    void register_statusByStock() {
        assertThat(project(10).getStatus()).isEqualTo(ProjectStatus.ON_SALE);
        assertThat(project(0).getStatus()).isEqualTo(ProjectStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("가격이 0 이하이거나 재고가 음수면 등록할 수 없다")
    void register_invalidValues_throw() {
        assertThatThrownBy(() -> Project.register(1L, "x", BigDecimal.ZERO, 1, "d"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Project.register(1L, "x", BigDecimal.valueOf(1000), -1, "d"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("재고를 차감하면 수량이 줄고, 0이 되면 품절로 전이된다")
    void decreaseStock_transitionsToOutOfStock() {
        Project project = project(5);

        project.decreaseStock(2);
        assertThat(project.getStockQuantity()).isEqualTo(3);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ON_SALE);

        project.decreaseStock(3);
        assertThat(project.getStockQuantity()).isZero();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고보다 많이 차감하면 예외가 발생한다")
    void decreaseStock_insufficient_throws() {
        Project project = project(1);
        assertThatThrownBy(() -> project.decreaseStock(2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("판매 종료된 프로젝트은 재고를 차감할 수 없다")
    void decreaseStock_discontinued_throws() {
        Project project = project(10);
        project.discontinue();
        assertThatThrownBy(() -> project.decreaseStock(1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("재고를 복원하면 품절 프로젝트이 다시 판매중으로 전이된다")
    void restoreStock_backToOnSale() {
        Project project = project(1);
        project.decreaseStock(1);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.OUT_OF_STOCK);

        project.restoreStock(2);
        assertThat(project.getStockQuantity()).isEqualTo(2);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.ON_SALE);
    }

    @Test
    @DisplayName("가격을 0 이하로 변경하면 예외가 발생한다")
    void changePrice_invalid_throws() {
        Project project = project(10);
        assertThatThrownBy(() -> project.changePrice(BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
