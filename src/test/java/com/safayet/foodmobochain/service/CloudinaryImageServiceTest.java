package com.safayet.foodmobochain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class CloudinaryImageServiceTest {
    @Test void acceptsWebpMagicHeader() {
        assertEquals("webp", CloudinaryImageService.sniffImageExtension(
                "RIFF0000WEBP12345".getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void rejectsUntrustedFileContents() {
        assertThrows(IllegalArgumentException.class, () -> CloudinaryImageService.sniffImageExtension(
                "<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void signatureIsDeterministicAndNeverEqualsSecret() {
        String s = CloudinaryImageService.signature("food-mobo-chain/foods", "123", "secret");
        assertEquals(40, s.length());
        assertEquals(s, CloudinaryImageService.signature("food-mobo-chain/foods", "123", "secret"));
        assertNotEquals("secret", s);
    }
}
