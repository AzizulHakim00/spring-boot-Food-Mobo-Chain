package com.safayet.foodmobochain.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** JWT holds only an account subject. The database remains authoritative for enabled/role/password state. */
@Service
public class JwtService {
    private static final Duration LIFETIME = Duration.ofMinutes(30);
    private final SecretKey key;

    public JwtService(@Value("${app.jwt.secret-base64:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be set to a random Base64-encoded 32+ byte key");
        }
        try {
            byte[] secret = Decoders.BASE64.decode(base64Key.trim());
            if (secret.length < 32) throw new IllegalArgumentException("JWT key must have at least 256 bits");
            this.key = Keys.hmacShaKeyFor(secret);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Invalid JWT_SECRET_BASE64: supply a Base64-encoded 32+ byte key", e);
        }
    }

    public String generateToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(LIFETIME)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Throws a JwtException for expired, altered, or otherwise invalid tokens. */
    public String verifiedSubject(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String email = claims.getSubject();
        if (email == null || email.isBlank()) throw new IllegalArgumentException("JWT missing subject");
        return email;
    }

    public long expiresInSeconds() {
        return LIFETIME.toSeconds();
    }
}
