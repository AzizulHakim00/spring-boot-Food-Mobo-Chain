# Food Mobo Chain — Spring Boot + MongoDB + JWT + Caffeine + Cloudinary + Docker

Food Mobo Chain is a Spring Boot MVC / Thymeleaf food-ordering platform for buyers, sellers, and administrators. This branch of the supplied project contains a **source-level MongoDB persistence conversion** of the former MySQL/JPA application.

> **Staging deployment (10 October 2026):** [Open Food Mobo Chain](https://food-mobo-chain-staging.onrender.com) on Render Free. The deployed container starts, `/ready` successfully pings MongoDB Atlas, the public catalog shows the sanitized demo records, and the live GitHub Actions HTTP smoke workflow verified the homepage, catalog, protected-route redirects, static scripts, CSS, and 24 rendered image URLs. This is a **staging demonstration**, not a certified production payment service. A real server-signed Cloudinary upload succeeded on staging on 10 October 2026; the one-time verifier has since been removed. Full authenticated seller-browser acceptance testing remains outstanding.

## Architecture

Browser → JWT cookie filter + Spring Security + Thymeleaf/CSRF → MVC services + Caffeine public catalog cache → Spring Data MongoDB / MongoDB Atlas; image uploads use a signed Cloudinary backend service. `/health` checks process liveness while `/ready` checks MongoDB reachability. The existing manually created Render service should have its health-check path set to `/ready` in Render Settings; this is not yet confirmed configured.

- Twelve root collections. Cart lines, order lines, payment, and delivery are embedded in their parent cart/order document.
- `String` document IDs and scalar relationship IDs; `Relations` fetches referenced documents explicitly for existing Thymeleaf views; no JPA or DBRef.
- Financial amounts: `BigDecimal` persisted as BSON Decimal128. Date conversion: Asia/Dhaka local timestamps ↔ UTC BSON Date.
- `MongoTransactionManager` for multi-document operations (requires a MongoDB replica set such as Atlas).
- `@Version` on shopping carts and orders for optimistic concurrency checks. The private Phase 2 export includes their initial version fields.
- BCrypt password hashes and roles are retained. Phase 3 adds JWT cookie and bearer authentication, Caffeine caches, authenticated Cloudinary image uploads, Docker/Render Blueprint and CI.
- The old hardcoded demo account provisioning was removed. Import data privately or create a controlled initial administrator through a separate provisioning procedure before trying admin login.


## Render staging operations

- **Staging URL:** https://food-mobo-chain-staging.onrender.com
- **Git branch:** `feature/mongodb-jwt-cloudinary-render` (automatic deployment off for staging).
- **Ready check:** `/ready` requires a successful MongoDB ping, unlike process-only `/health`.
- **Seed data:** one-time sanitized staging seed has completed; `APP_SEED_DEMO_CATALOG=false` prevents reseeding at subsequent restarts.
- **Cloudinary:** a real server-signed staging upload succeeded at **2026-10-10 12:53:58 UTC**. The current seller food and cover forms submit an image and save its Cloudinary URL in **one CSRF-protected multipart request** (JPEG/PNG/WebP below 2 MB); the older protected `/seller/uploads/images` endpoint remains available for integrations. A live browser upload through the new form still requires seller acceptance testing. Keep all Cloudinary credentials only in **Render → Environment**, never in Git.
- **Live health report:** [GitHub Actions live-site smoke](https://github.com/AzizulHakim00/spring-boot-Food-Mobo-Chain/actions/workflows/live-site-smoke.yml) checks public pages and images. Cold starts on Render Free may take time.
- **Security:** this is currently a shared Atlas cluster from a separate project; provision a dedicated least-privilege database user for `food_mobo_chain` and rotate any database password previously shared in chat.

## Run on a staging MongoDB Atlas database

Requirements: JDK 21, Maven wrapper (included), and access to a **private** MongoDB Atlas replica set. Never commit your connection URI or user data.

1. Create an empty MongoDB Atlas database named `food_mobo_chain`; create a dedicated least-privilege database user.
2. Run the offline converter and verification against your private SQL dump; see [`migration/README.md`](migration/README.md). Run `migration/mongo_indexes.js` after import.
3. Configure an environment variable:

   ```bash
   export JWT_SECRET_BASE64="$(python -c 'import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())')"
   export MONGODB_URI='mongodb+srv://USER:PASSWORD@YOUR_CLUSTER.mongodb.net/food_mobo_chain?retryWrites=true&w=majority'
   ```

   PowerShell: `$env:MONGODB_URI='mongodb+srv://.../food_mobo_chain?retryWrites=true&w=majority'`

4. Run tests and start the project:

   ```bash
   ./mvnw -B -ntp test
   ./mvnw spring-boot:run
   ```

5. The CI pipeline verifies JWT registration/login, browser cookies, CSRF, role isolation, discounted multi-seller checkout, seller menu/cover management, and demo-payment completion against a **disposable MongoDB replica set**. Render checks verify public pages, static assets and readiness. **Real seller-browser Cloudinary uploads and end-to-end live Atlas acceptance still require a manual test.**

## Offline test and source checks

```bash
python -m unittest discover -s migration/tests -v
python scripts/check_mongo_source.py
```

For the full source SQL regression use `FMC_SQL_DUMP=/path/to/food_mobo_chain.sql` with the Python tests. The SQL file and `.jsonl` exports are deliberately excluded from Git by `.gitignore`.

See [Phase 3 deployment guide](docs/PHASE3_DEPLOYMENT.md) for JWT, Caffeine, Cloudinary, Render Blueprint, Docker, environment variables, data import and smoke tests.

## Known limitations / work remaining

- GitHub Actions runs JUnit, MongoDB-backed authenticated buyer/seller/admin smoke tests, promo code and order-state integration tests, and a Docker build. Keep the full report at [Staging QA and audit](docs/STAGING_QA_2026-10-11.md).
- Existing Thymeleaf pages still work with hydrated entity-style accessors **by design**, but must be exercised against actual Atlas data.
- No checkout idempotency key: duplicate rapid submissions/retries need production handling. Re-evaluate payment/checkout concurrency with actual transactions and optimistic locking.
- Payment is a demo, not a live gateway. Cookie JWT login and CSRF were verified in disposable MongoDB CI; run live end-to-end buyer/seller workflows before public production use.
- All 42 sanitized food items reference bundled, optimized WebP images; the live-site GitHub Actions workflow verifies all seeded catalog image URLs. A live server-signed Cloudinary upload was successful; authenticated seller UI acceptance is not yet verified.
- Bounded Caffeine caching is **enabled** for public carts, featured foods and categories (maximum 200 entries, 3-minute TTL), with transaction-aware eviction. Payment/auth/orders remain intentionally uncached.
- Sanitized staging seed is available only as an explicit one-time opt-in. Render's staging database has already been seeded with 8 categories, 8 food carts, and 42 food items; subsequent automatic seeding has been disabled.
- Source code migration is reversible: keep your original MySQL project/archive and DB dump offline; no SQL source was modified by the converter.

Additional design and verification notes: [`docs/PHASE2_STATUS.md`](docs/PHASE2_STATUS.md), [`docs/MONGODB_MIGRATION_DESIGN.md`](docs/MONGODB_MIGRATION_DESIGN.md).
