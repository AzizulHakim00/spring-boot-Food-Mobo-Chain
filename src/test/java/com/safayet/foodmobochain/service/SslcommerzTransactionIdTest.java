package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.CustomerOrder;
import com.safayet.foodmobochain.model.Payment;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.OrderStatus;
import com.safayet.foodmobochain.model.enums.PaymentMethod;
import com.safayet.foodmobochain.model.enums.PaymentStatus;
import com.safayet.foodmobochain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SslcommerzTransactionIdTest {
    @Mock private OrderRepository orders;
    @Mock private NotificationService notifications;
    @Mock private Relations relations;
    @Mock private SslcommerzGatewayService gateway;
    @InjectMocks private PaymentService payments;

    @Test
    void newCheckoutUsesTransactionIdWithinGatewayLimit() {
        CustomerOrder order = CustomerOrder.builder()
                .orderNumber("FMC261011-9E11D93B")
                .total(new BigDecimal("275.00"))
                .build();

        Payment payment = payments.createForOrder(order, PaymentMethod.SSLCOMMERZ);

        assertThat(payment.getTransactionId()).hasSize(30);
        assertThat(payment.getTransactionId())
                .matches("FMC-FMC261011-9E11D93B-[A-F0-9]{7}");
    }

    @Test
    void existingPendingOrderIsFixedBeforeStartingGatewaySession() {
        User buyer = User.builder().id("users:123").fullName("Test Buyer").build();
        String orderNumber = "FMC261011-9E11D93B";
        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(orderNumber)
                .buyerId(buyer.getId())
                .buyer(buyer)
                .status(OrderStatus.PENDING_PAYMENT)
                .total(new BigDecimal("275.00"))
                .build();
        Payment payment = Payment.builder()
                .method(PaymentMethod.SSLCOMMERZ)
                .status(PaymentStatus.PENDING)
                .gateway("DEMO")
                .transactionId("FMC-FMC261011-9E11D93B-ABCDEF12")
                .amount(new BigDecimal("275.00"))
                .build();
        order.setPayment(payment);

        when(gateway.enabled()).thenReturn(true);
        when(orders.findByOrderNumber(orderNumber)).thenReturn(Optional.of(order));
        when(relations.order(order)).thenReturn(order);
        when(gateway.initiate(order, buyer)).thenReturn(
                new SslcommerzGatewayService.GatewaySession(
                        "https://sandbox.sslcommerz.com/gwprocess/v4/gw.php", "test-session"));

        payments.startSandbox(buyer, orderNumber);

        assertThat(payment.getTransactionId()).isEqualTo("FMC-FMC261011-9E11D93B-ABCDEF1");
        assertThat(payment.getGateway()).isEqualTo("SSLCOMMERZ_SANDBOX");
        assertThat(payment.getSessionKey()).isEqualTo("test-session");
        InOrder sequence = inOrder(orders, gateway);
        sequence.verify(orders).save(order);
        sequence.verify(gateway).initiate(order, buyer);
        sequence.verify(orders).save(order);
        verify(orders, times(2)).save(order);
    }
}
