# Safe, staging-only demo catalog initialization

The `StagingCatalogSeeder` is **disabled by default**. It uses only sanitized fixture data under `src/main/resources/demo/catalog-seed.json`, not the original private SQL dump. It preserves the original 8 categories, 8 cart names, 42 food names/prices/images, and 2 discounts; buyer/seller identities are synthetic. It imports no historical orders, notifications, real customer addresses, or original password hashes.

To populate a brand-new dedicated `food_mobo_chain` database on Render staging:

1. Confirm that `MONGODB_URI` points to your Atlas cluster and `SPRING_MONGODB_DATABASE=food_mobo_chain` selects the dedicated project database. Do **not** seed the unrelated `store_manager` database.
2. Temporarily set `APP_SEED_DEMO_CATALOG=true`, `APP_SEED_TARGET_DATABASE=food_mobo_chain`, and a new strong random `APP_SEED_ADMIN_PASSWORD` (at least 20 characters). Optionally supply `APP_SEED_BUYER_PASSWORD` and `APP_SEED_SELLER_PASSWORD` of at least 20 characters each.
3. Deploy once. The startup importer checks both the exact database name and all 12 application collections for emptiness. It refuses the wrong database and skips a nonempty database. It inserts 70 sanitized documents in a MongoDB transaction and creates basic unique indexes.
4. Verify `/ready`, `/foods` (42 items; 12 per page) and `/food-carts` (8 carts). Confirm public images load. The admin login email is `admin@foodmobo.local`, sample buyer `buyer@foodmobo.local`, and the first seller `seller1@foodmobo.local`.
5. Turn `APP_SEED_DEMO_CATALOG=false` and remove/clear `APP_SEED_ADMIN_PASSWORD`, `APP_SEED_BUYER_PASSWORD`, `APP_SEED_SELLER_PASSWORD` from the environment after securely saving the login credentials. Redeploy if necessary. The users' BCrypt hashes remain in Atlas. Do not publish credentials.

Do not enable demo seeding for a real production database or one containing existing users/orders. To migrate actual project data, use the private export and standard MongoDB import procedure (not this public demo seed). Rotate the MongoDB password previously shared in plaintext, then update the private Render environment setting.