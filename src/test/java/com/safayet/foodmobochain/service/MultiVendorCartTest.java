package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.SpiceLevel;
import com.safayet.foodmobochain.repository.CartRepository;
import com.safayet.foodmobochain.repository.FoodItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiVendorCartTest {

    @Test
    void addAcrossFoodCartsKeepsBothItemsAndChargesTwoDeliveryFees() {
        CartRepository carts = mock(CartRepository.class);
        FoodItemRepository foods = mock(FoodItemRepository.class);
        Relations relations = mock(Relations.class);
        CartService service = new CartService(carts, foods, relations);
        User buyer = User.builder().id("buyer:1").build();

        Category category = Category.builder().id("cat:1").active(true).build();
        User seller = User.builder().id("seller:1").enabled(true).build();
        FoodCart first = FoodCart.builder().id("foodCarts:1").name("First cart")
                .open(true).approved(true).owner(seller).deliveryFee(new BigDecimal("20.00")).build();
        FoodCart second = FoodCart.builder().id("foodCarts:2").name("Second cart")
                .open(true).approved(true).owner(seller).deliveryFee(new BigDecimal("15.00")).build();

        FoodItem firstFood = food("foods:1", first, category, "120.00");
        FoodItem secondFood = food("foods:2", second, category, "80.00");
        CartItem existing = CartItem.builder().id("cartItems:1").foodItemId("foods:1")
                .foodItem(firstFood).quantity(1).build();
        Cart cart = Cart.builder().buyerId(buyer.getId())
                .items(new ArrayList<>(List.of(existing))).build();
        cart.setFoodCart(first);

        when(foods.findById("foods:2")).thenReturn(Optional.of(secondFood));
        when(relations.food(secondFood)).thenReturn(secondFood);
        when(carts.findByBuyerId("buyer:1")).thenReturn(Optional.of(cart));
        when(relations.shopping(cart)).thenReturn(cart);

        service.add(buyer, "foods:2", 2, SpiceLevel.REGULAR);

        assertEquals(2, cart.getItems().size());
        assertNull(cart.getFoodCartId(), "Mixed-vendor cart must not pretend to have one owner");
        assertEquals(2, service.vendorGroups(cart).size());
        assertEquals(new BigDecimal("35.00"), service.deliveryTotal(cart));
        assertEquals(new BigDecimal("280.00"), service.subtotal(cart));
        verify(carts).save(cart);

        String secondLineId = cart.getItems().stream()
                .filter(line -> "foods:2".equals(line.getFoodItemId())).findFirst().orElseThrow().getId();
        service.remove(buyer, secondLineId);
        assertEquals(1, cart.getItems().size());
        assertEquals("foodCarts:1", cart.getFoodCartId(),
                "Legacy single-vendor cart field is restored when one seller remains");
    }

    private FoodItem food(String id, FoodCart cart, Category category, String price) {
        FoodItem item = FoodItem.builder().id(id).name(id).price(new BigDecimal(price))
                .available(true).archived(false).build();
        item.setFoodCart(cart);
        item.setCategory(category);
        return item;
    }
}
