package com.safayet.foodmobochain.config;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staging-only initialization of synthetic demo accounts and public menu data.
 * Disabled by default. Never imports private SQL accounts, orders or password hashes.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.catalog-enabled", havingValue = "true")
public class StagingCatalogSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StagingCatalogSeeder.class);
    private static final Map<String, Integer> COLLECTION_COUNTS = Map.of(
            "users", 10, "categories", 8, "foodCarts", 8, "foodItems", 42, "discounts", 2);
    private static final List<String> SAFETY_COLLECTIONS = List.of(
            "users", "categories", "foodCarts", "foodItems", "discounts",
            "orders", "shoppingCarts", "reviews", "notifications",
            "favoriteFoods", "favoriteCarts", "passwordResetTokens");

    private final MongoTemplate mongoTemplate;
    private final MongoTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_SEED_TARGET_DATABASE:food_mobo_chain}")
    private String expectedDatabase;
    @Value("${APP_SEED_ADMIN_PASSWORD:}")
    private String adminPassword;
    @Value("${APP_SEED_BUYER_PASSWORD:}")
    private String buyerPassword;
    @Value("${APP_SEED_SELLER_PASSWORD:}")
    private String sellerPassword;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String actualDatabase = mongoTemplate.getDb().getName();
        if (!"food_mobo_chain".equals(expectedDatabase)
                && !"food_mobo_chain_test".equals(expectedDatabase)) {
            throw new IllegalStateException("The staging seed target must be a dedicated Food Mobo Chain database");
        }
        if (!expectedDatabase.equals(actualDatabase)) {
            throw new IllegalStateException("The MongoDB URI points to a different database than the approved staging target");
        }
        if (adminPassword == null || adminPassword.length() < 20) {
            throw new IllegalStateException("APP_SEED_ADMIN_PASSWORD must contain at least 20 characters");
        }
        requireStrongIfPresent(buyerPassword);
        requireStrongIfPresent(sellerPassword);

        for (String collection : SAFETY_COLLECTIONS) {
            if (mongoTemplate.collectionExists(collection)
                    && mongoTemplate.getCollection(collection).estimatedDocumentCount() > 0) {
                log.warn("Sanitized staging seed skipped because application collections are not empty");
                return;
            }
        }

        String json;
        try (var stream = new ClassPathResource("demo/catalog-seed.json").getInputStream()) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Document root = Document.parse(json);
        Document collections = root.get("collections", Document.class);
        if (collections == null || !collections.keySet().equals(COLLECTION_COUNTS.keySet())) {
            throw new IllegalStateException("Invalid sanitized catalog collections");
        }
        Map<String, List<Document>> records = new LinkedHashMap<>();
        for (String collection : List.of("users", "categories", "foodCarts", "foodItems", "discounts")) {
            List<Document> rows = collections.getList(collection, Document.class);
            if (rows == null || rows.size() != COLLECTION_COUNTS.get(collection)) {
                throw new IllegalStateException("Invalid sanitized catalog count for " + collection);
            }
            records.put(collection, rows);
        }

        SecureRandom random = new SecureRandom();
        for (Document user : records.get("users")) {
            String password = switch (user.getString("_id")) {
                case "users:1" -> adminPassword;
                case "users:2" -> present(buyerPassword) ? buyerPassword : randomPassword(random);
                case "users:3" -> present(sellerPassword) ? sellerPassword : randomPassword(random);
                default -> randomPassword(random);
            };
            user.put("password", passwordEncoder.encode(password));
        }

        mongoTemplate.indexOps("users").ensureIndex(new Index().on("emailNormalized", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("categories").ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("foodCarts").ensureIndex(new Index().on("slug", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("foodCarts").ensureIndex(new Index().on("ownerId", Sort.Direction.ASC).unique());
        mongoTemplate.indexOps("discounts").ensureIndex(new Index().on("code", Sort.Direction.ASC).unique());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (Map.Entry<String, List<Document>> entry : records.entrySet()) {
                for (Document row : entry.getValue()) {
                    mongoTemplate.insert(row, entry.getKey());
                }
            }
        });
        log.info("Initialized staging demo catalog: {} categories, {} food carts, {} foods",
                records.get("categories").size(), records.get("foodCarts").size(), records.get("foodItems").size());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireStrongIfPresent(String value) {
        if (present(value) && value.length() < 20) {
            throw new IllegalStateException("Demo account passwords must contain at least 20 characters");
        }
    }

    private static String randomPassword(SecureRandom random) {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
