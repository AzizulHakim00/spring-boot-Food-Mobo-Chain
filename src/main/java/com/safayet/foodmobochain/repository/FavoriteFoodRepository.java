package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FavoriteFoodRepository extends MongoRepository<FavoriteFood, String> {
    Optional<FavoriteFood> findByUserIdAndFoodItemId(String userId, String foodId);
    boolean existsByUserIdAndFoodItemId(String userId, String foodId);
    List<FavoriteFood> findByUserId(String userId);
}
