package com.azizul.foodmobochain.config;

import com.azizul.foodmobochain.model.enums.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-time, explicit opt-in migration for the ten known synthetic staging demo users.
 * It never changes passwords, user enablement, roles, food carts, or other records.
 * Rotate demo credentials securely before enabling APP_DEMO_VERIFY_EXISTING.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.verify-existing", havingValue = "true")
public class StagingDemoAccountVerification implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StagingDemoAccountVerification.class);
    private final MongoTemplate mongo;

    @Value("${app.public-base-url:}")
    private String baseUrl;

    @Override
    public void run(ApplicationArguments args) {
        if (!"https://food-mobo-chain-staging.onrender.com".equals(baseUrl) ||
                !"food_mobo_chain".equals(mongo.getDb().getName())) {
            throw new IllegalStateException("Demo verification is restricted to the staging database.");
        }

        long updated = 0;
        updated += mark(1, "admin@foodmobo.local", Role.ADMIN);
        updated += mark(2, "buyer@foodmobo.local", Role.BUYER);
        for (int seller = 1; seller <= 8; seller++) {
            updated += mark(seller + 2, "seller" + seller + "@foodmobo.local", Role.SELLER);
        }
        log.info("Staging demo verification reconciliation completed ({} matching records updated)", updated);
    }

    private long mark(int id, String email, Role role) {
        Query query = Query.query(Criteria.where("_id").is("users:" + id)
                .and("emailNormalized").is(email)
                .and("role").is(role.name())
                .and("emailVerified").ne(true));
        return mongo.updateFirst(query, new Update().set("emailVerified", true), "users")
                .getModifiedCount();
    }
}
