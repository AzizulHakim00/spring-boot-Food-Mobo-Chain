# Phase 2 source conversion — honest completion and QA status

## Scope that changed

| Component | Status / approach |
|---|---|
| Maven dependency | JPA/MySQL removed, Spring Data MongoDB added, Java target set to 21 |
| Mongo schema | 12 @Document roots and embedded CartItem, OrderItem, Payment, Delivery |
| IDs | String document keys, scalar references and explicit transient view relationships |
| Repositories | 12 MongoRepository interfaces; removed item/payment/delivery JPA repositories |
| Catalog | MongoTemplate dynamic search; public/seller service query redesign |
| Cart | embedded lines, 1..20 quantity invariant, optimistic version |
| Checkout | embedded order snapshot/payment/delivery, Mongo transaction for separate root documents |
| Review and favorites | separate indexed collections; delivered-purchase review eligibility |
| Reports | MongoDB aggregation for paid amount and top-selling foods |
| Views/controllers | Controller and DTO IDs converted to String; Thymeleaf relationship hydration bridge |
| Timestamps | Asia/Dhaka LocalDateTime converters for BSON UTC Date |
| Security | Existing Spring Security form/session login retained, no JWT yet; no demo credentials seeded |
| Static images | Kept bundled images; remote Cloudinary upload not yet implemented |

## Verification performed in this environment

1. Phase 2 exporter converted the uploaded SQL into 12 MongoDB Extended JSONL collections.
2. Five Python exporter and integrity tests passed.
3. Sample historical order amount and references reconciled in offline export.
4. The Java syntax parser inspected both main and test `.java` sources with zero parse errors (syntax-only, not type checking).
5. Full `./mvnw test` **not run successfully**: Maven distribution download was unavailable in the environment. No test suite or Spring context has actually run.
6. No live MongoDB Atlas import, running MongoDB query, Java compilation, or browser smoke test has occurred. This remains an unverified conversion candidate.

## High-priority staging checks before release

- **Build:** GitHub Actions / a JDK 21 Maven environment must compile all Java files; review the errors and fix until green.
- **Repositories:** derive nested-property queries against actual MongoDB indexes and verify all results, especially buyer-item review eligibility and payment-state queries.
- **Transactions:** verify `@Transactional` on your Atlas replica set; a standalone `mongod` cannot satisfy multi-document transactions without a replica-set configuration.
- **Data:** private import has `version: 0` on historical cart/order documents; verify updates preserve optimistic locking.
- **UI:** full GET/POST routes, CSRF, dashboard statistics, form field binding, and all Thymeleaf template pages.
- **Checkout:** concurrent clicks, payment state transitions, rollback behavior, failed notification write, and cross-seller carts.
- **Accounts:** existing BCrypt hashes, account disabled status, reset-token expiry, and safe bootstrap for first admin.
- **Time zone:** confirm Asia/Dhaka was actually used for old SQL dates; BSON Date precision is milliseconds.
- **Secrets:** never push MySQL dump, converted private account records or any Mongo URI/secret.

## Independent future work

Phase 3: JWT + Secure/HttpOnly cookies + CSRF-preserving MVC security. Phase 4: Cloudinary assets. Phase 5: Caffeine cache. Phase 6: Docker + Render deployment and smoke tests.
