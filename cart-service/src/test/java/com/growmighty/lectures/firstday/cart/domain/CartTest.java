package com.growmighty.lectures.firstday.cart.domain;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemsCommand;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.cart.application.dto.UpdateCartItemQuantitiesCommand;
import com.growmighty.lectures.firstday.cart.application.port.RewardPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class CartTest {

    @Test
    @DisplayName("POST 단일 종류 리워드 추가")
    void addItems_addOneNewReward() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        CartView cart = cartService.addItems(addCommand(1L, 10L, addItem(101L, 2)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 2));
    }

    @Test
    @DisplayName("POST 여러 종류 리워드 추가")
    void addItems_addMultipleRewards() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        CartView cart = cartService.addItems(addCommand(1L, 10L, addItem(101L, 2), addItem(102L, 1)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 2), tuple(102L, 1));
    }

    @Test
    @DisplayName("POST 장바구니에 존재하는 리워드 추가 시 수량 증가")
    void addItems_incrementsExistingReward() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 3));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.addItems(addCommand(1L, 10L, addItem(101L, 2)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 5));
    }

    @Test
    @DisplayName("POST 한 번의 요청에 신규 종류 리워드 추가 & 기존 존재 리워드 수량 증가 동시에 처리")
    void addItems_addsAndIncrements() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 3));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.addItems(addCommand(1L, 10L, addItem(101L, 2), addItem(102L, 1)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 5), tuple(102L, 1));
    }

    @Test
    @DisplayName("POST 중복 리워드 Id 거부")
    void addItems_rejectsDuplicateRewardIds() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(101L, 1), addItem(101L, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("POST 유효하지 않은 리워드 거부")
    void addItems_rejectsInvalidReward() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(999L, 1))))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("POST 유효하지 않은 프로젝트 ID 요청 거부")
    void addItems_rejectsWrongProjectReward() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(201L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    @DisplayName("POST 유효하지 않는 수량 거부")
    void addItems_rejectsInvalidQuantity() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(101L, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("POST 시스템상 최고 수량 초과하는 요청 거부, 시스템상 최고 수량이 없으면 해당 부분은 없어도 됨")
    void addItems_rejectsQuantityAboveMax() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 98));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(101L, 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("POST 재고 초과 시 거부")
    void addItems_rejectsQuantityAboveStock() {
        CartService cartService = new CartService(new InMemoryCartRepository(), rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(103L, 3))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stock");
    }

    @Test
    @DisplayName("POST 요청에 담긴 여러 종류의 리워드 중 하나라도 유효하지 않을 시 전체 거부, 정책상 변경 가능")
    void addItems_doesNotApplyAnyMutationWhenInvalid() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.addItems(addCommand(1L, 10L, addItem(101L, 2), addItem(103L, 3))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(cartService.getCart(1L).items())
                .extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 2));
    }

    @Test
    @DisplayName("POST 추가 시 기존 장바구니에 존재하는 리워드와 동일한 리워드의 경우 새 항목을 생성하지 않음")
    void addItems_doesNotCreateDuplicateCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.addItems(addCommand(1L, 10L, addItem(101L, 1)));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 3));
    }

    @Test
    @DisplayName("PATCH 단일 리워드 수량 변경")
    void updateItems_updatesOneItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 3)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 3));
    }

    @Test
    @DisplayName("PATCH 여러 종류 리워드 수량 변경")
    void updateItems_updatesMultipleItems() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2, 102L, 1));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 3), updateItem(102L, 5)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 3), tuple(102L, 5));
    }

    @Test
    @DisplayName("PATCH 수량을 추가하는 방식이 아니라 교체하는 방식, 추후 변경 가능성 있음")
    void updateItems_setsExactQuantity() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 5)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(101L, 5));
    }

    @Test
    @DisplayName("PATCH 유효하지 않은 cartItem 요청에 대해 거절")
    void updateItems_addsMissingCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.updateItems(updateCommand(1L, 10L, updateItem(102L, 5)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 2), tuple(102L, 5));
    }

    @Test
    @DisplayName("PATCH 기존 항목과 신규 항목 동시에 요청")
    void updateItems_updatesExistingAndAddsMissingCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 4), updateItem(102L, 5)));

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 4), tuple(102L, 5));
    }

    @Test
    @DisplayName("PATCH 한 요청에 중복 리워드 Id 있을 시 거절")
    void updateItems_rejectsDuplicateRewardIds() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 3), updateItem(101L, 4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("PATCH 유효하지 않은 수량에 대해 거절")
    void updateItems_rejectsInvalidFinalQuantity() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("PATCH 여러 리워드에 대한 작업 요청 시 하나라도 무효일 경우 거절, 정책상 변경 가능")
    void updateItems_doesNotApplyAnyMutationWhenInvalid() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2, 102L, 1));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 3), updateItem(103L, 3))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(cartService.getCart(1L).items())
                .extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 2), tuple(102L, 1));
    }

    @Test
    @DisplayName("PATCH 수량 감소 시 0은 거절")
    void updateItems_zeroDoesNotDeleteCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.updateItems(updateCommand(1L, 10L, updateItem(101L, 0))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(cartService.getCart(1L).items()).hasSize(1);
    }

    @Test
    @DisplayName("DELETE 장바구니에서 리워드 삭제")
    void removeItem_deletesOneCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2, 102L, 1));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.removeItem(1L, 101L);

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactly(tuple(102L, 1));
    }

    @Test
    @DisplayName("DELETE 장바구니에서 유효하지 않은 리워드 삭제 요청 시 거절")
    void removeItem_rejectsMissingCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.removeItem(1L, 102L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DELETE 사용자 ID 유효하지 않을 시 거절")
    void removeItem_rejectsOtherUsersCartItem() {
        InMemoryCartRepository cartRepository = repositoryWithCart(2L, Map.of(101L, 2));
        CartService cartService = new CartService(cartRepository, rewards());

        assertThatThrownBy(() -> cartService.removeItem(1L, 101L))
                .isInstanceOf(EntityNotFoundException.class);

        assertThat(cartService.getCart(2L).items()).hasSize(1);
    }

    @Test
    @DisplayName("DELETE 선택된 리워드만 삭제되도록 함, 아닐 시 거절")
    void removeItem_removesOnlySelectedReward() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2, 102L, 1, 201L, 1));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.removeItem(1L, 102L);

        assertThat(cart.items()).extracting(CartView.Line::rewardId, CartView.Line::quantity)
                .containsExactlyInAnyOrder(tuple(101L, 2), tuple(201L, 1));
    }

    @Test
    @DisplayName("GET 장바구니 조회 시 프로젝트 단위로 조회")
    void getCart_groupsRewardsByProject() {
        InMemoryCartRepository cartRepository = repositoryWithCart(1L, Map.of(101L, 2, 102L, 1, 201L, 1));
        CartService cartService = new CartService(cartRepository, rewards());

        CartView cart = cartService.getCart(1L);

        assertThat(cart.projects()).hasSize(2);
        CartView.ProjectGroup project10 = cart.projects().stream()
                .filter(project -> project.projectId().equals(10L))
                .findFirst()
                .orElseThrow();
        assertThat(project10.rewards()).extracting(CartView.RewardLine::rewardId)
                .containsExactlyInAnyOrder(101L, 102L);
    }

    @Test
    @DisplayName("양식 외의 필드가 있는지 검증")
    void currentCommands_doNotUseRemovedOperationFields() {
        assertThat(recordComponentNames(AddCartItemsCommand.Item.class))
                .containsExactly("rewardId", "quantity");
        assertThat(recordComponentNames(UpdateCartItemQuantitiesCommand.Item.class))
                .containsExactly("rewardId", "quantity");
    }

    @Test
    @DisplayName("Domain addItem merges the same reward into one item")
    void addItem_mergesSameReward() {
        Cart cart = Cart.create(1L);

        cart.addItem(10L, 2);
        cart.addItem(10L, 3);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Domain addItem keeps different rewards as separate items")
    void addItem_distinctRewards() {
        Cart cart = Cart.create(1L);

        cart.addItem(10L, 1);
        cart.addItem(20L, 1);

        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("Domain addItem rejects quantity above max")
    void addItem_exceedsMaxQuantity_throws() {
        Cart cart = Cart.create(1L);

        assertThatThrownBy(() -> cart.addItem(10L, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Domain changeQuantity sets quantity and removeItem deletes item")
    void changeQuantity_and_removeItem() {
        Cart cart = Cart.create(1L);
        cart.addItem(10L, 1);

        cart.changeQuantity(10L, 7);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(7);

        cart.removeItem(10L);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    @DisplayName("Domain setItemQuantity updates existing rewards and adds missing rewards")
    void setItemQuantity_updatesExistingAndAddsMissingReward() {
        Cart cart = Cart.create(1L);
        cart.addItem(10L, 2);

        cart.setItemQuantity(10L, 5);
        cart.setItemQuantity(20L, 3);

        assertThat(cart.getItems()).extracting(CartItem::getRewardId, CartItem::getQuantity)
                .containsExactlyInAnyOrder(tuple(10L, 5), tuple(20L, 3));
    }

    @Test
    @DisplayName("Domain changeQuantity and removeItem reject missing items")
    void operateMissingItem_throws() {
        Cart cart = Cart.create(1L);

        assertThatThrownBy(() -> cart.changeQuantity(10L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.removeItem(10L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Domain addItem rejects more than max distinct rewards")
    void addItem_exceedsMaxDistinct_throws() {
        Cart cart = Cart.create(1L);
        for (long rewardId = 1; rewardId <= Cart.MAX_DISTINCT_ITEMS; rewardId++) {
            cart.addItem(rewardId, 1);
        }

        assertThatThrownBy(() -> cart.addItem(999L, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Domain clear removes all items")
    void clear_removesAll() {
        Cart cart = Cart.create(1L);
        cart.addItem(10L, 1);
        cart.addItem(20L, 1);

        cart.clear();

        assertThat(cart.getItems()).isEmpty();
    }

    private static AddCartItemsCommand addCommand(Long userId, Long projectId, AddCartItemsCommand.Item... items) {
        return new AddCartItemsCommand(userId, projectId, List.of(items));
    }

    private static AddCartItemsCommand.Item addItem(Long rewardId, Integer quantity) {
        return new AddCartItemsCommand.Item(rewardId, quantity);
    }

    private static UpdateCartItemQuantitiesCommand updateCommand(Long userId, Long projectId,
                                                                 UpdateCartItemQuantitiesCommand.Item... items) {
        return new UpdateCartItemQuantitiesCommand(userId, projectId, List.of(items));
    }

    private static UpdateCartItemQuantitiesCommand.Item updateItem(Long rewardId, Integer quantity) {
        return new UpdateCartItemQuantitiesCommand.Item(rewardId, quantity);
    }

    private static InMemoryCartRepository repositoryWithCart(Long userId, Map<Long, Integer> items) {
        InMemoryCartRepository cartRepository = new InMemoryCartRepository();
        cartRepository.put(cartWithItems(userId, items));
        return cartRepository;
    }

    private static RewardPort rewards() {
        Map<Long, RewardSnapshot> rewards = Map.of(
                101L, reward(101L, 10L, "White Round", "Example Project", 7_500, 10, true),
                102L, reward(102L, 10L, "White Square", "Example Project", 7_500, 10, true),
                103L, reward(103L, 10L, "Limited", "Example Project", 7_500, 2, true),
                201L, reward(201L, 20L, "Black Round", "Other Project", 10_000, 10, true)
        );
        return rewards::get;
    }

    private static RewardSnapshot reward(Long rewardId, Long projectId, String rewardName, String projectName,
                                         int price, int remainingQuantity, boolean orderable) {
        return new RewardSnapshot(
                rewardId,
                projectId,
                rewardName,
                projectName,
                BigDecimal.valueOf(price),
                remainingQuantity,
                orderable);
    }

    private static Cart cartWithItems(Long userId, Map<Long, Integer> items) {
        Cart cart = Cart.create(userId);
        items.forEach(cart::addItem);
        return cart;
    }

    private static List<String> recordComponentNames(Class<? extends Record> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }

    private static final class InMemoryCartRepository implements CartRepository {
        private final Map<Long, Cart> carts = new HashMap<>();

        @Override
        public Cart save(Cart cart) {
            put(cart);
            return cart;
        }

        @Override
        public Optional<Cart> findByUserId(Long userId) {
            return Optional.ofNullable(carts.get(userId));
        }

        void put(Cart cart) {
            carts.put(cart.getUserId(), cart);
        }
    }
}
