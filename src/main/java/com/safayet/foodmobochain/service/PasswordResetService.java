package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.model.PasswordResetToken;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.repository.PasswordResetTokenRepository;
import com.safayet.foodmobochain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @org.springframework.beans.factory.annotation.Value("${app.password-reset.log-demo-link:false}")
    private boolean logDemoLinks;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailNormalized(email.trim().toLowerCase(java.util.Locale.ROOT)).ifPresent(user -> {
            tokenRepository.deleteByUserId(user.getId());
            String rawToken = generateToken();
            tokenRepository.save(PasswordResetToken.builder()
                    .user(user).userId(user.getId())
                    .tokenHash(hash(rawToken))
                    .expiresAt(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE).plusMinutes(30))
                    .used(false)
                    .build());

            // Localhost student project: show the demo reset URL in the application console.
            if (logDemoLinks) {
                log.info("DEVELOPMENT ONLY reset link for {}: http://localhost:8080/reset-password?token={}",
                        user.getEmail(), rawToken);
            }
        });
    }

    public boolean isTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return tokenRepository.findByTokenHashAndUsedFalse(hash(rawToken))
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE)))
                .isPresent();
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHashAndUsedFalse(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or has already been used."));
        if (token.getExpiresAt().isBefore(LocalDateTime.now(com.safayet.foodmobochain.config.MongoConfig.APP_ZONE))) {
            throw new IllegalArgumentException("This reset link has expired. Request a new one.");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User account was not found."));
        user.setPassword(passwordEncoder.encode(newPassword));
        token.setUsed(true);
        userRepository.save(user);
        tokenRepository.save(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
