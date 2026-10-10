package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends MongoRepository<Discount, String> {
    Optional<Discount> findByCodeNormalized(String code);
    boolean existsByCodeNormalized(String code);
    // Older staging demo documents contain 'code' but not 'codeNormalized'.
    Optional<Discount> findByCode(String code);
    boolean existsByCode(String code);
    List<Discount> findByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByEndsAtAsc(LocalDateTime now1, LocalDateTime now2);
    List<Discount> findAllByOrderByEndsAtDesc();
}
