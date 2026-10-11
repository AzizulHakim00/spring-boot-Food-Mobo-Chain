package com.azizul.foodmobochain.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Shared primitives for 256-bit, one-time, never-logged account links. */
public final class AccountTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountTokens() { }

    public static String create() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public static boolean validShape(String token) {
        return token != null && token.length() >= 32 && token.length() <= 128
                && token.matches("[A-Za-z0-9_-]+");
    }

    public static String hash(String token) {
        if (!validShape(token)) throw new IllegalArgumentException("Invalid account link.");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
