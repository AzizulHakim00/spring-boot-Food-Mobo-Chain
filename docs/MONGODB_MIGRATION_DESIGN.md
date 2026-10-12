# Food Mobo Chain: MongoDB migration design and compatibility review

**Status: Phase 2 source conversion candidate assembled; see `PHASE2_STATUS.md`.**
**Important:** The former MySQL/JPA application source has now been adapted for MongoDB in Phase 2, but Java compilation and live Atlas integration are **not verified**. The following sections retain the original design rationale and some historical planning wording. Do not deploy until the full Maven build and end-to-end staging tests pass.

## 1. What was audited

Source: `FoodMoboChain.zip`, the 16-table `food_mobo_chain.sql`, and the Q4 JWT/MongoDB sample.
Actual Java persistence shape: 16 JPA entities / 16 SQL tables; 16 repository interfaces;
services and Thymeleaf controllers rely heavily on `Long` IDs and JPA object relationships.
The migration creates 12 MongoDB collections.

| MySQL | MongoDB | Storage decision | Purpose |
|---|---|---|---|
| users | users | Root document | Login credentials, roles, account status |
| categories | categories | Root document | Category filter and URLs |
| food_carts | foodCarts | Root document, `ownerId` | One seller owns one cart |
| food_items | foodItems | Root document, `foodCartId` + `categoryId` | Price/availability updates and search |
| shopping_carts + cart_items | shoppingCarts | Root doc + embedded `items[]` | Atomic cart changes; buyer has one cart |
| customer_orders + order_items | orders | Root doc + immutable `items[]` | Historical item and price snapshots |
| payments | orders.payment | Embedded | One payment per order in current domain |
| deliveries | orders.delivery | Embedded | One delivery per order in current domain |
| discounts | discounts | Root document | Global coupon lifecycle |
| reviews | reviews | Root document, exactly one target | Food/cart reviews and moderation |
| favorite_foods | favoriteFoods | Root document, pair-unique | Favorites grow independently |
| favorite_carts | favoriteCarts | Root document, pair-unique | Favorites grow independently |
| notifications | notifications | Root document | Independent event/feed, paginated |
| password_reset_tokens | passwordResetTokens | Root + TTL index | Token-hash expiration |

**Not embedded:** seller menus into `foodCarts` (independently searchable and frequently changed);
reviews/notifications (unbounded collections); discounts (independent campaign state).

## 2. IDs and links

Existing MySQL numeric keys are mapped deterministically, as **string `_id` values**:
`users:2`, `foodCarts:1`, `foodItems:2`, `orders:1`, etc. New application-generated IDs should
also be strings, such as prefixed UUIDs (e.g. `orders:<uuid>`), rather than requiring numeric
sequences. Every `Long` Java ID parameter, template link, and DTO needs coordinated replacement
with `String`, not only the MongoDB entity field.

Foreign keys become simple string reference fields (e.g. `buyerId`, `ownerId`,
`foodCartId`), not `@DBRef`. Fetch referenced documents deliberately in services and
assemble view DTOs; avoid implicit database joins or lazy references in Thymeleaf.

**Snapshot exception:** `orderItems.foodItemIdSnapshot` had no FK in SQL. Keep the
immutable name/image/unit-price snapshot even if food is later deleted. The offline converter
preserves this with `foodItemIdSnapshot` as an optional historical reference.

## 3. Financial data, dates, and images

* Prices, fees, discounts, totals and payment amounts: MongoDB **Decimal128**;
  Java `BigDecimal` with `@Field(targetType = FieldType.DECIMAL128)` on persisted fields.
* The original SQL `DATETIME(6)` has **no time zone**. The exporter accepts an
  `--source-timezone` argument (default `Asia/Dhaka`, inferred from the original
  JDBC configuration). Confirm the actual clock used in the dump first.
  Conversion writes MongoDB UTC `$date`; precision is reduced from microseconds to
  milliseconds, which is a BSON Date limitation.
* New app timestamps should preferably use `Instant` stored in UTC; local display
  time should be converted at the view boundary.
* Preserve existing `/images/...` strings for bundled WebP demo assets. Future seller
  images should use a durable cloud image host (e.g. Cloudinary), with `imagePublicId`
  and `imageUrl`; **never** write uploaded images to Render's ephemeral filesystem.

## 4. Service and repository compatibility (DO NOT SKIP)

| Current code | MongoDB refactor |
|---|---|
| `JpaRepository<...,Long>` | `MongoRepository<...,String>` for 12 root collections |
| `@Entity`, JPA `@Table`, `@JoinColumn`, cascades | `@Document`, `@Id`, `@Indexed`, embedded types; no JPA lifecycle callbacks |
| `@PrePersist/@PreUpdate` | Spring Data auditing callbacks (`@CreatedDate`, `@LastModifiedDate`) or explicit timestamps |
| JPA mapped entity graph + Thymeleaf | IDs + explicit service query + view DTOs |
| JPQL `@Query` search with many joins | `MongoTemplate` dynamic `Criteria`, service-level catalog visibility checks, denormalization only where justified |
| CartService separate CartItemRepository | Single shopping-cart document with item list; atomic updates and versioning |
| OrderService separate OrderItemRepository | Immutable embedded order item snapshots; aggregation for top foods |
| PaymentService PaymentRepository | Embedded payment update through OrderRepository/MongoTemplate; compare-and-set status |
| DeliveryRepository | Embedded delivery; admin views derive delivery rows from orders |
| `@Transactional` on relational write paths | Explicit MongoDB transaction manager for true multi-document workflows; test Atlas tier first |
| `Long foodId`, `Long cartId`, path variables | `String` IDs in all controllers/DTOs/templates/tests |
| `DataInitializer` SQL demo data | Optional `dev` profile seed only, disabled in Render `prod` |
| Spring Security BCrypt users via JPA | Same BCrypt hashes in users Mongo docs; user-details lookup by normalized email |

No production schema should rely on MongoDB DBRef auto-fetch. `MongoRepository` query
methods do not automatically recreate JPA join semantics; reports use MongoDB aggregation.

## 5. Preserved business rules

1. Users have exactly one role: BUYER, SELLER or ADMIN; disabled users cannot act.
2. One seller owns one food cart; cart slug is unique.
3. A buyer has one active shopping cart and cannot mix sellers in checkout.
4. Cart line quantity range is 1..20; reject invalid new inputs in the Java refactor.
5. Only approved/open carts with enabled owners and visible/available items can be ordered.
6. Server recalculates prices and fees from authoritative food documents at checkout.
7. `orders.items[]` captures immutable item names, prices, images and quantities.
8. Order status machine remains PENDING_PAYMENT/CONFIRMED/ACCEPTED/COOKING/READY/
   ON_THE_WAY/DELIVERED/CANCELLED with explicit allowed transitions.
9. An order contains exactly one payment and delivery, consistent with today's domain.
10. COD payment changes to PAID only when the delivery is completed.
11. Paid cancellation needs a REFUND_REQUIRED state and eventual reconciliation.
12. Buyer can review only previously delivered food / food cart, one review per target.
13. Password reset tokens remain SHA-256 hashes, with expiry enforced in application
    and an optional MongoDB TTL cleanup index.
14. Date/number fields must not be stored as arbitrary JavaScript floating-point numbers.

## 6. Transaction safety and concurrency

Embedding order + payment + delivery offers atomic updates *within one order document*.
It does not automatically make separate writes to the cart, order, notifications, or
seller data atomic. The current checkout has a multi-entity `@Transactional` method.

The final application should use a `MongoTransactionManager` where supported by Atlas;
explicitly verify transaction support on the actual free cluster, rather than assuming
that all MongoDB tiers behave identically. In addition:

* Add a `@Version` field to cart and order root documents for optimistic concurrency.
* Use atomic conditional status updates, e.g. update order only if current status is
  the expected status. Avoid lost updates between payment callback and seller action.
* Use a checkout idempotency key / unique checkout request ID to avoid duplicate orders
  when users double-submit or clients retry after timeouts.
* Consider an outbox / eventual retry for notifications and cloud uploads: MongoDB
  transactions **cannot** roll back Cloudinary or external payment gateway calls.
* Never clear the buyer's cart before durable order creation; require transaction,
  or use a clearly designed recoverable workflow with idempotent retry.

## 7. Index and validation implementation

Run `migration/mongo_indexes.js` on a **new empty database** after import.
Use canonical keys `emailNormalized` (lowercase), `codeNormalized` (uppercase),
`nameNormalized` (casefolded). Maintain them on every save and always query
using those keys. Key indexes include unique email/slugs/order numbers,
unique favorites/reviews, buyer/order lookup indexes, seller order history,
public catalog filters, and password reset expiry (TTL).

Unique indexes prevent duplicate keys, but **not** broken references or invalid
business values. Service-layer validation and optional MongoDB `$jsonSchema`
validators still need to be implemented in the Java refactor.

## 8. Free hosting constraints

Atlas Free offers a small database and limited throughput; Render Free has an
**ephemeral filesystem** and sleeps on inactivity. Caffeine is an in-process cache
only, so its content is lost across Render restarts. Never cache payment state,
checkout eligibility, or user authorization data without carefully controlled
invalidation. Cloud image uploads must be persisted outside Render.

For a student/demo site, keep a single Spring Boot/Thymeleaf container;
MongoDB Atlas stores data, Cloudinary stores uploaded images. No Redis or
microservices are necessary at this stage. Do not run migration automatically
on each Docker deployment.

## 9. Verification gates before replacing JPA

**Completed in this phase:** offline SQL parse, 16-to-12 collection export,
referential check, finance reconciliation, unique-key preflight, index plan,
repeatable Python tests.

**Still required (next phase):** Java `@Document` entities; new Mongo repositories,
query replacements and aggregation; service refactor; controllers/DTOs/templates
String-ID migration; JWT integration; Cloudinary uploads; Caffeine; MongoDB
integration tests against a real cluster; Docker build and Render deployment.

Do **not** claim the Spring Boot application is MongoDB-ready until all required
integration tests pass with the MongoDB configuration.
