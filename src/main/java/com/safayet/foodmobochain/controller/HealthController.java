package com.safayet.foodmobochain.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Separate inexpensive process liveness from database-dependent readiness. */
@RestController
@RequiredArgsConstructor
public class HealthController {
    private final MongoTemplate mongoTemplate;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (RuntimeException unavailable) {
            // Never disclose the MongoDB URI or driver error details to public clients.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "NOT_READY"));
        }
    }
}
