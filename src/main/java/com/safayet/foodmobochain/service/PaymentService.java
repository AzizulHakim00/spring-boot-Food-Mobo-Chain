package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Payments are embedded in the corresponding orders; never persist them independently. */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final Relations relations;
    private final SslcommerzGatewayService sslGateway;

    public Payment createForOrder(CustomerOrder order, PaymentMethod method) {
        Payment payment = Payment.builder()
                .id("payments:" + UUID.randomUUID())
                .method(method)
                .status(PaymentStatus.PENDING)
                .gateway(method == PaymentMethod.CASH_ON_DELIVERY ? "COD" : "DEMO")
                .transactionId("FMC-" + order.getOrderNumber() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .amount(order.getTotal())
                .createdAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE))
                .build();
        payment.setOrder(order);
        order.setPayment(payment);
        return payment;
    }

    public Payment paymentFor(CustomerOrder order) {
        if (order.getPayment() == null) throw new IllegalArgumentException("Payment record was not found.");
        order.getPayment().setOrder(order);
        return order.getPayment();
    }

    public List<Payment> recentPayments() {
        return orderRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(relations::order).filter(order -> order.getPayment() != null)
                .map(CustomerOrder::getPayment).toList();
    }

    public String paymentModeLabel() { return sslGateway.enabled() ? "SSLCOMMERZ Sandbox (test only)" : "Demo payment"; }

    public boolean sandboxEnabled() { return sslGateway.enabled(); }

    public SslcommerzGatewayService.GatewaySession startSandbox(User buyer, String orderNumber) {
        if (!sslGateway.enabled()) throw new IllegalStateException("Sandbox gateway is not configured.");
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber).map(relations::order)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
        if (!buyer.getId().equals(order.getBuyerId())) throw new SecurityException("Not your order.");
        Payment p = paymentFor(order);
        if (p.getMethod() != PaymentMethod.SSLCOMMERZ || p.getStatus() != PaymentStatus.PENDING
                || order.getStatus() != OrderStatus.PENDING_PAYMENT)
            throw new IllegalArgumentException("Order is not awaiting an online payment.");
        var session = sslGateway.initiate(order, buyer);
        p.setGateway("SSLCOMMERZ_SANDBOX");
        p.setSessionKey(session.sessionKey());
        orderRepository.save(order);
        return session;
    }

    @Transactional
    public void confirmSandbox(String valId) {
        var verified = sslGateway.verify(valId);
        CustomerOrder order = orderRepository.findByPaymentTransactionId(verified.transactionId())
                .map(relations::order).orElseThrow(() -> new SecurityException("Unknown transaction."));
        Payment p = paymentFor(order);
        if (!"SSLCOMMERZ_SANDBOX".equals(p.getGateway())
                || !verified.transactionId().equals(p.getTransactionId())
                || p.getAmount().compareTo(verified.amount()) != 0
                || order.getTotal().compareTo(verified.amount()) != 0
                || !"BDT".equalsIgnoreCase(verified.currency()))
            throw new SecurityException("Gateway payment details do not match this order.");
        if (p.getStatus() == PaymentStatus.PAID) return;
        if (p.getStatus() != PaymentStatus.PENDING || order.getStatus() != OrderStatus.PENDING_PAYMENT)
            throw new SecurityException("Order cannot accept another payment.");
        p.setStatus(PaymentStatus.PAID);
        p.setBankTransactionId(verified.bankTransactionId());
        p.setPaidAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        notificationService.send(order.getBuyer(), NotificationType.PAYMENT, "Sandbox payment verified",
                "Test payment for order " + order.getOrderNumber() + " was confirmed.",
                "/orders/" + order.getOrderNumber());
        notificationService.send(order.getFoodCart().getOwner(), NotificationType.ORDER, "New paid order",
                "Order " + order.getOrderNumber() + " is ready for processing.",
                "/seller/orders/" + order.getOrderNumber());
    }

    @Transactional
    public CustomerOrder completeDemoPayment(User buyer, String orderNumber) {
        if (sslGateway.enabled()) throw new IllegalArgumentException("Demo payment shortcut is disabled in sandbox mode.");
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber).map(relations::order)
                .orElseThrow(() -> new IllegalArgumentException("Order was not found."));
        if (!buyer.getId().equals(order.getBuyerId())) throw new SecurityException("You cannot pay for this order.");
        Payment payment = paymentFor(order);
        if (payment.getMethod() != PaymentMethod.SSLCOMMERZ)
            throw new IllegalArgumentException("This order does not require online payment.");
        if (payment.getStatus() == PaymentStatus.PAID) return order;
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT || payment.getStatus() != PaymentStatus.PENDING)
            throw new IllegalArgumentException("This payment can no longer be completed.");

        payment.setStatus(PaymentStatus.PAID);
        payment.setGateway("DEMO");
        payment.setBankTransactionId("DEMO-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        payment.setPaidAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE));
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        notificationService.send(buyer, NotificationType.PAYMENT, "Payment successful",
                "Demo payment for order " + order.getOrderNumber() + " was confirmed.", "/orders/" + orderNumber);
        notificationService.send(order.getFoodCart().getOwner(), NotificationType.ORDER, "New paid order",
                "Order " + orderNumber + " is ready for processing.", "/seller/orders/" + orderNumber);
        return order;
    }

    /** Mutates the order's embedded payment; caller saves the parent order atomically. */
    public void requestRefundIfNeeded(CustomerOrder order) {
        Payment payment = paymentFor(order);
        if (payment.getStatus() == PaymentStatus.PAID) payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        else if (payment.getStatus() == PaymentStatus.PENDING) payment.setStatus(PaymentStatus.CANCELLED);
    }

    /** Mutates the order's embedded payment; caller saves the parent order atomically. */
    public void markCashOnDeliveryCollected(CustomerOrder order) {
        Payment payment = paymentFor(order);
        if (payment.getMethod() != PaymentMethod.CASH_ON_DELIVERY || payment.getStatus() == PaymentStatus.PAID) return;
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE));
        notificationService.send(order.getBuyer(), NotificationType.PAYMENT, "Cash payment recorded",
                "Cash on delivery for order " + order.getOrderNumber() + " was received.",
                "/orders/" + order.getOrderNumber());
    }
}
