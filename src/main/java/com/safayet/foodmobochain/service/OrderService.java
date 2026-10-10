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

    /**
     * One checkout creates a distinct order for each seller.
     * All seller orders, payment snapshots, notifications and clearing the shared basket
     * run in a single MongoDB transaction, so a failed checkout cannot create half an order.
     */
    @Transactional
    public List<CustomerOrder> createOrders(User buyer, CheckoutDTO dto) {
        Cart cart = cartService.getOrCreate(buyer);
        List<CartService.VendorGroup> groups = cartService.vendorGroups(cart);
        if (groups.isEmpty()) throw new IllegalArgumentException("Your cart is empty.");

        // Validate every vendor and line before writing any order.
        for (CartService.VendorGroup group : groups) {
            if (group.foodCart() == null || !group.foodCart().isApproved()
                    || !group.foodCart().isOpen() || group.foodCart().getOwner() == null
                    || !group.foodCart().getOwner().isEnabled()) {
                throw new IllegalArgumentException("One of your food carts is currently unavailable.");
            }
            if (group.items().stream().anyMatch(line ->
                    line.getFoodItem() == null || !CartService.orderable(line.getFoodItem())
                            || line.getQuantity() < 1 || line.getQuantity() > 20
                            || !Objects.equals(line.getFoodItem().getFoodCartId(), group.foodCart().getId()))) {
                throw new IllegalArgumentException("One or more items in your cart are no longer available.");
            }
        }

        Map<String, BigDecimal> vendorSubtotals = new LinkedHashMap<>();
        for (CartService.VendorGroup group : groups) {
            vendorSubtotals.put(group.foodCart().getId(), group.subtotal());
        }
        BigDecimal subtotal = vendorSubtotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        DiscountService.AppliedDiscount applied = discountService.calculate(dto.getDiscountCode(), subtotal);
        Map<String, BigDecimal> discountShares = allocateDiscountShares(vendorSubtotals, applied.amount());

        OrderStatus initial = dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT;
        List<CustomerOrder> created = new ArrayList<>();
        for (CartService.VendorGroup group : groups) {
            FoodCart vendor = group.foodCart();
            BigDecimal vendorDiscount = discountShares.get(vendor.getId());
            CustomerOrder order = CustomerOrder.builder()
                    .orderNumber(generateOrderNumber())
                    .buyerId(buyer.getId()).buyer(buyer)
                    .foodCartId(vendor.getId()).foodCart(vendor)
                    .status(initial).subtotal(group.subtotal()).discountAmount(vendorDiscount)
                    .deliveryFee(vendor.getDeliveryFee())
                    .total(group.subtotal().subtract(vendorDiscount).add(vendor.getDeliveryFee()))
                    .discountCode(applied.discount() == null ? null : applied.discount().getCode())
                    .deliveryAddress(dto.getDeliveryAddress().trim()).phone(dto.getPhone().trim())
                    .note(dto.getNote() == null || dto.getNote().isBlank() ? null : dto.getNote().trim())
                    .build();

            List<OrderItem> lines = new ArrayList<>();
            for (CartItem line : group.items()) {
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
                    .estimatedMinutes(vendor.getEstimatedDeliveryMinutes()).build());
            order.getDelivery().setOrder(order);

            CustomerOrder saved = orderRepository.save(order);
            created.add(saved);
            notificationService.send(buyer, NotificationType.ORDER,
                    dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY
                            ? "Order confirmed" : "Order created - payment pending",
                    "Order " + saved.getOrderNumber() + " from " + vendor.getName() + " was created.",
                    "/orders/" + saved.getOrderNumber());
            if (dto.getPaymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
                notificationService.send(vendor.getOwner(), NotificationType.ORDER,
                        "New order received", "Order " + saved.getOrderNumber() + " is ready for processing.",
                        "/seller/orders/" + saved.getOrderNumber());
            }
        }
        cartService.clear(buyer);
        return List.copyOf(created);
    }

    /**
     * Divide a global promo exactly once across seller orders using the largest-remainder
     * method. Allocation never exceeds a seller subtotal; rounded shares sum to the
     * original discount, even when the order has tiny prices.
     */
    static Map<String, BigDecimal> allocateDiscountShares(
            Map<String, BigDecimal> vendorSubtotals, BigDecimal discount) {
        BigDecimal total = vendorSubtotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (vendorSubtotals.isEmpty() || total.signum() <= 0 || discount == null
                || discount.signum() < 0 || discount.compareTo(total) > 0) {
            throw new IllegalArgumentException("Invalid checkout discount allocation.");
        }
        BigDecimal amount = discount.setScale(2, java.math.RoundingMode.UNNECESSARY);
        BigDecimal cent = new BigDecimal("0.01");
        Map<String, BigDecimal> shares = new LinkedHashMap<>();
        Map<String, BigDecimal> remainders = new HashMap<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : vendorSubtotals.entrySet()) {
            BigDecimal ideal = amount.multiply(entry.getValue())
                    .divide(total, 12, java.math.RoundingMode.HALF_UP);
            BigDecimal base = ideal.setScale(2, java.math.RoundingMode.DOWN);
            shares.put(entry.getKey(), base);
            remainders.put(entry.getKey(), ideal.subtract(base));
            allocated = allocated.add(base);
        }
        long pennies = amount.subtract(allocated).movePointRight(2).longValueExact();
        List<String> keys = new ArrayList<>(shares.keySet());
        keys.sort(Comparator.comparing((String key) -> remainders.get(key)).reversed());
        for (long i = 0; i < pennies; i++) {
            String key = keys.get((int) (i % keys.size()));
            BigDecimal candidate = shares.get(key).add(cent);
            if (candidate.compareTo(vendorSubtotals.get(key)) > 0) {
                throw new IllegalArgumentException("A discounted vendor total cannot be negative.");
            }
            shares.put(key, candidate);
        }
        return Collections.unmodifiableMap(shares);
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
