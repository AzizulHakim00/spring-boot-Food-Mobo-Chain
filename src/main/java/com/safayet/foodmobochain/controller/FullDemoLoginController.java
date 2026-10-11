package com.safayet.foodmobochain.controller;

import com.safayet.foodmobochain.model.User;
import com.safayet.foodmobochain.model.enums.Role;
import com.safayet.foodmobochain.security.JwtCookieService;
import com.safayet.foodmobochain.security.JwtService;
import com.safayet.foodmobochain.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * One-click real-role sign-in is permitted ONLY in the physically separate,
 * disposable DEMO database on the dedicated demo host. This never runs on
 * existing staging/prod, even if the feature toggle is accidentally set.
 */
@Controller
@RequiredArgsConstructor
public class FullDemoLoginController {
    public static final String DEMO_URL = "https://food-mobo-chain-demo.onrender.com";
    public static final String DEMO_DATABASE = "food_mobo_chain_demo";

    private static final List<String> SELLER_CARTS = List.of(
            "dhaka-biryani-house", "street-bite", "burger-station", "pizza-corner",
            "deshi-kitchen", "spice-route", "sweet-and-sip", "campus-cravings");

    private final UserService users;
    private final JwtService tokens;
    private final JwtCookieService cookies;
    private final MongoTemplate mongo;

    @Value("${app.demo.full-access-enabled:false}")
    private boolean enabled;

    @Value("${app.public-base-url:}")
    private String baseUrl;

    public boolean isolatedDemo() {
        return enabled && DEMO_URL.equals(baseUrl)
                && DEMO_DATABASE.equals(mongo.getDb().getName());
    }

    @GetMapping("/demo/enter")
    public String enter(@RequestParam String role,
                        @RequestParam(name = "store", required = false) String store,
                        HttpServletResponse response) {
        if (!isolatedDemo()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        final String email;
        final Role expectedRole;
        final String redirect;
        switch (role) {
            case "buyer" -> {
                email = "buyer@foodmobo.local";
                expectedRole = Role.BUYER;
                redirect = "/";
            }
            case "admin" -> {
                email = "admin@foodmobo.local";
                expectedRole = Role.ADMIN;
                redirect = "/admin";
            }
            case "seller" -> {
                int slot = SELLER_CARTS.indexOf(store);
                if (slot < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown demo seller");
                email = "seller" + (slot + 1) + "@foodmobo.local";
                expectedRole = Role.SELLER;
                redirect = "/seller";
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown demo role");
        }

        final User account;
        try {
            account = users.getByEmail(email);
        } catch (IllegalArgumentException absent) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo account is unavailable");
        }
        if (account.getRole() != expectedRole
                || !account.isEnabled()
                || !Boolean.TRUE.equals(account.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Demo account is unavailable");
        }

        cookies.issue(response, tokens.generateToken(account.getEmail(), account.getSessionVersion()),
                tokens.expiresInSeconds());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader("Referrer-Policy", "no-referrer");
        return "redirect:" + redirect;
    }
}
