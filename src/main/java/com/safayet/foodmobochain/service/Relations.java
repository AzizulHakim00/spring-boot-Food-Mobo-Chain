package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Explicit hydration for Thymeleaf view compatibility. No MongoDB DBRefs are used. */
@Component
@RequiredArgsConstructor
public class Relations {
    private final UserRepository users;
    private final CategoryRepository categories;
    private final FoodCartRepository carts;
    private final FoodItemRepository foods;

    public FoodCart cart(FoodCart cart) {
        if (cart == null) return null;
        if (cart.getOwnerId() != null) {
            users.findById(cart.getOwnerId()).ifPresent(cart::setOwner);
        }
        return cart;
    }

    public FoodItem food(FoodItem item) {
        if (item == null) return null;
        if (item.getFoodCartId() != null) carts.findById(item.getFoodCartId()).map(this::cart).ifPresent(item::setFoodCart);
        if (item.getCategoryId() != null) categories.findById(item.getCategoryId()).ifPresent(item::setCategory);
        return item;
    }

    public Cart shopping(Cart cart) {
        if (cart == null) return null;
        if (cart.getBuyerId() != null) users.findById(cart.getBuyerId()).ifPresent(cart::setBuyer);
        if (cart.getFoodCartId() != null) carts.findById(cart.getFoodCartId()).map(this::cart).ifPresent(cart::setFoodCart);
        for (CartItem line : cart.getItems()) {
            if (line.getFoodItemId() != null) foods.findById(line.getFoodItemId()).map(this::food).ifPresent(line::setFoodItem);
        }
        return cart;
    }

    public CustomerOrder order(CustomerOrder order) {
        if (order == null) return null;
        if (order.getBuyerId() != null) users.findById(order.getBuyerId()).ifPresent(order::setBuyer);
        if (order.getFoodCartId() != null) carts.findById(order.getFoodCartId()).map(this::cart).ifPresent(order::setFoodCart);
        for (OrderItem item : order.getItems()) item.setOrder(order);
        if (order.getPayment() != null) order.getPayment().setOrder(order);
        if (order.getDelivery() != null) order.getDelivery().setOrder(order);
        return order;
    }

    public Review review(Review review) {
        if (review == null) return null;
        if (review.getBuyerId() != null) users.findById(review.getBuyerId()).ifPresent(review::setBuyer);
        if (review.getFoodItemId() != null) foods.findById(review.getFoodItemId()).map(this::food).ifPresent(review::setFoodItem);
        if (review.getFoodCartId() != null) carts.findById(review.getFoodCartId()).map(this::cart).ifPresent(review::setFoodCart);
        return review;
    }

    public FavoriteFood favorite(FavoriteFood favorite) {
        if (favorite.getUserId() != null) users.findById(favorite.getUserId()).ifPresent(favorite::setUser);
        if (favorite.getFoodItemId() != null) foods.findById(favorite.getFoodItemId()).map(this::food).ifPresent(favorite::setFoodItem);
        return favorite;
    }

    public FavoriteCart favorite(FavoriteCart favorite) {
        if (favorite.getUserId() != null) users.findById(favorite.getUserId()).ifPresent(favorite::setUser);
        if (favorite.getFoodCartId() != null) carts.findById(favorite.getFoodCartId()).map(this::cart).ifPresent(favorite::setFoodCart);
        return favorite;
    }

    public List<FoodCart> carts(List<FoodCart> list) {
        List<String> ownerIds = list.stream().map(FoodCart::getOwnerId).filter(Objects::nonNull).distinct().toList();
        Map<String, User> owners = users.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (FoodCart cart : list) {
            if (owners.containsKey(cart.getOwnerId())) cart.setOwner(owners.get(cart.getOwnerId()));
        }
        return list;
    }
    public List<FoodItem> foods(List<FoodItem> list) {
        List<String> cartIds = list.stream().map(FoodItem::getFoodCartId).filter(Objects::nonNull).distinct().toList();
        List<String> categoryIds = list.stream().map(FoodItem::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<String, FoodCart> foodCarts = carts(carts.findAllById(cartIds)).stream()
                .collect(Collectors.toMap(FoodCart::getId, Function.identity()));
        Map<String, Category> cats = categories.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (FoodItem item : list) {
            if (foodCarts.containsKey(item.getFoodCartId())) item.setFoodCart(foodCarts.get(item.getFoodCartId()));
            if (cats.containsKey(item.getCategoryId())) item.setCategory(cats.get(item.getCategoryId()));
        }
        return list;
    }
    public List<CustomerOrder> orders(List<CustomerOrder> list) {return list.stream().map(this::order).toList();}
    public List<Review> reviews(List<Review> list) {return list.stream().map(this::review).toList();}
}
