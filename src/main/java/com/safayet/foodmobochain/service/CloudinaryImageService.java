package com.safayet.foodmobochain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Server-signed Cloudinary upload, bounded to 2 MiB, with no persistent files on Render. */
@Service
public class CloudinaryImageService {
    public static final long MAX_SIZE = 2L * 1024 * 1024;
    private static final Set<String> KINDS = Set.of("foods", "carts", "categories");
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final RestClient client;

    public CloudinaryImageService(@Value("${cloudinary.cloud-name:}") String cloudName,
                                  @Value("${cloudinary.api-key:}") String apiKey,
                                  @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.client = RestClient.create();
    }

    public String upload(MultipartFile image, String kind) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown image destination");
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            throw new IllegalStateException("Cloudinary is not configured on this server");
        }
        if (!cloudName.matches("[a-zA-Z0-9_-]+")) throw new IllegalStateException("Invalid cloud name");
        if (image == null || image.isEmpty() || image.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Please upload an image smaller than 2 MB");
        }
        byte[] contents;
        try { contents = image.getBytes(); }
        catch (IOException e) { throw new IllegalArgumentException("The image cannot be read", e); }
        String extension = sniffImageExtension(contents);
        String folder = "food-mobo-chain/" + kind;
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = signature(folder, timestamp, apiSecret);
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("api_key", apiKey);
        parts.add("timestamp", timestamp);
        parts.add("folder", folder);
        parts.add("signature", signature);
        parts.add("file", new ByteArrayResource(contents) {
            @Override public String getFilename() { return "upload." + extension; }
        });
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> reply = client.post()
                    .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts).retrieve().body(Map.class);
            Object url = reply == null ? null : reply.get("secure_url");
            String prefix = "https://res.cloudinary.com/" + cloudName + "/image/upload/";
            if (!(url instanceof String str) || !str.startsWith(prefix)) {
                throw new IllegalStateException("Unexpected Cloudinary upload response");
            }
            return str;
        } catch (RestClientException e) {
            throw new IllegalStateException("Image upload failed. Check Cloudinary settings or limits.", e);
        }
    }

    static String signature(String folder, String timestamp, String secret) {
        String signingInput = "folder=" + folder + "&timestamp=" + timestamp + secret;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static String sniffImageExtension(byte[] bytes) {
        if (bytes.length >= 12 && bytes[0] == (byte)0x52 && bytes[1] == (byte)0x49
                && bytes[2] == (byte)0x46 && bytes[3] == (byte)0x46
                && bytes[8] == (byte)0x57 && bytes[9] == (byte)0x45
                && bytes[10] == (byte)0x42 && bytes[11] == (byte)0x50) return "webp";
        if (bytes.length >= 8 && bytes[0] == (byte)0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47) return "png";
        if (bytes.length >= 3 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xd8
                && bytes[2] == (byte)0xff) return "jpg";
        throw new IllegalArgumentException("Only JPEG, PNG and WebP images are allowed");
    }
}
