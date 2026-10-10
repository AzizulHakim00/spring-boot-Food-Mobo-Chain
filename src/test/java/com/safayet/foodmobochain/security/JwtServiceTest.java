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
