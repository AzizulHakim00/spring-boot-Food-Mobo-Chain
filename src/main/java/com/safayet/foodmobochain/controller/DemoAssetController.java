package com.safayet.foodmobochain.controller;

import org.bson.Document;
import org.bson.types.Binary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

/** Isolated demo-only image storage. Never reads or writes the live Cloudinary account. */
@Controller
@RequiredArgsConstructor
public class DemoAssetController {
    private final MongoTemplate mongo;
    @Value("${app.demo.full-access-enabled:false}")
    private boolean enabled;
    @Value("${app.public-base-url:}")
    private String baseUrl;

    @GetMapping("/demo-assets/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        if (!enabled || !FullDemoLoginController.DEMO_URL.equals(baseUrl)
                || !FullDemoLoginController.DEMO_DATABASE.equals(mongo.getDb().getName())
                || !id.matches("[0-9a-f-]{36}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Document image = mongo.findById(id, Document.class, "demoImages");
        if (image == null || !(image.get("data") instanceof Binary data)
                || !(image.get("mime") instanceof String mime)
                || !java.util.Set.of("image/png", "image/jpeg", "image/webp").contains(mime)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(15, TimeUnit.MINUTES).cachePublic())
                .contentType(MediaType.parseMediaType(mime))
                .body(data.getData());
    }
}
