package com.safayet.foodmobochain.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Public, strictly read-only demonstration sessions. These are NOT Spring
 * Security user authentications and never issue FMC_ACCESS JWT cookies.
 */
@Service
public class DemoSessionService {
    public static final String COOKIE_NAME = "FMC_DEMO";
    private static final Duration LIFETIME = Duration.ofMinutes(20);
    private static final String STAGING_BASE = "https://food-mobo-chain-staging.onrender.com";
    private static final String CI_BASE = "http://127.0.0.1:10000";
    private static final String TOKEN_SUBJECT = "food-mobo-readonly-demo";

    public static final List<DemoStore> STORES = List.of(
            new DemoStore("dhaka-biryani-house", "Dhaka Biryani House"),
            new DemoStore("street-bite", "Street Bite"),
            new DemoStore("burger-station", "Burger Station"),
            new DemoStore("pizza-corner", "Pasta & Bowl Corner"),
            new DemoStore("deshi-kitchen", "Deshi Kitchen"),
            new DemoStore("spice-route", "Spice Route"),
            new DemoStore("sweet-and-sip", "Sweet Street"),
            new DemoStore("campus-cravings", "Campus Cravings")
    );
    private static final Map<String, String> STORE_NAMES =
            STORES.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    DemoStore::slug, DemoStore::name));

    private final SecretKey key;
    private final String publicBaseUrl;
    private final boolean enabled;
    private final boolean allowCiLoopback;
    private final boolean secureCookie;

    public DemoSessionService(
            @Value("${app.jwt.secret-base64:}") String base64Secret,
            @Value("${app.public-base-url:}") String publicBaseUrl,
            @Value("${app.demo.access-enabled:false}") boolean enabled,
            @Value("${app.demo.allow-ci-loopback:false}") boolean allowCiLoopback,
            @Value("${app.auth.secure-cookies:true}") boolean secureCookie) {
        byte[] secret = Decoders.BASE64.decode(base64Secret);
        if (secret.length < 32) throw new IllegalStateException("Demo requires a valid signing secret");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update("food-mobo-readonly-demo-v1".getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(secret);
            this.key = Keys.hmacShaKeyFor(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
        this.publicBaseUrl = publicBaseUrl;
        this.enabled = enabled;
        this.allowCiLoopback = allowCiLoopback;
        this.secureCookie = secureCookie;
    }

    public boolean isEnabled() {
        return enabled && (STAGING_BASE.equals(publicBaseUrl)
                || (allowCiLoopback && CI_BASE.equals(publicBaseUrl)));
    }

    public DemoScope choose(String role, String storeSlug) {
        if (!isEnabled()) throw new IllegalStateException("Demo access is disabled");
        return switch (role == null ? "" : role) {
            case "buyer" -> new DemoScope("buyer", "");
            case "admin" -> new DemoScope("admin", "");
            case "seller" -> {
                if (storeSlug == null || !STORE_NAMES.containsKey(storeSlug))
                    throw new IllegalArgumentException("Unknown demo food cart");
                yield new DemoScope("seller", storeSlug);
            }
            default -> throw new IllegalArgumentException("Unknown demo role");
        };
    }

    public String issue(DemoScope scope) {
        // Validate all fields again: a forged scope cannot select other users.
        DemoScope checked = choose(scope.role(), scope.storeSlug());
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(TOKEN_SUBJECT)
                .claim("kind", "read-only")
                .claim("demoRole", checked.role())
                .claim("demoStore", checked.storeSlug())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(LIFETIME)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public DemoScope verify(String signed) {
        if (!isEnabled() || signed == null || signed.isBlank())
            throw new IllegalArgumentException("Missing demo session");
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(signed).getPayload();
        if (!TOKEN_SUBJECT.equals(claims.getSubject())
                || !"read-only".equals(claims.get("kind", String.class))) {
            throw new IllegalArgumentException("Invalid demo scope");
        }
        String role = claims.get("demoRole", String.class);
        String store = claims.get("demoStore", String.class);
        return choose(role, store);
    }

    public void issueCookie(HttpServletResponse response, DemoScope scope) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, issue(scope))
                        .httpOnly(true).secure(secureCookie).sameSite("Lax")
                        .path("/demo").maxAge(LIFETIME).build().toString());
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                ResponseCookie.from(COOKIE_NAME, "").httpOnly(true)
                        .secure(secureCookie).sameSite("Lax")
                        .path("/demo").maxAge(Duration.ZERO).build().toString());
    }

    public static String storeName(String slug) {
        return STORE_NAMES.get(slug);
    }

    public record DemoScope(String role, String storeSlug) {}
    public record DemoStore(String slug, String name) {}
}
