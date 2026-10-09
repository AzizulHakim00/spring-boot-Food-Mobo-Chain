package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FavoriteCartRepository favoriteCartRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodCartRepository foodCartRepository;
    private final Relations relations;

    @Transactional
    public boolean toggleFood(User user, String foodId) {
        FoodItem food = foodItemRepository.findById(foodId).map(relations::food)
                .orElseThrow(() -> new IllegalArgumentException("Food item was not found."));
        if (!isPublicFood(food)) {
            throw new IllegalArgumentException("This food item is not available to save right now.");
        }
        var existing = favoriteFoodRepository.findByUserIdAndFoodItemId(user.getId(), food.getId());
        if (existing.isPresent()) {
            favoriteFoodRepository.delete(existing.get());
            return false;
        }
        favoriteFoodRepository.save(FavoriteFood.builder().user(user).userId(user.getId()).foodItem(food).foodItemId(food.getId()).build());
        return true;
    }

    @Transactional
    public boolean toggleCart(User user, String cartId) {
        FoodCart cart = foodCartRepository.findById(cartId).map(relations::cart)
                .orElseThrow(() -> new IllegalArgumentException("Food cart was not found."));
        if (!cart.isApproved() || !cart.getOwner().isEnabled()) {
            throw new IllegalArgumentException("This food cart is not available to save right now.");
        }
        var existing = favoriteCartRepository.findByUserIdAndFoodCartId(user.getId(), cart.getId());
        if (existing.isPresent()) {
            favoriteCartRepository.delete(existing.get());
            return false;
        }
        favoriteCartRepository.save(FavoriteCart.builder().user(user).userId(user.getId()).foodCart(cart).foodCartId(cart.getId()).build());
        return true;
    }

    public List<FavoriteFood> foods(User user) {
        return favoriteFoodRepository.findByUserId(user.getId()).stream().map(relations::favorite)
                .filter(favorite -> isPublicFood(favorite.getFoodItem()))
                .toList();
    }

    public List<FavoriteCart> carts(User user) {
        return favoriteCartRepository.findByUserId(user.getId()).stream().map(relations::favorite)
                .filter(favorite -> favorite.getFoodCart() != null && favorite.getFoodCart().getOwner() != null
                        && favorite.getFoodCart().isApproved() && favorite.getFoodCart().getOwner().isEnabled())
                .toList();
    }

    public boolean isFoodFavorite(User user, FoodItem food) {
        return favoriteFoodRepository.existsByUserIdAndFoodItemId(user.getId(), food.getId());
    }

    public boolean isCartFavorite(User user, FoodCart cart) {
        return favoriteCartRepository.existsByUserIdAndFoodCartId(user.getId(), cart.getId());
    }

    private boolean isPublicFood(FoodItem food) {
        return food != null && food.getCategory() != null && food.getFoodCart() != null
                && food.getFoodCart().getOwner() != null && food.isAvailable()
                && !food.isArchived()
                && food.getCategory().isActive()
                && food.getFoodCart().isApproved()
                && food.getFoodCart().getOwner().isEnabled();
    }
}
