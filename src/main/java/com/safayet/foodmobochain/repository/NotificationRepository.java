package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findTop8ByUserIdOrderByCreatedAtDesc(String userId);
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndReadFalse(String userId);
}
