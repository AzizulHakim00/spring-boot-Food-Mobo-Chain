package com.safayet.foodmobochain.config;

import com.safayet.foodmobochain.controller.FullDemoLoginController;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Fail closed at startup if full-access one-click demo is configured anywhere
 * except the dedicated demo host and database.
 */
@Component
@RequiredArgsConstructor
public class FullDemoIsolationGuard implements ApplicationRunner {
    private final MongoTemplate mongo;

    @Value("${app.demo.full-access-enabled:false}")
    private boolean fullDemoEnabled;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Override public void run(ApplicationArguments args) {
        if (!fullDemoEnabled) return;
        if (!FullDemoLoginController.DEMO_URL.equals(publicBaseUrl)
                || !FullDemoLoginController.DEMO_DATABASE.equals(mongo.getDb().getName())) {
            throw new IllegalStateException(
                    "Full-operation demo must be hosted on the dedicated demo service and database");
        }
    }
}
