package com.azizul.foodmobochain.repository;

import com.azizul.foodmobochain.model.EmailVerificationToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByTokenHashAndUsedFalse(String tokenHash);
}
