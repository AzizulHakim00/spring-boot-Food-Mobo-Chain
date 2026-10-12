# Food Mobo Chain — Code audit and repair record (11 October 2026)

## Audit boundaries

Staging branch: `feature/mongodb-jwt-cloudinary-render`. The inspected
repository tree contained approximately 268 tracked files, including 142
application/controller/model/repository/service/template/JavaScript/migration/
workflow source files inspected in a source scan (about 8,950 source lines),
in addition to review of the main stylesheets, application configuration,
security filters, high-risk services and integration tests. This is **not**
a proof that every possible bug has been discovered.

The automated checks operate against a **disposable local MongoDB replica set**
in GitHub Actions. The audit did not delete, reimport or alter real Atlas
orders, users, food carts or Cloudinary objects.

## Confirmed problems and corrective changes

| Severity | Location | Finding | Corrective action |
|---|---|---|---|
| High — buyer reliability | `CartService`, `CartController`, `OrderController` | Removed MongoDB food/category/seller records could strand a buyer on cart/checkout error pages | Recover orphaned embedded cart lines, preserving valid items, and redirect checkout for review |
| Medium — cart UX | `CartController.remove` | Stale Remove action raised a global 400 page | Surface a normal flash message and redirect to cart |
| Medium — catalog | `CatalogService.menu` | Dereferenced deleted category | Skip unresolvable categories instead of throwing |
| Medium — favorites | `FavoriteService.toggleCart` | Dereferenced missing cart owner | Return unavailable validation error |
| High — account sessions | `User`, `AccountUserDetails`, `JwtService`, `JwtAuthFilter`, auth controllers and user/reset services | Older JWTs survived password changes, password reset and suspend/re-enable | Add signed account session version and compare current MongoDB version on every authenticated request; increment on credential/access changes |
| Medium — first-time carts | `CartService.getOrCreate` | Concurrent requests could create two different IDs for a new buyer's cart | Assign a deterministic `_id` per buyer for new baskets; migration index also declares unique buyerId |
| Medium — admin KPI | `ReportService.adminDashboard` | All PENDING payments, including normal COD, counted as awaiting online payment | Count only `PENDING_PAYMENT` orders with `SSLCOMMERZ` and `PENDING` |
| Low — seller KPI | `ReportService.sellerDashboard` | “Last 7 days” calculation used a sliding seven-day time period, unlike the seven calendar-day chart | Align the KPI to six days ago at midnight through today |
| Medium — product analytics | `ReportService.topFoods` | Different products sharing the same name were conflated | Group by food item snapshot ID plus name |
| Medium — link handling | `NotificationService` | Backslash and encoded separator patterns could create misleading off-site links | Tighten internal-link validation, with regression coverage |
| Medium — migration | `migration/mongo_indexes.js` | Multiple legacy discounts without normalized codes could fail a unique index | Apply a unique partial index only to string normalized codes; no automatic live backfill |
| Low — new demo databases | `StagingCatalogSeeder` | Some new demo collection uniqueness indexes were not guaranteed | Create cart-buyer, order-number and normalized-promo indexes during the one-time empty-database seed |

## Frontend and workflow protection

The automated source audit now checks:

- Every Thymeleaf template for missing local assets, duplicated static HTML IDs
  and nested/unclosed `form` elements.
- Every bundled stylesheet for balanced CSS blocks.
- Every JavaScript file for JavaScript syntax using `node --check`.
- Main user flows with role-aware HTTP requests, MongoDB inspection and CSRF.

These checks catch source integrity problems; they do not replace screenshots,
browser automation or responsive visual acceptance at many screen widths.

## Verification commands

```bash
python -m unittest discover -s migration/tests -v
python scripts/check_mongo_source.py
python scripts/check_frontend_source.py
for file in src/main/resources/static/js/*.js; do node --check "$file"; done
bash ./mvnw -B -ntp verify
bash scripts/ci_mongo_smoke.sh
docker build -t food-mobo-chain:ci .
```

The MongoDB smoke script creates an isolated database, verifies public/admin/
seller/buyer authorization, JWT role separation, stale-token revocation and
fresh login, buyer cart updates, multi-seller order splitting, promotions,
demo payment completion, catalog and seller image failure handling.

## Intentionally retained or outstanding limitations

1. **Online payment is a simulation**, not real SSLCOMMERZ, bKash, Nagad or
   bank settlement. A real gateway requires credentials, webhook signature
   verification, replay protection, refunds, reconciliation and separate
   gateway acceptance tests. Do not present demo completion as real payment.
2. Password-reset **email delivery is not configured** on staging.
3. Rate limiting, operational alerting, full browser accessibility audits and
   automatic backups/restore drills still need production hardening.
4. Legacy, already-deployed databases may have duplicate records or missing
   indexes. Apply a reviewed migration **after taking a backup**; do not run
   destructive automated migrations on live Atlas.
5. Cloudinary image uploads require a real authenticated seller-browser test
   with the actual staging account. Unit/integration tests deliberately
   simulate missing upload configuration without replacing existing media.
6. Authenticated visual responsive tests at 320, 375, 768, 1024, 1440+ px,
   on mobile browsers and with long food names, remain a manual/browser
   acceptance requirement. Static validation cannot certify pixel layout.
7. New functionality lives on a staging feature branch. The repository's
   Render Blueprint `render.yaml` still targets `main`; do not interpret
   staging checks as a successful production release.
8. Atomic checkouts use MongoDB transactions and optimistic version fields,
   but comprehensive adversarial load/idempotency tests are future work.

**Release gate:** deploy to Render staging only after the final branch's
GitHub unit, integration, frontend, migration and Docker jobs are green.
Confirm Render status=live, MongoDB readiness, and rerun the live-site
smoke test after deployment.
