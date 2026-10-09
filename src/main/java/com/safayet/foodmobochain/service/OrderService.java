package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.dto.CheckoutDTO;
import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import com.safayet.foodmobochain.repository.FoodCartRepository;
import com.safayet.foodmobochain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final FoodCartRepository foodCartRepository;
    private final CartService cartService;
    private final DiscountService discountService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final Relations relations;

    /** Transaction includes order creation, shopping-cart clearing, and in-app notifications. */
    @Transactional
    public CustomerOrder createOrder(User buyer, CheckoutDTO dto) {
        Cart cart = cartService.getOrCreate(buyer);
        if (cart.getItems().isEmpty() || cart.getFoodCart() == null) throw new IllegalArgumentException("Your cart is empty.");
        if (!cart.getFoodCart().isApproved() || !cart.getFoodCart().isOpen()
                || cart.getFoodCart().getOwner() == null || !cart.getFoodCart().getOwner().isEnabled())
            throw new IllegalArgumentException("This food cart is currently unavailable.");
        if (cart.getItems().stream().anyMatch(line ->
                !CartService.orderable(line.getFoodItem()) || line.getQuantity() < 1 || line.getQuantity() > 20
                        || !line.getFoodItem().getFoodCartId().equals(cart.getFoodCartId())))
            throw new IllegalArgumentException("One or more items in your cart are no longer available.");

        BigDecimal subtotal = cartService.subtotal(cart);
        DiscountService.AppliedDiscount applied = discountService.calculate(dto.getDiscountCode(), subtotal);
        BigDecimal total = subtotal.subtract(applied.amount()).add(cart.getFoodCart().getDeliveryFee());
        OrderStatus initial = dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT;
        CustomerOrder order = CustomerOrder.builder()
                .orderNumber(generateOrderNumber())
                .buyerId(buyer.getId()).buyer(buyer)
                .foodCartId(cart.getFoodCartId()).foodCart(cart.getFoodCart())
                .status(initial).subtotal(subtotal).discountAmount(applied.amount())
                .deliveryFee(cart.getFoodCart().getDeliveryFee()).total(total)
                .discountCode(applied.discount() == null ? null : applied.discount().getCode())
                .deliveryAddress(dto.getDeliveryAddress().trim()).phone(dto.getPhone().trim())
                .note(dto.getNote() == null || dto.getNote().isBlank() ? null : dto.getNote().trim())
                .build();
        List<OrderItem> lines = new ArrayList<>();
        for (CartItem line : cart.getItems()) {
            FoodItem food = line.getFoodItem();
            OrderItem snapshot = OrderItem.builder()
                    .id("orderItems:" + UUID.randomUUID())
                    .foodItemIdSnapshot(food.getId()).foodName(food.getName()).foodImage(food.getImage())
                    .unitPrice(food.getPrice()).quantity(line.getQuantity()).spiceLevel(line.getSpiceLevel())
                    .subtotal(food.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()))).build();
            snapshot.setOrder(order);
            lines.add(snapshot);
        }
        order.setItems(lines);
        paymentService.createForOrder(order, dto.getPaymentMethod());
        order.setDelivery(Delivery.builder()
                .id("deliveries:" + UUID.randomUUID())
                .status(DeliveryStatus.WAITING)
                .address(order.getDeliveryAddress()).contactNumber(order.getPhone())
                .estimatedMinutes(order.getFoodCart().getEstimatedDeliveryMinutes()).build());
        order.getDelivery().setOrder(order);
        CustomerOrder saved = orderRepository.save(order);
        cartService.clear(buyer);
        notificationService.send(buyer, NotificationType.ORDER,
                dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY ? "Order confirmed" : "Order created - payment pending",
                "Order " + saved.getOrderNumber() + " has been created.", "/orders/" + saved.getOrderNumber());
        if (dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            notificationService.send(saved.getFoodCart().getOwner(), NotificationType.ORDER,
                    "New order received", "Order " + saved.getOrderNumber() + " is ready for processing.",
                    "/seller/orders/" + saved.getOrderNumber());
        }
        return saved;
    }

    public List<CustomerOrder> buyerOrders(User buyer) {
        return relations.orders(orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.getId()));
    }

    public CustomerOrder buyerOrder(User buyer, String orderNumber) {
        CustomerOrder order = getByNumber(orderNumber);
        if (!buyer.getId().equals(order.getBuyerId())) throw new SecurityException("You cannot access this order.");
        return order;
    }

    public List<CustomerOrder> sellerOrders(User seller) {
        FoodCart cart = sellerCart(seller);
        return relations.orders(orderRepository.findByFoodCartIdOrderByCreatedAtDesc(cart.getId()));
    }

    public CustomerOrder sellerOrder(User seller, String orderNumber) {
        CustomerOrder order = getByNumber(orderNumber);
        if (!order.getFoodCart().getOwnerId().equals(seller.getId())) throw new SecurityException("You cannot access this order.");
        return order;
    }

    public CustomerOrder getByNumber(String number) {
        return orderRepository.findByOrderNumber(number).map(relations::order)
                .orElseThrow(() -> new IllegalArgumentException("Order was not found."));
    }

    public List<CustomerOrder> recentOrders() {
        return relations.orders(orderRepository.findTop20ByOrderByCreatedAtDesc());
    }

    public List<Delivery> recentDeliveries() {
        return orderRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(relations::order).map(CustomerOrder::getDelivery).filter(Objects::nonNull).toList();
    }

    public Payment paymentFor(CustomerOrder order) { return paymentService.paymentFor(order); }

    public Delivery deliveryFor(CustomerOrder order) {
        if (order.getDelivery() == null) throw new IllegalArgumentException("Delivery record was not found.");
        return order.getDelivery();
    }

    @Transactional
    public void cancelByBuyer(User buyer, String number) {
        CustomerOrder order = buyerOrder(buyer, number);
        if (!canBuyerCancel(order)) throw new IllegalArgumentException("This order can no longer be cancelled.");
        cancel(order);
        notificationService.send(order.getFoodCart().getOwner(), NotificationType.ORDER,
                "Order cancelled", "Order " + number + " was cancelled by the buyer.", "/seller/orders/" + number);
    }

    @Transactional
    public void updateStatusBySeller(User seller, String number, OrderStatus next) {
        CustomerOrder order = sellerOrder(seller, number);
        if (!allowedNext(order.getStatus()).contains(next)) throw new IllegalArgumentException("Invalid order-status transition.");
        order.setStatus(next);
        Delivery delivery = deliveryFor(order);
        switch (next) {
            case ACCEPTED, COOKING, READY -> delivery.setStatus(DeliveryStatus.PREPARING);
            case ON_THE_WAY -> {
                delivery.setStatus(DeliveryStatus.ON_THE_WAY);
                delivery.setDispatchedAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE));
            }
            case DELIVERED -> {
                delivery.setStatus(DeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE));
                paymentService.markCashOnDeliveryCollected(order);
            }
            case CANCELLED -> {
                delivery.setStatus(DeliveryStatus.CANCELLED);
                paymentService.requestRefundIfNeeded(order);
            }
            default -> { }
        }
        orderRepository.save(order);
        String friendly = next.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        notificationService.send(order.getBuyer(), NotificationType.ORDER,
                "Order update", "Order " + number + " is now " + friendly + ".", "/orders/" + number);
    }

    @Transactional
    public void cancelByAdmin(String number) {
        CustomerOrder order = getByNumber(number);
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED)
            throw new IllegalArgumentException("This order is already in a final state.");
        cancel(order);
        notificationService.send(order.getBuyer(), NotificationType.ORDER,
                "Order cancelled by support", "Order " + number + " was cancelled by an administrator.", "/orders/" + number);
        notificationService.send(order.getFoodCart().getOwner(), NotificationType.ORDER,
                "Order cancelled by support", "Order " + number + " was cancelled by an administrator.", "/seller/orders/" + number);
    }

    private void cancel(CustomerOrder order) {
        order.setStatus(OrderStatus.CANCELLED);
        deliveryFor(order).setStatus(DeliveryStatus.CANCELLED);
        paymentService.requestRefundIfNeeded(order);
        orderRepository.save(order);
    }

    public Set<OrderStatus> allowedNext(OrderStatus current) {
        return switch (current) {
            case CONFIRMED -> EnumSet.of(OrderStatus.ACCEPTED);
            case ACCEPTED -> EnumSet.of(OrderStatus.COOKING, OrderStatus.CANCELLED);
            case COOKING -> EnumSet.of(OrderStatus.READY);
            case READY -> EnumSet.of(OrderStatus.ON_THE_WAY);
            case ON_THE_WAY -> EnumSet.of(OrderStatus.DELIVERED);
            default -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canBuyerCancel(CustomerOrder order) {
        return order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.CONFIRMED
                || order.getStatus() == OrderStatus.ACCEPTED;
    }

    public int progressStep(OrderStatus status) {
        return switch (status) {
            case PENDING_PAYMENT -> 0;
            case CONFIRMED -> 1;
            case ACCEPTED -> 2;
            case COOKING -> 3;
            case READY -> 4;
            case ON_THE_WAY -> 5;
            case DELIVERED -> 6;
            case CANCELLED -> -1;
        };
    }

    private FoodCart sellerCart(User seller) {
        return foodCartRepository.findByOwnerId(seller.getId())
                .orElseThrow(() -> new IllegalArgumentException("Seller food cart was not found."));
    }

    private String generateOrderNumber() {
        String prefix = "FMC" + LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).format(DateTimeFormatter.ofPattern("yyMMdd"));
        String candidate;
        do {
            candidate = prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (orderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
