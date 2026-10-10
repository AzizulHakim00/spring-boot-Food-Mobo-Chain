package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FavoriteCartRepository extends MongoRepository<FavoriteCart, String> {
    Optional<FavoriteCart> findByUserIdAndFoodCartId(String userId, String cartId);
    boolean existsByUserIdAndFoodCartId(String userId, String cartId);
    List<FavoriteCart> findByUserId(String userId);
}
