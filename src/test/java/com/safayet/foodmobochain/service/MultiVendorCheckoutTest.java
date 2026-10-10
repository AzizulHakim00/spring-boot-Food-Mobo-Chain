package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.CheckoutDTO;
import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.FoodCartRepository;
import com.safayet.foodmobochain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MultiVendorCheckoutTest {
    @Mock private OrderRepository orders;
    @Mock private FoodCartRepository foodCarts;
    @Mock private CartService cartService;
    @Mock private DiscountService discountService;
    @Mock private PaymentService payments;
    @Mock private NotificationService notifications;
    @Mock private Relations relations;
    @InjectMocks private OrderService service;

    @Test
    void checkoutCreatesSeparateSellerOrdersWithSplitDiscountAndDelivery() {
        User buyer = User.builder().id("buyer:1").build();
        FoodCart first = vendor("foodCarts:1", "seller:1", "20.00");
        FoodCart second = vendor("foodCarts:2", "seller:2", "15.00");
        CartItem a = item(first, "foods:1", "120.00");
        CartItem b = item(second, "foods:2", "80.00");
        Cart cart = Cart.builder().buyerId("buyer:1").items(List.of(a, b)).build();

        when(cartService.getOrCreate(buyer)).thenReturn(cart);
        when(cartService.vendorGroups(cart)).thenReturn(List.of(
                new CartService.VendorGroup(first, List.of(a), new BigDecimal("120.00")),
                new CartService.VendorGroup(second, List.of(b), new BigDecimal("80.00"))));
        when(discountService.calculate(eq("SAVE"), eq(new BigDecimal("200.00"))))
                .thenReturn(new DiscountService.AppliedDiscount(null, new BigDecimal("40.00")));
        when(orders.save(any(CustomerOrder.class))).thenAnswer(call -> call.getArgument(0));

        CheckoutDTO dto = CheckoutDTO.builder().discountCode("SAVE")
                .paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .deliveryAddress("Road 15, Dhanmondi, Dhaka")
                .phone("01712345678").build();

        List<CustomerOrder> created = service.createOrders(buyer, dto);

        assertEquals(2, created.size());
        assertEquals("foodCarts:1", created.get(0).getFoodCartId());
        assertEquals("foodCarts:2", created.get(1).getFoodCartId());
        assertEquals(new BigDecimal("24.00"), created.get(0).getDiscountAmount());
        assertEquals(new BigDecimal("16.00"), created.get(1).getDiscountAmount());
        assertEquals(new BigDecimal("116.00"), created.get(0).getTotal());
        assertEquals(new BigDecimal("79.00"), created.get(1).getTotal());
        assertEquals(new BigDecimal("195.00"),
                created.get(0).getTotal().add(created.get(1).getTotal()));
        assertEquals(1, created.get(0).getItems().size());
        assertEquals(1, created.get(1).getItems().size());
        assertEquals("foods:1", created.get(0).getItems().getFirst().getFoodItemIdSnapshot());
        assertEquals("foods:2", created.get(1).getItems().getFirst().getFoodItemIdSnapshot());
        assertEquals(OrderStatus.CONFIRMED, created.get(0).getStatus());
        assertEquals(OrderStatus.CONFIRMED, created.get(1).getStatus());
        verify(payments, times(2)).createForOrder(any(CustomerOrder.class), eq(PaymentMethod.CASH_ON_DELIVERY));
        verify(orders, times(2)).save(any(CustomerOrder.class));
        verify(cartService).clear(buyer);
    }

    @Test
    void proratedDiscountNeverExceedsSellerSubtotalAndRoundingIsExact() {
        Map<String, BigDecimal> subtotals = new LinkedHashMap<>();
        subtotals.put("cart1", new BigDecimal("0.01"));
        subtotals.put("cart2", new BigDecimal("0.01"));
        subtotals.put("cart3", new BigDecimal("0.01"));
        Map<String, BigDecimal> result =
                OrderService.allocateDiscountShares(subtotals, new BigDecimal("0.02"));
        assertEquals(new BigDecimal("0.02"),
                result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        result.forEach((key, value) -> {
            assertTrue(value.signum() >= 0);
            assertTrue(value.compareTo(subtotals.get(key)) <= 0);
        });

        Map<String, BigDecimal> full =
                OrderService.allocateDiscountShares(subtotals, new BigDecimal("0.03"));
        assertEquals(new BigDecimal("0.01"), full.get("cart1"));
        assertEquals(new BigDecimal("0.01"), full.get("cart2"));
        assertEquals(new BigDecimal("0.01"), full.get("cart3"));
    }

    @Test
    void unavailableSellerRejectsWholeCheckoutBeforeAnyWrite() {
        User buyer = User.builder().id("buyer:2").build();
        FoodCart seller = vendor("foodCarts:9", "seller:9", "30.00");
        seller.setOpen(false);
        CartItem line = item(seller, "foods:9", "55.00");
        Cart cart = Cart.builder().buyerId(buyer.getId()).items(List.of(line)).build();
        when(cartService.getOrCreate(buyer)).thenReturn(cart);
        when(cartService.vendorGroups(cart)).thenReturn(List.of(
                new CartService.VendorGroup(seller, List.of(line), new BigDecimal("55.00"))));
        CheckoutDTO dto = CheckoutDTO.builder().paymentMethod(PaymentMethod.CASH_ON_DELIVERY)
                .deliveryAddress("Road 15, Dhanmondi, Dhaka").phone("01712345678").build();
        assertThrows(IllegalArgumentException.class, () -> service.createOrders(buyer, dto));
        verifyNoInteractions(orders, discountService, payments);
        verify(cartService, never()).clear(any());
    }

    private FoodCart vendor(String cartId, String sellerId, String fee) {
        User user = User.builder().id(sellerId).enabled(true).build();
        FoodCart cart = FoodCart.builder().id(cartId).name(cartId).approved(true).open(true)
                .owner(user).deliveryFee(new BigDecimal(fee)).estimatedDeliveryMinutes(35).build();
        cart.setOwner(user);
        return cart;
    }

    private CartItem item(FoodCart cart, String id, String price) {
        FoodItem food = FoodItem.builder().id(id).name(id).image("/images/foods/test.webp")
                .price(new BigDecimal(price)).available(true).archived(false).build();
        food.setFoodCart(cart);
        food.setCategory(Category.builder().id("category:1").active(true).build());
        CartItem line = CartItem.builder().id("cartItems:" + id).quantity(1)
                .spiceLevel(SpiceLevel.REGULAR).build();
        line.setFoodItem(food);
        return line;
    }
}
