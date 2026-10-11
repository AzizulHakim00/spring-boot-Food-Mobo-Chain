package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.Cart;
import com.safayet.foodmobochain.model.CartItem;
import com.safayet.foodmobochain.model.FoodCart;
import com.safayet.foodmobochain.model.FoodItem;
import com.safayet.foodmobochain.model.Category;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
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
    @Test void newBuyerCartHasDeterministicIdentity() {
        when(carts.findByBuyerId(buyer.getId())).thenReturn(Optional.empty());
        when(carts.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(relations.shopping(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart created = service.getOrCreate(buyer);
        assertThat(created.getId()).isEqualTo("shoppingCarts:" + buyer.getId());
        assertThat(created.getBuyerId()).isEqualTo(buyer.getId());
        assertThat(created.getItems()).isEmpty();
        verify(carts).save(any(Cart.class));
    }

    @Test void orphanedFoodIsRemovedButExistingCartItemsArePreserved() {
        FoodCart seller = FoodCart.builder().id("foodCarts:1").build();
        FoodItem kept = FoodItem.builder().id("foodItems:1").available(false)
                .archived(true).build();
        kept.setFoodCart(seller);
        kept.setCategory(Category.builder().id("categories:1").active(false).build());
        CartItem valid = CartItem.builder().id("cartItems:1").quantity(3).build();
        valid.setFoodItem(kept);
        CartItem missing = CartItem.builder().id("cartItems:orphan")
                .foodItemId("foodItems:deleted").quantity(2).build();
        Cart cart = Cart.builder().id("shoppingCarts:1").buyerId(buyer.getId())
                .items(new ArrayList<>(List.of(valid, missing))).build();
        when(carts.findByBuyerId(buyer.getId())).thenReturn(Optional.of(cart));
        when(relations.shopping(cart)).thenReturn(cart);

        assertThat(service.removeOrphanedItems(buyer)).isEqualTo(1);
        assertThat(cart.getItems()).containsExactly(valid);
        assertThat(valid.getQuantity()).isEqualTo(3);
        verify(carts).save(cart);
    }

    @Test void healthyCartIsNotModifiedByRecovery() {
        FoodItem food = FoodItem.builder().id("foodItems:1").build();
        food.setFoodCart(FoodCart.builder().id("foodCarts:1").build());
        food.setCategory(Category.builder().id("categories:1").build());
        CartItem item = CartItem.builder().id("cartItems:1").quantity(2).build();
        item.setFoodItem(food);
        Cart cart = Cart.builder().id("shoppingCarts:1").buyerId(buyer.getId())
                .items(new ArrayList<>(List.of(item))).build();
        when(carts.findByBuyerId(buyer.getId())).thenReturn(Optional.of(cart));
        when(relations.shopping(cart)).thenReturn(cart);

        assertThat(service.removeOrphanedItems(buyer)).isZero();
        assertThat(cart.getItems()).containsExactly(item);
        verify(carts, never()).save(any(Cart.class));
    }

}
