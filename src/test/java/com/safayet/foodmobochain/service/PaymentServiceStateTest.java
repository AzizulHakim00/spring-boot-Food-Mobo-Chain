package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceStateTest {
    @Mock private OrderRepository orderRepository;
    @Mock private NotificationService notificationService;
    @Mock private Relations relations;
    @InjectMocks private PaymentService paymentService;

    @Test void cancelledOnlineOrderCannotBePaidAfterCancellation() {
        User buyer = User.builder().id("users:1").build();
        CustomerOrder order = CustomerOrder.builder()
                .id("orders:10").orderNumber("FMC-TEST").buyerId(buyer.getId()).buyer(buyer)
                .status(OrderStatus.CANCELLED).build();
        order.setPayment(Payment.builder().id("payments:20").method(PaymentMethod.SSLCOMMERZ)
                .status(PaymentStatus.CANCELLED).build());
        when(orderRepository.findByOrderNumber("FMC-TEST")).thenReturn(Optional.of(order));
        when(relations.order(order)).thenReturn(order);
        assertThatThrownBy(() -> paymentService.completeDemoPayment(buyer, "FMC-TEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can no longer be completed");
    }
}
