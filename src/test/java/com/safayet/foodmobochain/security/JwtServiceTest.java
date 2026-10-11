package com.safayet.foodmobochain.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final String testSecret = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void signsAndVerifiesAccountSubject() {
        JwtService service = new JwtService(testSecret);
        String jwt = service.generateToken("buyer@example.test");
        assertEquals("buyer@example.test", service.verifiedSubject(jwt));
        assertEquals(1800, service.expiresInSeconds());
    }

    @Test void versionedJwtCarriesSignedAccountVersion() {
        JwtService service = new JwtService(testSecret);
        String token = service.generateToken("seller@example.test", 7L);
        JwtService.VerifiedAccount signed = service.verifiedAccount(token);
        assertEquals("seller@example.test", signed.email());
        assertEquals(7L, signed.sessionVersion());
    }

    @Test void legacyJwtWithoutSessionVersionIsCompatibleWithVersionZero() {
        JwtService service = new JwtService(testSecret);
        String legacy = io.jsonwebtoken.Jwts.builder()
                .subject("buyer@example.test")
                .issuedAt(new java.util.Date())
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(300)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32]), io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();
        assertEquals(0L, service.verifiedAccount(legacy).sessionVersion());
    }

    @Test void rejectsTamperedToken() {
        JwtService service = new JwtService(testSecret);
        String token = service.generateToken("buyer@example.test");
        String bad = token.substring(0, token.lastIndexOf('.') + 1) + "invalidSignature";
        assertThrows(JwtException.class, () -> service.verifiedSubject(bad));
    }

    @Test void rejectsEmptyOrWeakSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService(""));
        assertThrows(IllegalStateException.class, () ->
                new JwtService(Base64.getEncoder().encodeToString(new byte[8])));
    }
}
