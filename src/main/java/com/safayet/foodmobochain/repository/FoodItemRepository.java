package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends MongoRepository<FoodItem, String> {
    List<FoodItem> findByFoodCartIdAndArchivedFalseOrderByNameAsc(String foodCartId);
    List<FoodItem> findAllByOrderByNameAsc();
    List<FoodItem> findByFeaturedTrueAndAvailableTrueAndArchivedFalseOrderByCreatedAtDesc();
    long countByFoodCartIdAndArchivedFalse(String cartId);
    long countByAvailableTrue();
}
