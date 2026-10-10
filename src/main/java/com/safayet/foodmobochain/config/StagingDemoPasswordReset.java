package com.safayet.foodmobochain.config;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * One-time staging-only reset of 10 pre-existing DEMO accounts.
 * Never prints raw credentials; does not modify carts, foods or orders.
 * Remove this temporary runner immediately after live verification.
 */
@Component
@ConditionalOnProperty(name = "app.staging-demo.password-reset", havingValue = "true")
public class StagingDemoPasswordReset implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StagingDemoPasswordReset.class);
    private static final String STAGING_ID = "srv-db4h57vlk1mc73816qk0";
    private static final String DATABASE = "food_mobo_chain";
    private static final String[][] ACCOUNTS = {
        {"users:1", "admin@foodmobo.local", "ADMIN"},
        {"users:2", "buyer@foodmobo.local", "BUYER"},
        {"users:3", "seller1@foodmobo.local", "SELLER"},
        {"users:4", "seller2@foodmobo.local", "SELLER"},
        {"users:5", "seller3@foodmobo.local", "SELLER"},
        {"users:6", "seller4@foodmobo.local", "SELLER"},
        {"users:7", "seller5@foodmobo.local", "SELLER"},
        {"users:8", "seller6@foodmobo.local", "SELLER"},
        {"users:9", "seller7@foodmobo.local", "SELLER"},
        {"users:10", "seller8@foodmobo.local", "SELLER"}
    };

    private final MongoTemplate mongoTemplate;
    private final MongoTransactionManager transactionManager;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final String serviceId;
    private final String credentialsJson;

    public StagingDemoPasswordReset(
            MongoTemplate mongoTemplate,
            MongoTransactionManager transactionManager,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authManager,
            @Value("${RENDER_SERVICE_ID:}") String serviceId,
            @Value("${APP_STAGING_DEMO_PASSWORDS_JSON:}") String credentialsJson) {
        this.mongoTemplate = mongoTemplate;
        this.transactionManager = transactionManager;
        this.passwordEncoder = passwordEncoder;
        this.authManager = authManager;
        this.serviceId = serviceId;
        this.credentialsJson = credentialsJson;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!STAGING_ID.equals(serviceId) || !DATABASE.equals(mongoTemplate.getDb().getName())) {
            throw new IllegalStateException("Demo password reset refused: not approved staging service/database");
        }
        if (credentialsJson == null || credentialsJson.isBlank()) {
            throw new IllegalStateException("Demo password reset requires private environment credentials");
        }

        Document passwords = Document.parse(credentialsJson);
        Set<String> expectedEmails = new HashSet<>();
        Set<String> uniquePasswords = new HashSet<>();
        int cartCount = 0;

        // Validate every account and its seller/cart relationship before any write.
        for (String[] account : ACCOUNTS) {
            String id = account[0], email = account[1], role = account[2];
            expectedEmails.add(email);
            String password = passwords.getString(email);
            if (password == null || password.length() < 24 || password.length() > 128
                    || !uniquePasswords.add(password)) {
                throw new IllegalStateException("Invalid or duplicated staging credentials");
            }
            Document user = mongoTemplate.findOne(exactUser(id, email, role), Document.class, "users");
            if (user == null || !Boolean.TRUE.equals(user.getBoolean("enabled"))) {
                throw new IllegalStateException("A required staging demo account is missing or disabled");
            }
            if ("SELLER".equals(role)) {
                Document cart = mongoTemplate.findOne(
                        Query.query(Criteria.where("ownerId").is(id)), Document.class, "foodCarts");
                if (cart == null || !id.equals(cart.getString("ownerId"))) {
                    throw new IllegalStateException("A required seller food cart is missing");
                }
                cartCount++;
            }
        }
        if (passwords.size() != ACCOUNTS.length || !passwords.keySet().equals(expectedEmails)
                || cartCount != 8) {
            throw new IllegalStateException("The staging demo account set is not an exact match");
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            for (String[] account : ACCOUNTS) {
                String id = account[0], email = account[1], role = account[2];
                String hash = passwordEncoder.encode(passwords.getString(email));
                UpdateResult result = mongoTemplate.updateFirst(
                        exactUser(id, email, role),
                        new Update().set("password", hash), "users");
                if (result.getMatchedCount() != 1) {
                    throw new IllegalStateException("Staging demo password reset was not atomic");
                }
            }
        });

        int authenticated = 0;
        for (String[] account : ACCOUNTS) {
            Authentication authentication = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            account[1], passwords.getString(account[1])));
            if (!authentication.isAuthenticated() || authentication.getAuthorities().stream()
                    .noneMatch(authority -> authority.getAuthority().equals("ROLE_" + account[2]))) {
                throw new IllegalStateException("A staging demo login could not be verified");
            }
            authenticated++;
        }
        log.info("STAGING_DEMO_RESET_SUCCESS accounts={} authenticated={} sellerCarts={}",
                ACCOUNTS.length, authenticated, cartCount);
    }

    private static Query exactUser(String id, String email, String role) {
        return Query.query(Criteria.where("_id").is(id)
                .and("emailNormalized").is(email)
                .and("role").is(role));
    }
}
