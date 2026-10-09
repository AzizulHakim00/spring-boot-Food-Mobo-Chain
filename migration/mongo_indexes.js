// Food Mobo Chain MongoDB index definitions. Run AFTER mongoimport in a fresh db.
// Usage: mongosh "mongodb+srv://<...>/food_mobo_chain" --file migration/mongo_indexes.js
// `db` is the database selected by the connection URI; do not hardcode secrets here.

const index = (collection, keys, options = {}) =>
    db.getCollection(collection).createIndex(keys, options);

// Accounts and seller/catalog identity
index('users', {emailNormalized: 1}, {unique: true, name: 'uq_user_email_normalized'});
index('users', {role: 1, enabled: 1}, {name: 'ix_users_role_enabled'});
index('categories', {nameNormalized: 1}, {unique: true, name: 'uq_category_name_normalized'});
index('categories', {slug: 1}, {unique: true, name: 'uq_category_slug'});
index('categories', {active: 1, name: 1}, {name: 'ix_active_categories'});
index('foodCarts', {ownerId: 1}, {unique: true, name: 'uq_food_cart_owner'});
index('foodCarts', {slug: 1}, {unique: true, name: 'uq_food_cart_slug'});
index('foodCarts', {approved: 1, open: 1, createdAt: -1}, {name: 'ix_public_food_carts'});
index('foodItems', {foodCartId: 1, slug: 1}, {unique: true, name: 'uq_food_slug_per_cart'});
index('foodItems', {foodCartId: 1, archived: 1, name: 1}, {name: 'ix_seller_foods'});
index('foodItems', {categoryId: 1, available: 1, archived: 1}, {name: 'ix_food_category_visible'});
index('foodItems', {featured: 1, available: 1, archived: 1, createdAt: -1}, {name: 'ix_featured_foods'});

// Shopping cart: one cart per buyer. Items are an embedded array.
index('shoppingCarts', {buyerId: 1}, {unique: true, name: 'uq_shopping_cart_buyer'});

// Orders: item snapshots, payment and delivery are embedded in orders.
index('orders', {orderNumber: 1}, {unique: true, name: 'uq_order_number'});
index('orders', {buyerId: 1, createdAt: -1}, {name: 'ix_buyer_order_history'});
index('orders', {foodCartId: 1, createdAt: -1}, {name: 'ix_seller_order_history'});
index('orders', {status: 1, createdAt: -1}, {name: 'ix_status_date'});
index('orders', {'payment.status': 1, createdAt: -1}, {name: 'ix_payment_state'});
index('orders', {'payment.transactionId': 1}, {
    unique: true, name: 'uq_payment_transaction_id',
    partialFilterExpression: {'payment.transactionId': {$type: 'string'}}
});

// Discounts, reviews, favorites and user notifications
index('discounts', {codeNormalized: 1}, {unique: true, name: 'uq_discount_code_normalized'});
index('discounts', {active: 1, startsAt: 1, endsAt: 1}, {name: 'ix_active_discounts'});
index('reviews', {buyerId: 1, foodItemId: 1}, {
    unique: true, name: 'uq_buyer_food_review',
    partialFilterExpression: {foodItemId: {$type: 'string'}}
});
index('reviews', {buyerId: 1, foodCartId: 1}, {
    unique: true, name: 'uq_buyer_cart_review',
    partialFilterExpression: {foodCartId: {$type: 'string'}}
});
index('reviews', {foodItemId: 1, approved: 1, hidden: 1, createdAt: -1}, {name: 'ix_food_reviews'});
index('reviews', {foodCartId: 1, approved: 1, hidden: 1, createdAt: -1}, {name: 'ix_cart_reviews'});
index('favoriteFoods', {userId: 1, foodItemId: 1}, {unique: true, name: 'uq_favorite_food'});
index('favoriteCarts', {userId: 1, foodCartId: 1}, {unique: true, name: 'uq_favorite_cart'});
index('notifications', {userId: 1, createdAt: -1}, {name: 'ix_user_notifications'});
index('notifications', {userId: 1, read: 1}, {name: 'ix_user_unread'});
index('passwordResetTokens', {tokenHash: 1}, {unique: true, name: 'uq_reset_token_hash'});
index('passwordResetTokens', {expiresAt: 1}, {expireAfterSeconds: 0, name: 'ttl_expired_reset_tokens'});
print('Food Mobo Chain indexes created. Check logs above for any duplicate-key errors.');
