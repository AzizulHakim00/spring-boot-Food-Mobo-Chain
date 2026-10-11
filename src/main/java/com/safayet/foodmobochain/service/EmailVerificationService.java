package com.safayet.foodmobochain.service;

import com.safayet.foodmobochain.config.MongoConfig;
import com.safayet.foodmobochain.model.EmailVerificationToken;
import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.repository.EmailVerificationTokenRepository;
import com.safayet.foodmobochain.repository.UserRepository;
import com.safayet.foodmobochain.security.AccountTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** New accounts are inaccessible until a one-time, emailed proof is confirmed. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokens;
    private final UserRepository users;
    private final ResendMailService mail;

    private static final int COOLDOWN_SECONDS = 90;

    public boolean isMailConfigured() { return mail.isConfigured(); }
    public void requireMailConfigured() { mail.requireConfigured(); }

    /**
     * Only one active token per account. A failed provider call rolls back the
     * token change; registration already committed and can be retried by email.
     */
    @Transactional
    public void sendForUser(User user) {
        mail.requireConfigured();
        if (!Boolean.FALSE.equals(user.getEmailVerified())) return;
        LocalDateTime now = LocalDateTime.now(MongoConfig.APP_ZONE);
        String key = "verification:" + user.getId();
        EmailVerificationToken record = tokens.findById(key)
                .orElseGet(() -> EmailVerificationToken.builder()
                        .id(key).userId(user.getId()).build());
        if (record.getLastSentAt() != null
                && record.getLastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(now)) return;
        String rawToken = AccountTokens.create();
        record.setUsed(false);
        record.setTokenHash(AccountTokens.hash(rawToken));
        record.setLastSentAt(now);
        record.setExpiresAt(now.plusHours(24));
        tokens.save(record);
        mail.sendVerification(user.getEmail(), rawToken);
    }

    /** The response is the same for unknown, verified and rate-limited emails. */
    @Transactional
    public void resend(String address) {
        mail.requireConfigured();
        if (address == null || address.length() > 150) return;
        users.findByEmailNormalized(address.trim().toLowerCase(Locale.ROOT))
                .filter(user -> Boolean.FALSE.equals(user.getEmailVerified()))
                .ifPresent(this::sendForUser);
    }

    public boolean isValid(String rawToken) {
        if (!AccountTokens.validShape(rawToken)) return false;
        LocalDateTime now = LocalDateTime.now(MongoConfig.APP_ZONE);
        return tokens.findByTokenHashAndUsedFalse(AccountTokens.hash(rawToken))
                .filter(token -> token.getExpiresAt() != null && token.getExpiresAt().isAfter(now))
                .flatMap(token -> users.findById(token.getUserId()))
                .filter(user -> Boolean.FALSE.equals(user.getEmailVerified()))
                .isPresent();
    }

    /** Called only on a CSRF-protected POST to avoid email-link scanners activating accounts. */
    @Transactional
    public void confirm(String rawToken) {
        if (!AccountTokens.validShape(rawToken))
            throw new IllegalArgumentException("Verification link is invalid or expired.");
        EmailVerificationToken token = tokens.findByTokenHashAndUsedFalse(AccountTokens.hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired."));
        if (token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(LocalDateTime.now(MongoConfig.APP_ZONE)))
            throw new IllegalArgumentException("Verification link is invalid or expired.");
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired."));
        if (!Boolean.FALSE.equals(user.getEmailVerified()))
            throw new IllegalArgumentException("This email has already been verified.");
        user.setEmailVerified(true);
        user.setSessionVersion(user.getSessionVersion() + 1L);
        token.setUsed(true);
        tokens.save(token);
        users.save(user);
    }
}
