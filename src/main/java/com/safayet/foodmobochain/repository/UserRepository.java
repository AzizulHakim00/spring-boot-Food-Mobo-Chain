package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmailNormalized(String emailNormalized);
    boolean existsByEmailNormalized(String emailNormalized);
    long countByRole(Role role);
    List<User> findAllByOrderByCreatedAtDesc();
    List<User> findByEnabledTrue();
}
