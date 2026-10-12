# Food Mobo Chain - offline migration audit

Input: supplied `food_mobo_chain.sql` (source MySQL 8 dump); output: Extended JSON Lines for MongoDB import. The input was not modified.

## Actual record counts (verified)

| Existing SQL table | Rows | MongoDB destination |
|---|---:|---|
| `users` | 10 | `users` |
| `categories` | 8 | `categories` |
| `food_carts` | 8 | `foodCarts` |
| `food_items` | 42 | `foodItems` |
| `shopping_carts` | 1 | `shoppingCarts` |
| `cart_items` | 0 | `shoppingCarts.items[]` |
| `customer_orders` | 1 | `orders` |
| `order_items` | 1 | `orders.items[]` |
| `payments` | 1 | `orders.payment` |
| `deliveries` | 1 | `orders.delivery` |
| `discounts` | 2 | `discounts` |
| `reviews` | 2 | `reviews` |
| `favorite_foods` | 0 | `favoriteFoods` |
| `favorite_carts` | 0 | `favoriteCarts` |
| `notifications` | 8 | `notifications` |
| `password_reset_tokens` | 0 | `passwordResetTokens` |
| **Total** | **85** | **82 MongoDB root documents, 12 collections** |

These counts match the supplied SQL dump. Three legacy root records become
embedded in the one order (`order_items`, `payments`, `deliveries`). There were
no cart-item records to embed in this sample, so a synthetic populated-cart
case was added to unit tests.

## One anonymized order reconciliation

* Order items subtotal: BDT 780.00 (3 x BDT 260.00)
* Delivery fee: BDT 60.00
* Discount: BDT 0.00
* Total: BDT 840.00
* Payment amount: BDT 840.00, method COD, status PAID
* Delivery status: DELIVERED

The exporter checks foreign-key-style references, duplicate identifiers,
financial totals, review target integrity, cart quantities, and other unique keys.
Five Python unit tests passed, including a live private-dump export verification,
intentional corruption tests, and a test that changing a current food price does
not modify the old order snapshot price.

## Remaining verification (not performed)

* No live Atlas write or Atlas transaction test was run.
* No Java services/controllers/repositories were migrated in this phase.
* No Docker build, Render deployment, or Spring Boot integration test was run.
* Confirm the original SQL `DATETIME` timezone before importing actual dates.
* No Cloudinary or JWT changes were made in this phase.
