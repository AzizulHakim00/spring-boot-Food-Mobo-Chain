# Java and Thymeleaf change map for Phase 2

Grounded in the uploaded `FoodMoboChain.zip`. This was the Phase 1 refactor checklist. Phase 2 has now implemented the source changes described below; see `PHASE2_STATUS.md` for their actual verification status and outstanding items.

## 1. Build and settings

- `pom.xml`: replace Spring Data JPA and MySQL with Spring Data MongoDB;
  retain MVC, Thymeleaf, Security, Validation and Lombok. Add MongoDB test support.
- `application.properties`: remove JDBC URL/username/password and `spring.jpa.*`;
  configure `spring.mongodb.uri=${MONGODB_URI}` (Spring Boot 4);
  supply demo defaults through dev-only configuration. Never put DB credentials in Git.
- `config/DataInitializer.java`: no Hibernate relationships or SQL PK generators;
  make seeding opt-in with `@Profile("dev")` or an explicit flag. Import should be
  performed once by the CLI, NOT at every app startup.
- `config/SecurityConfig.java`, `service/CustomUserDetailsService.java`:
  keep BCrypt and role boundaries, but switch account lookup to normalized Mongo email;
  JWT should be integrated separately after Mongo migration is stable.

## 2. Entity model changes

`User`, `Category`, `FoodCart`, `FoodItem`, `Discount`, `Review`, `FavoriteFood`,
`FavoriteCart`, `Notification`, `PasswordResetToken`: root `@Document`s.

`Cart` + `CartItem`: one `ShoppingCartDocument` root with embedded line entries.

`CustomerOrder` + `OrderItem` + `Payment` + `Delivery`: one `OrderDocument`
root with embedded item snapshots, payment and delivery. Avoid separate Mongo
repositories for items, payment and delivery unless independent workflows are
explicitly required.

- Use `String id`, `@Id`, explicit reference IDs, no implicit `@DBRef`.
- Replace `@PrePersist/@PreUpdate` with Spring Data auditing or explicitly
  generated timestamps.
- For all monetary fields use `BigDecimal` with `@Field(targetType=DECIMAL128)`.
- Prefer UTC `Instant` for stored timestamps; display Asia/Dhaka if needed.
- Consider `@Version` on shopping cart and order roots.

## 3. Repository conversion

Remove/replace all 16 JPA repository interfaces. The new persistent root set
requires 12 Mongo repository interfaces, with custom MongoTemplate implementations
for queries not expressible via derived methods.

**Special cases:**

- `FoodItemRepository.searchPublic` uses JPQL joins across food, category, cart and
  owner. MongoDB does not automatically translate these; use batched ID filtering,
  an aggregation pipeline, or carefully maintained search eligibility fields.
- `FoodCartRepository.findPublicApproved` must enforce an enabled owner.
- `ReviewRepository.averageForFood/Cart` use aggregation (`$match`, `$group`).
- `OrderRepository.sumDeliveredRevenue*`, `countDeliveredOrdersWithFood`,
  `countDeliveredOrdersFromCart`, daily counts: MongoTemplate aggregations/queries.
- `OrderItemRepository.findTopSellingFoods`: unwind the embedded `orders.items[]`
  and aggregate delivered order quantities.
- `PaymentRepository` and `DeliveryRepository`: replaced by queries/updates
  against `orders.payment.*` and `orders.delivery.*` fields.
- `CartItemRepository`: no longer required for embedded cart items.
- `NotificationRepository.findTop8...`: query `userId`, sort createdAt desc,
  request limited results and use lightweight view models.

## 4. Service migration and regression tests

- `UserService`: account ID Strings, consistent normalized email, seller/cart creation.
- `CatalogService`: ID Strings, category/cart lookups, seller editing, food search,
  strict visibility and orderability policy, and aggregated review ratings.
- `CartService`: embedded item mutations, buyer ownership checks, one-seller policy;
  reject quantity <= 0 rather than coercing; concurrency-safe writes.
- `OrderService`: embed order snapshots, payment, and delivery; multi-document
  transaction or idempotent recovery for order + cart clearing + notifications.
- `PaymentService`: update embedded payment state conditionally and idempotently.
- `DiscountService`: normalized coupon code and Decimal128 calculations.
- `ReviewService`: review eligibility against delivered order snapshots;
  unique per buyer/target indexes; exactly one target.
- `FavoriteService`: unique buyer-food and buyer-cart pairs.
- `NotificationService`: no JPA lazy User navigation; use `userId`.
- `PasswordResetService`: tokenHash unique, used flag, TTL expiry.
- `ReportService`: replace SQL JPQL aggregate queries with Mongo aggregation.

## 5. Controllers/DTOs known to depend on numeric IDs

- `AdminController`: numeric path IDs for users/carts/foods/categories/discounts/reviews.
- `SellerController`: food item route IDs.
- `CartController`: `foodId`, cart line `id` route IDs.
- `FavoriteController`: food/cart IDs.
- `CatalogController`: optional cart filter (`Long`) and food detail (`Long`).
- `NotificationController`: notification route IDs.
- `ReviewDTO`: `Long foodItemId`, `Long foodCartId` -> `String`.
- `FoodItemDTO`: `Long categoryId` -> `String`.

`OrderController` already uses `String orderNumber` for order detail and payment
routes; preserve that stable business-facing order-number URL.

## 6. Thymeleaf coupling and Java view models

Existing templates navigate JPA objects directly; for example:

- Public catalog: `food.foodCart.name`, `food.category.name`.
- Seller/admin: `cart.owner.fullName`, `cart.owner.email`.
- Order detail pages: order buyer/cart info and item snapshots.
- Cart views: line item information via `CartItem.foodItem`.

Mongo documents will hold `ownerId`, `categoryId`, `foodCartId`, etc., not
nested JPA objects. Introduce explicit view DTOs or service-side batching rather
than returning raw Mongo documents to Thymeleaf and silently breaking templates.

## 7. Required green tests before replacing deployment

1. New buyer + seller registration and BCrypt login.
2. Role separation: buyer cannot access seller/admin endpoints.
3. Food cart approval, menu changes, catalog filters, seller enabled/disabled.
4. Add same/multiple items, update quantity, clear cart, one-seller rule.
5. Checkout: immutable prices, discount math, all-or-nothing behavior, idempotency.
6. COD delivered -> PAID and seller order state machine restrictions.
7. Simulated online payment success + failure + cancellation/refund requested.
8. Delivered-only review eligibility and one review per target.
9. Favorites, notifications, reset-token invalidation.
10. All reports and dashboard aggregations consistent with original SQL results.
11. Thymeleaf page rendering for buyer, seller and admin with String IDs.
12. Docker startup connecting to a real MongoDB server and safe network outages.

## 8. Source snapshot and push safety

The original MySQL project is **not modified at runtime** by Phase 1.
The existing `application.properties` contains local demo credentials and the login
page describes demo accounts. Before any public GitHub push, remove/replace
hardcoded credentials and ensure the private MySQL dump/export is ignored.

The linked public GitHub repository was empty when verified, so do not describe
this phase as a branch deployed on GitHub; no commit or push has been made.
