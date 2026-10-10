package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.SpiceLevel;
import com.safayet.foodmobochain.repository.CartRepository;
import com.safayet.foodmobochain.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final FoodItemRepository foodItemRepository;
    private final Relations relations;

    @Transactional
    public Cart getOrCreate(User buyer) {
        return relations.shopping(cartRepository.findByBuyerId(buyer.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder()
                        .buyerId(buyer.getId()).items(new ArrayList<>()).build())));
    }

    public Optional<Cart> find(User buyer) {
        return cartRepository.findByBuyerId(buyer.getId()).map(relations::shopping);
    }

    @Transactional
    public void add(User buyer, String foodId, int quantity, SpiceLevel spiceLevel) {
        if (quantity < 1 || quantity > 20) throw new IllegalArgumentException("Quantity must be between 1 and 20.");
        FoodItem food = foodItemRepository.findById(foodId).map(relations::food)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (!orderable(food)) throw new IllegalArgumentException("This food item is not available right now.");

        Cart cart = getOrCreate(buyer);
        CartItem item = cart.getItems().stream().filter(line -> foodId.equals(line.getFoodItemId())).findFirst()
                .orElseGet(() -> {
                    CartItem added = CartItem.builder().id("cartItems:" + UUID.randomUUID())
                            .foodItemId(foodId).quantity(0).build();
                    added.setFoodItem(food);
                    cart.getItems().add(added);
                    return added;
                });
        if (item.getQuantity() + quantity > 20) throw new IllegalArgumentException("Maximum quantity per item is 20.");
        item.setQuantity(item.getQuantity() + quantity);
        item.setSpiceLevel(food.isSpicySupported() && spiceLevel != null ? spiceLevel : SpiceLevel.REGULAR);
        syncLegacySingleVendor(cart);
        cartRepository.save(cart);
    }

    @Transactional
    public void update(User buyer, String itemId, int quantity, SpiceLevel spiceLevel) {
        if (quantity < 1 || quantity > 20) throw new IllegalArgumentException("Quantity must be between 1 and 20. Use Remove to delete an item.");
        Cart cart = getOrCreate(buyer);
        CartItem item = cart.getItems().stream().filter(line -> itemId.equals(line.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item was not found."));
        if (item.getFoodItem() == null || !orderable(item.getFoodItem()))
            throw new IllegalArgumentException("This food item is no longer available.");
        item.setQuantity(quantity);
        item.setSpiceLevel(item.getFoodItem().isSpicySupported() && spiceLevel != null ? spiceLevel : SpiceLevel.REGULAR);
        cartRepository.save(cart);
    }

    @Transactional
    public void remove(User buyer, String itemId) {
        Cart cart = getOrCreate(buyer);
        if (!cart.getItems().removeIf(item -> itemId.equals(item.getId()))) {
            throw new IllegalArgumentException("Cart item was not found.");
        }
        syncLegacySingleVendor(cart);
        cartRepository.save(cart);
    }

    @Transactional
    public void clear(User buyer) {
        Cart cart = getOrCreate(buyer);
        cart.getItems().clear();
        cart.setFoodCart(null);
        cartRepository.save(cart);
    }

    /** Items may belong to multiple food carts; the legacy cart.foodCartId is set only for single-vendor baskets. */
    private static void syncLegacySingleVendor(Cart cart) {
        if (cart.getItems().isEmpty()) {
            cart.setFoodCart(null);
            return;
        }
        FoodItem first = cart.getItems().getFirst().getFoodItem();
        if (first == null || first.getFoodCart() == null || cart.getItems().stream().anyMatch(item ->
                item.getFoodItem() == null
                        || !Objects.equals(first.getFoodCartId(), item.getFoodItem().getFoodCartId()))) {
            cart.setFoodCart(null);
        } else {
            cart.setFoodCart(first.getFoodCart());
        }
    }

    /** Deterministic grouping used for cart display, delivery fees and seller-specific checkout. */
    public List<VendorGroup> vendorGroups(Cart cart) {
        Map<String, List<CartItem>> itemsBySeller = new LinkedHashMap<>();
        Map<String, FoodCart> vendors = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            FoodItem food = item.getFoodItem();
            if (food == null || food.getFoodCart() == null) {
                throw new IllegalArgumentException("An item is no longer available. Please remove it from your cart.");
            }
            itemsBySeller.computeIfAbsent(food.getFoodCartId(), ignored -> new ArrayList<>()).add(item);
            vendors.put(food.getFoodCartId(), food.getFoodCart());
        }
        List<VendorGroup> groups = new ArrayList<>();
        itemsBySeller.forEach((sellerId, lines) -> {
            BigDecimal subtotal = lines.stream()
                    .map(line -> line.getFoodItem().getPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            groups.add(new VendorGroup(vendors.get(sellerId), List.copyOf(lines), subtotal));
        });
        return List.copyOf(groups);
    }

    public BigDecimal deliveryTotal(Cart cart) {
        return vendorGroups(cart).stream()
                .map(group -> group.foodCart().getDeliveryFee())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record VendorGroup(FoodCart foodCart, List<CartItem> items, BigDecimal subtotal) {}

    public BigDecimal subtotal(Cart cart) {
        return cart.getItems().stream().map(line -> {
            if (line.getFoodItem() == null) throw new IllegalArgumentException("An item no longer exists. Please remove it.");
            return line.getFoodItem().getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int itemCount(User buyer) {
        return cartRepository.findByBuyerId(buyer.getId())
                .map(cart -> cart.getItems().stream().mapToInt(CartItem::getQuantity).sum()).orElse(0);
    }

    public static boolean orderable(FoodItem food) {
        return food != null && food.isAvailable() && !food.isArchived()
                && food.getCategory() != null && food.getCategory().isActive()
                && food.getFoodCart() != null && food.getFoodCart().isApproved()
                && food.getFoodCart().isOpen()
                && food.getFoodCart().getOwner() != null && food.getFoodCart().getOwner().isEnabled();
    }
}
