package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.SpiceLevel;
import com.safayet.foodmobochain.repository.CartRepository;
import com.safayet.foodmobochain.repository.FoodItemRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CartServiceValidationTest {
    private final CartRepository carts = mock(CartRepository.class);
    private final FoodItemRepository foods = mock(FoodItemRepository.class);
    private final Relations relations = mock(Relations.class);
    private final CartService service = new CartService(carts, foods, relations);
    private final User buyer = User.builder().id("users:2").build();

    @Test void zeroQuantityIsRejectedBeforeWritingToMongo() {
        assertThatThrownBy(() -> service.add(buyer, "foodItems:2", 0, SpiceLevel.REGULAR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 20");
        verifyNoInteractions(carts, foods, relations);
    }

    @Test void excessivelyLargeQuantityIsRejected() {
        assertThatThrownBy(() -> service.add(buyer, "foodItems:2", 21, SpiceLevel.REGULAR))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(carts, foods, relations);
    }
}
