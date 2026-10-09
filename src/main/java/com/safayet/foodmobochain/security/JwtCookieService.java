package com.safayet.foodmobochain.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class JwtCookieService {
    public static final String COOKIE_NAME = "FMC_ACCESS";

    private final boolean secureOnly;

    public JwtCookieService(@Value("${app.auth.secure-cookies:true}") boolean secureOnly) {
        this.secureOnly = secureOnly;
    }

    public void issue(HttpServletResponse response, String jwt, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(jwt, Duration.ofSeconds(seconds)).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureOnly)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
