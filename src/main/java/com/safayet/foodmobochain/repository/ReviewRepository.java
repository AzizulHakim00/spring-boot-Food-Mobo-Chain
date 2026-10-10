package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends MongoRepository<Review, String> {
    boolean existsByBuyerIdAndFoodItemId(String buyerId, String foodItemId);
    boolean existsByBuyerIdAndFoodCartId(String buyerId, String cartId);
    List<Review> findByFoodItemIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(String foodId);
    List<Review> findByFoodCartIdAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(String cartId);
    List<Review> findByApprovedFalseAndHiddenFalseOrderByCreatedAtAsc();
    List<Review> findAllByOrderByCreatedAtDesc();
}
