package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.OrderRepository;
import com.safayet.foodmobochain.repository.FoodCartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowTest {
    @Mock private OrderRepository orderRepository;
    @Mock private FoodCartRepository foodCartRepository;
    @Mock private Relations relations;
    @Mock private CartService cartService;
    @Mock private DiscountService discountService;
    @Mock private PaymentService paymentService;
    @Mock private NotificationService notificationService;
    @InjectMocks private OrderService orderService;

    @Test void sellerWorkflowOnlyAllowsExpectedNextStates() {
        assertThat(orderService.allowedNext(OrderStatus.CONFIRMED)).containsExactly(OrderStatus.ACCEPTED);
        assertThat(orderService.allowedNext(OrderStatus.ACCEPTED)).containsExactlyInAnyOrder(OrderStatus.COOKING, OrderStatus.CANCELLED);
        assertThat(orderService.allowedNext(OrderStatus.COOKING)).containsExactly(OrderStatus.READY);
        assertThat(orderService.allowedNext(OrderStatus.READY)).containsExactly(OrderStatus.ON_THE_WAY);
        assertThat(orderService.allowedNext(OrderStatus.ON_THE_WAY)).containsExactly(OrderStatus.DELIVERED);
        assertThat(orderService.allowedNext(OrderStatus.DELIVERED)).isEmpty();
        assertThat(orderService.allowedNext(OrderStatus.CANCELLED)).isEmpty();
    }

    @Test void cancelledOrdersDoNotPretendToBeDelivered() {
        assertThat(orderService.progressStep(OrderStatus.CANCELLED)).isEqualTo(-1);
        assertThat(orderService.progressStep(OrderStatus.DELIVERED)).isEqualTo(6);
    }

    @Test void sellerCancellationUpdatesEmbeddedPaymentAndDelivery() {
        User seller = User.builder().id("users:7").build();
        FoodCart cart = FoodCart.builder().id("foodCarts:8").ownerId(seller.getId()).owner(seller).build();
        CustomerOrder order = CustomerOrder.builder().id("orders:9")
                .orderNumber("FMC-CANCEL").foodCartId(cart.getId()).foodCart(cart)
                .status(OrderStatus.ACCEPTED).delivery(Delivery.builder().status(DeliveryStatus.PREPARING).build()).build();
        when(orderRepository.findByOrderNumber("FMC-CANCEL")).thenReturn(Optional.of(order));
        when(relations.order(order)).thenReturn(order);
        orderService.updateStatusBySeller(seller, "FMC-CANCEL", OrderStatus.CANCELLED);
        verify(paymentService).requestRefundIfNeeded(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getDelivery().getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        verify(orderRepository).save(order);
    }
}
