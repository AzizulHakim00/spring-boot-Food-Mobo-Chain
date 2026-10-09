package com.safayet.foodmobochain.repository;

import com.safayet.foodmobochain.model.*;
import com.safayet.foodmobochain.model.enums.*;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(String tokenHash);
    void deleteByUserId(String userId);
}
