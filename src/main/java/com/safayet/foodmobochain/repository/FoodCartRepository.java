package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FoodCartRepository extends MongoRepository<FoodCart, String> {
    Optional<FoodCart> findBySlug(String slug);
    Optional<FoodCart> findByOwnerId(String ownerId);
    boolean existsBySlug(String slug);
    List<FoodCart> findByApprovedTrueOrderByNameAsc();
    List<FoodCart> findAllByOrderByCreatedAtDesc();
    long countByApprovedTrue();
    long countByApprovedFalse();
}
