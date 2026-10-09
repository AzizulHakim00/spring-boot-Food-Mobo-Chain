# Food Mobo Chain — MongoDB + JWT + Caffeine + Cloudinary + Docker (Phase 3 candidate)

Food Mobo Chain is a Spring Boot MVC / Thymeleaf food-ordering platform for buyers, sellers, and administrators. This branch of the supplied project contains a **source-level MongoDB persistence conversion** of the former MySQL/JPA application.

> **Not yet deployment-certified.** The SQL-to-MongoDB export and its offline integrity tests passed, but this environment could not fetch Maven or run the Spring Boot build; no Atlas import, HTTP end-to-end run, or production security test has been performed. **Do not replace a working deployment before CI and a staging Atlas test pass.**

## Architecture

Browser → JWT cookie filter + Spring Security + Thymeleaf/CSRF → MVC services + Caffeine public catalog cache → Spring Data MongoDB / MongoDB Atlas; image uploads use a signed Cloudinary backend service. `/health` checks process liveness while `/ready` checks MongoDB reachability and is used by Render.

- Twelve root collections. Cart lines, order lines, payment, and delivery are embedded in their parent cart/order document.
- `String` document IDs and scalar relationship IDs; `Relations` fetches referenced documents explicitly for existing Thymeleaf views; no JPA or DBRef.
- Financial amounts: `BigDecimal` persisted as BSON Decimal128. Date conversion: Asia/Dhaka local timestamps ↔ UTC BSON Date.
- `MongoTransactionManager` for multi-document operations (requires a MongoDB replica set such as Atlas).
- `@Version` on shopping carts and orders for optimistic concurrency checks. The private Phase 2 export includes their initial version fields.
- BCrypt password hashes and roles are retained. Phase 3 adds JWT cookie and bearer authentication, Caffeine caches, authenticated Cloudinary image uploads, Docker/Render Blueprint and CI.
- The old hardcoded demo account provisioning was removed. Import data privately or create a controlled initial administrator through a separate provisioning procedure before trying admin login.

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

5. Confirm login, catalog, buyer cart, checkout, seller order transitions, demo payment, and admin dashboard on the running server. **These steps have not been executed here.**

## Offline test and source checks

```bash
python -m unittest discover -s migration/tests -v
python scripts/check_mongo_source.py
```

For the full source SQL regression use `FMC_SQL_DUMP=/path/to/food_mobo_chain.sql` with the Python tests. The SQL file and `.jsonl` exports are deliberately excluded from Git by `.gitignore`.

See [Phase 3 deployment guide](docs/PHASE3_DEPLOYMENT.md) for JWT, Caffeine, Cloudinary, Render Blueprint, Docker, environment variables, data import and smoke tests.

## Known limitations / work remaining

- No real MongoDB repository/integration test or verified Maven compilation yet. GitHub Actions verification is included as a next-stage gate.
- Existing Thymeleaf pages still work with hydrated entity-style accessors **by design**, but must be exercised against actual Atlas data.
- No checkout idempotency key: duplicate rapid submissions/retries need production handling. Re-evaluate payment/checkout concurrency with actual transactions and optimistic locking.
- Payment is a demo, not a live gateway. Browser auth uses JWT cookies; real browser login, CSRF and deployment require staging checks.
- Bundled static images remain. Seller/admin Cloudinary upload support is now in source but must be tested with real Cloudinary credentials.
- Bounded Caffeine caches for public catalog data are now in source. Payment/auth/orders are intentionally uncached.
- No automatic demo seed account: to run, import a **private** compatible export or provision an administrator privately.
- Source code migration is reversible: keep your original MySQL project/archive and DB dump offline; no SQL source was modified by the converter.

Additional design and verification notes: [`docs/PHASE2_STATUS.md`](docs/PHASE2_STATUS.md), [`docs/MONGODB_MIGRATION_DESIGN.md`](docs/MONGODB_MIGRATION_DESIGN.md).
