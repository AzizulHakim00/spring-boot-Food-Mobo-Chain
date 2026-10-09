package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<CustomerOrder, String> {
    Optional<CustomerOrder> findByOrderNumber(String orderNumber);
    boolean existsByOrderNumber(String orderNumber);
    List<CustomerOrder> findByBuyerIdOrderByCreatedAtDesc(String buyerId);
    List<CustomerOrder> findByFoodCartIdOrderByCreatedAtDesc(String foodCartId);
    List<CustomerOrder> findTop20ByOrderByCreatedAtDesc();
    List<CustomerOrder> findTop50ByOrderByCreatedAtDesc();
    long countByFoodCartId(String foodCartId);
    long countByStatus(OrderStatus status);
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);
    long countByFoodCartIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(String foodCartId, LocalDateTime start, LocalDateTime end);
    long countByFoodCartIdAndCreatedAtGreaterThanEqual(String foodCartId, LocalDateTime from);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    long countByBuyerIdAndStatusAndItemsFoodItemIdSnapshot(String buyerId, OrderStatus status, String foodItemId);
    long countByBuyerIdAndFoodCartIdAndStatus(String buyerId, String foodCartId, OrderStatus status);
    long countByPaymentStatus(PaymentStatus status);
    List<CustomerOrder> findByStatus(OrderStatus status);
    List<CustomerOrder> findByFoodCartIdAndStatus(String foodCartId, OrderStatus status);
    List<CustomerOrder> findByPaymentStatus(PaymentStatus status);
}
