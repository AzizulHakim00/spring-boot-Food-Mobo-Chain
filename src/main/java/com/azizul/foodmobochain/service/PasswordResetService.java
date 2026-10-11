package com.azizul.foodmobochain.service;

import com.azizul.foodmobochain.config.MongoConfig;
import com.azizul.foodmobochain.model.PasswordResetToken;
import com.azizul.foodmobochain.model.User;
import com.azizul.foodmobochain.repository.PasswordResetTokenRepository;
import com.azizul.foodmobochain.repository.UserRepository;
import com.azizul.foodmobochain.security.AccountTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** Single-use password links sent privately through AgentMail, never printed to logs. */
@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgentMailService mail;

    /**
     * Unknown address and rate-limited address produce the same public response.
     * Sending inside the MongoDB transaction rolls token changes back if delivery fails.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.length() > 150) return;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        // Demo *.local addresses cannot receive Internet email; keep a neutral response.
        if (normalized.endsWith(".local")) return;
        mail.requireConfigured();
        userRepository.findByEmailNormalized(normalized).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now(MongoConfig.APP_ZONE);
            if (tokenRepository.findByUserId(user.getId())
                    .map(token -> token.getLastSentAt() != null
                            && token.getLastSentAt().plusSeconds(90).isAfter(now))
                    .orElse(false)) return;

            String rawToken = AccountTokens.create();
            tokenRepository.deleteByUserId(user.getId());
            tokenRepository.save(PasswordResetToken.builder()
                    .user(user).userId(user.getId())
                    .tokenHash(AccountTokens.hash(rawToken))
                    .expiresAt(now.plusMinutes(30))
                    .lastSentAt(now).used(false)
                    .build());
            mail.sendPasswordReset(user.getEmail(), rawToken);
        });
    }

    public boolean isTokenValid(String rawToken) {
        if (!AccountTokens.validShape(rawToken)) return false;
        LocalDateTime now = LocalDateTime.now(MongoConfig.APP_ZONE);
        return tokenRepository.findByTokenHashAndUsedFalse(AccountTokens.hash(rawToken))
                .filter(token -> token.getExpiresAt() != null && token.getExpiresAt().isAfter(now))
                .isPresent();
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (!AccountTokens.validShape(rawToken))
            throw new IllegalArgumentException("This reset link is invalid or has already been used.");
        PasswordResetToken token = tokenRepository.findByTokenHashAndUsedFalse(AccountTokens.hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has already been used."));
        if (token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(LocalDateTime.now(MongoConfig.APP_ZONE))) {
            throw new IllegalArgumentException("This reset link has expired. Request a new one.");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setSessionVersion(user.getSessionVersion() + 1L);
        token.setUsed(true);
        userRepository.save(user);
        tokenRepository.save(token);
    }
}
