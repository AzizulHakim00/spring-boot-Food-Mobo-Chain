# Food Mobo Chain — Staging QA and release checklist

**Scope:** GitHub branch `feature/mongodb-jwt-cloudinary-render` and Render staging at
`https://food-mobo-chain-staging.onrender.com`. All automated write-path tests run
against an isolated, disposable MongoDB replica set in GitHub Actions. **No tests
write demo orders or edit food carts on the live Atlas database.**

## Fixed in the October 2026 checkout revision

- Legacy demo promotions were imported with `code` but without `codeNormalized`.
  The discount service now prefers the normalized field and falls back to the
  original `code` for pre-existing records. Fresh demo imports populate both.
  Current offer definitions include WELCOME15 (15%, minimum ৳300, max discount
  ৳150) and FMC100 (৳100, minimum ৳700). Offers must also be active and in date.
- Checkout promo errors now appear under **Promo code**, not under order notes.
  A read-only, BUYER-protected preview API shows the same server-calculated
  amount; server-side checkout revalidates the code and prices in the order
  transaction. Neither the browser nor promo preview is the financial authority.
- Rebuilt the dark order summary with individual seller groups, a two-column
  item/price grid, nonbreaking prices, separate delivery fees, discount and
  final total, and narrow-screen CSS. No delivery fee is silently waived.
- Mixed-vendor checkout retains one basket but creates separate seller-owned
  orders in a single MongoDB transaction, dividing one promo across the
  vendor subtotals exactly once. The buyer sees all individual orders.
- Seller menu and cover-image updates use a single CSRF-protected multipart
  save. Cloudinary failures display field errors without replacing images or
  creating partial menu data.

## Authentication and authorization

- Browser: custom POST `/login` validates BCrypt credentials, issues an
  HS256 JWT with an account subject and 30-minute lifetime, stored in an
  HttpOnly, Secure, SameSite=Lax `FMC_ACCESS` cookie.
- Requests: a Spring Security filter verifies the token, loads current user
  details from MongoDB, and reapplies current enabled state and role.
- API: `/api/**` authenticates exclusively via `Authorization: Bearer`; the
  browser cookie is deliberately ignored.
- Roles: `/admin/**` is ADMIN; `/seller/**` is SELLER; cart, checkout, orders
  and payments are BUYER. MVC modifications require CSRF protection.
- Logout clears the browser cookie; it is not an immediate server-side
  revocation list for already-issued bearer JWTs.

## Caching and data consistency

- `@EnableCaching` and Caffeine are enabled in `CacheConfig`.
- Cache names: `publicCarts`, `featuredFoods`, `categories`, each with a
  maximum of 200 entries and three-minute write expiry.
- Catalog mutations evict relevant caches through a
  `TransactionAwareCacheManagerProxy` after successful commits.
- Authentication, shopping carts, orders and payment states are **not cached**.
- Monetary values are `BigDecimal` as MongoDB Decimal128; dates are stored in
  UTC and displayed in Asia/Dhaka. Checkout relies on a MongoDB replica set.

## Automated verification matrix

| Area | Primary automated check |
|---|---|
| Build | `mvnw verify`, JUnit, Docker image build |
| Authentication | JWT signature and tamper tests, browser JWT cookie, bearer token, logout |
| Authorization | Admin/seller/buyer role isolation; API cookies rejected; CSRF protected |
| Catalog | 42 bundled food fixtures, public catalog rendering and seller-created food |
| Images | Seller multipart validation, safe upload failure, cart cover persistence |
| Discounts | Normalized and legacy promo lookup; expiry, minimum, cap, duplicate checks |
| Checkout | Authenticated mixed-seller basket, seller-specific orders and shared discount |
| Invalid promo | Field-level error; zero orders persisted |
| Demo payment | Pending online order, simulated completion, paid status persisted |
| Caching | Cache manager configuration, public caches, eviction and uncached sensitive data |
| Data | Replica-set-backed MongoDB integration, transactional order writes |
| Staging | Readiness check, public HTTP smoke, static assets and Render deployment logs |

GitHub Actions: `Verify MongoDB, JWT, Cloudinary and Docker` and
`Verify live Render staging`. Keep the most recent job URL and deploy status
with project submission evidence. GitHub tests cannot prove a real Cloudinary
upload through a logged-in browser on the latest public site.

## Not production-ready without further work

1. **Payments:** online payment is still a simulation. Integrate a real gateway
   and authenticated webhooks, refunds, reconciliation and fraud safeguards.
2. **Reset email:** forgot-password does not deliver email yet.
3. **Checkout idempotency:** protect rapid retries/double clicks with a
   server-side idempotency key and load/concurrency tests.
4. **Security:** rotate any credentials shared in chats, provision a dedicated
   least-privilege Atlas user and consider short-lived bearer revocation.
5. **Live acceptance:** test Cloudinary upload with a real seller login,
   admin/seller/buyer browsers and responsive mobile layouts after deploy.
6. **Operations:** verify production backup/restore, Render readiness
   monitoring, data privacy controls and operational alerts.

**Do not claim the application is fully production-ready based solely on
successful CI.** The demo staging platform can be deployed and exercised
without writing test purchases to live accounts.
