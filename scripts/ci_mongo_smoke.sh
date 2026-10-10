#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="foodmobo-ci-mongo-${GITHUB_RUN_ID:-local}"
BASE_URL="http://127.0.0.1:10000"
LOG_FILE="${RUNNER_TEMP:-/tmp}/foodmobo-app-smoke.log"
APP_PID=""

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "MongoDB smoke check failed; final application log lines:" >&2
    if [ -f "$LOG_FILE" ]; then tail -n 100 "$LOG_FILE" >&2; fi
  fi
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true; fi
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  exit "$status"
}
trap cleanup EXIT

# A disposable, local replica set is needed because checkout uses MongoDB transactions.
docker run -d --name "$CONTAINER_NAME" -p 127.0.0.1:27017:27017 mongo:7 \
  --replSet rs0 --bind_ip_all >/dev/null

for attempt in $(seq 1 45); do
  if docker exec "$CONTAINER_NAME" mongosh --quiet --eval 'db.adminCommand({ ping: 1 }).ok' 2>/dev/null | grep -q '^1$'; then break; fi
  if [ "$attempt" -eq 45 ]; then echo 'MongoDB did not start' >&2; exit 1; fi
  sleep 2
done

docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})' >/dev/null

for attempt in $(seq 1 45); do
  if docker exec "$CONTAINER_NAME" mongosh --quiet --eval 'db.hello().isWritablePrimary' 2>/dev/null | grep -q '^true$'; then break; fi
  if [ "$attempt" -eq 45 ]; then echo 'MongoDB replica set did not become primary' >&2; exit 1; fi
  sleep 2
done

# Deliberately use an unrelated URI database to verify that SPRING_MONGODB_DATABASE isolates this app.
export MONGODB_URI='mongodb://127.0.0.1:27017/store_manager?directConnection=true&replicaSet=rs0'
export SPRING_MONGODB_DATABASE=food_mobo_chain_test
export APP_SEED_TARGET_DATABASE=food_mobo_chain_test
export APP_SEED_DEMO_CATALOG=true
# CI-only password, never used on Atlas or Render.
export APP_SEED_ADMIN_PASSWORD='ci-only-admin-password-2026-isolated-test'
export APP_SEED_BUYER_PASSWORD='ci-only-buyer-password-2026-isolated-test'
export APP_SEED_SELLER_PASSWORD='ci-only-seller-password-2026-isolated-test'
export SPRING_PROFILES_ACTIVE=prod
# Only the CI loopback HTTP transport needs an insecure cookie; Render stays HTTPS-only.
export APP_SECURE_COOKIES=false
export PORT=10000
java -jar target/food-mobo-chain-1.0.0.jar >"$LOG_FILE" 2>&1 &
APP_PID=$!

for attempt in $(seq 1 90); do
  if curl -fsS --max-time 4 "$BASE_URL/ready" | grep -q '"UP"'; then break; fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then echo 'Spring Boot process exited before readiness' >&2; exit 1; fi
  if [ "$attempt" -eq 90 ]; then echo 'Application failed to become Mongo-ready' >&2; exit 1; fi
  sleep 2
done

for path in /health /ready / /login /foods /food-carts; do
  curl -fsS --max-time 12 -o /dev/null "$BASE_URL$path" || { echo "Public path failed: $path" >&2; exit 1; }
done

# Validate that public food data is present and not silently loaded from the wrong database.
food_count=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval "db.getSiblingDB('food_mobo_chain_test').foodItems.countDocuments({})" | tail -n 1)
if [ "$food_count" != 42 ]; then
  echo "Sanitized MongoDB fixture missing: food count was $food_count (expected 42)" >&2
  exit 1
fi
CATALOG_HTML="${RUNNER_TEMP:-/tmp}/foodmobo-seed-catalog.html"
curl -fsS --max-time 15 -o "$CATALOG_HTML" "$BASE_URL/foods"
if ! grep -q 'class="food-card-link"' "$CATALOG_HTML"; then
  echo 'Seeded food products did not render in Thymeleaf' >&2
  exit 1
fi
# Every new fixture needs a normalized discount code, even though legacy records are still supported.
discount_seed_check=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval "
const discounts=db.getSiblingDB('food_mobo_chain_test').discounts.find({}).toArray();
print(discounts.length===2 && discounts.every(x=>x.codeNormalized===x.code) ? 'PASS' : 'FAIL');" | tail -n 1)
[ "$discount_seed_check" = PASS ] || { echo 'Normalized demo discount seed failed' >&2; exit 1; }
echo 'Sanitized catalog, normalized discounts and MongoDB database-name isolation passed.'

assert_status() {
  local method="$1" path="$2" expected="$3"
  local actual
  actual=$(curl --max-time 12 -sS -o /dev/null -w '%{http_code}' -X "$method" "$BASE_URL$path")
  if [ "$actual" != "$expected" ]; then
    echo "Unexpected $method $path HTTP status: $actual (expected $expected)" >&2
    exit 1
  fi
}

# Verify a protected MVC route, protected API, and CSRF rejection without a token.
assert_status GET /admin 302
assert_status GET /api/auth/me 401
assert_status POST /logout 403

# Register a buyer through the real Thymeleaf form (including its CSRF token),
# authenticate through both supported JWT mechanisms and verify logout.
COOKIE_FILE="${RUNNER_TEMP:-/tmp}/foodmobo-smoke-cookies.txt"
FORM_HTML="${RUNNER_TEMP:-/tmp}/foodmobo-smoke-form.html"
API_JSON="${RUNNER_TEMP:-/tmp}/foodmobo-api-auth.json"
USER_EMAIL='ci-buyer@example.invalid'
USER_PASSWORD='DemoPassword123!'

csrf_from_html() {
  python3 - "$1" <<'PY'
from html.parser import HTMLParser
import sys
class CsrfParser(HTMLParser):
    csrf = None
    def handle_starttag(self, tag, attrs):
        fields = dict(attrs)
        if tag.lower() == "input" and fields.get("name") == "_csrf":
            self.csrf = fields.get("value")
p = CsrfParser()
with open(sys.argv[1], encoding="utf-8") as f:
    p.feed(f.read())
if not p.csrf:
    raise SystemExit("Thymeleaf did not emit a CSRF hidden field")
print(p.csrf)
PY
}

curl -fsS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o "$FORM_HTML" "$BASE_URL/register"
REGISTER_CSRF=$(csrf_from_html "$FORM_HTML")
REG_STATUS=$(curl -sS --max-time 20 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$REGISTER_CSRF" \
  --data-urlencode 'fullName=CI Buyer' \
  --data-urlencode "email=$USER_EMAIL" \
  --data-urlencode 'phone=01712345678' \
  --data-urlencode "password=$USER_PASSWORD" \
  --data-urlencode "confirmPassword=$USER_PASSWORD" \
  "$BASE_URL/register")
if [ "$REG_STATUS" != 302 ]; then echo "Buyer registration failed: $REG_STATUS" >&2; exit 1; fi

API_STATUS=$(curl -sS --max-time 20 -o "$API_JSON" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  --data "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}" \
  "$BASE_URL/api/auth/login")
if [ "$API_STATUS" != 200 ]; then echo "API login failed: $API_STATUS" >&2; exit 1; fi
BEARER_TOKEN=$(python3 - "$API_JSON" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as f:
    payload = json.load(f)
print(payload['accessToken'])
PY
)
if [ -z "$BEARER_TOKEN" ]; then echo 'JWT was not issued' >&2; exit 1; fi
API_ME=$(curl -fsS --max-time 15 -H "Authorization: Bearer $BEARER_TOKEN" "$BASE_URL/api/auth/me")
if ! printf '%s' "$API_ME" | grep -q 'ROLE_BUYER'; then echo 'Bearer JWT role verification failed' >&2; exit 1; fi

curl -fsS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o "$FORM_HTML" "$BASE_URL/login"
LOGIN_CSRF=$(csrf_from_html "$FORM_HTML")
LOGIN_STATUS=$(curl -sS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$LOGIN_CSRF" --data-urlencode "username=$USER_EMAIL" \
  --data-urlencode "password=$USER_PASSWORD" "$BASE_URL/login")
if [ "$LOGIN_STATUS" != 302 ]; then echo "Thymeleaf login failed: $LOGIN_STATUS" >&2; exit 1; fi
if ! grep -q 'FMC_ACCESS' "$COOKIE_FILE"; then echo 'HttpOnly login cookie missing' >&2; exit 1; fi
curl -fsS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o /dev/null "$BASE_URL/profile"

curl -fsS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o "$FORM_HTML" "$BASE_URL/profile"
LOGOUT_CSRF=$(csrf_from_html "$FORM_HTML")
LOGOUT_STATUS=$(curl -sS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$LOGOUT_CSRF" "$BASE_URL/logout")
if [ "$LOGOUT_STATUS" != 302 ]; then echo "Logout failed: $LOGOUT_STATUS" >&2; exit 1; fi
LOGGED_OUT_STATUS=$(curl -sS --max-time 15 -b "$COOKIE_FILE" -c "$COOKIE_FILE" -o /dev/null -w '%{http_code}' "$BASE_URL/profile")
if [ "$LOGGED_OUT_STATUS" != 302 ]; then echo "Logout did not revoke browser cookie: $LOGGED_OUT_STATUS" >&2; exit 1; fi

# Exercise authenticated dashboards against actual disposable MongoDB data.
# This catches failures after successful login which public smoke checks cannot see.
check_role_dashboard() {
  local email="$1" password="$2" route="$3" extra_route="$4"
  local cookies form body csrf login_status status
  cookies=$(mktemp)
  form=$(mktemp)
  body=$(mktemp)
  curl -fsS --max-time 15 -b "$cookies" -c "$cookies" -o "$form" "$BASE_URL/login"
  csrf=$(csrf_from_html "$form")
  login_status=$(curl -sS --max-time 20 -b "$cookies" -c "$cookies" -o /dev/null -w '%{http_code}' \
    --data-urlencode "_csrf=$csrf" \
    --data-urlencode "username=$email" \
    --data-urlencode "password=$password" "$BASE_URL/login")
  if [ "$login_status" != 302 ] || ! grep -q 'FMC_ACCESS' "$cookies"; then
    echo "Role login failed for $email: HTTP $login_status" >&2
    exit 1
  fi
  for page in "$route" "$extra_route"; do
    status=$(curl -sS --max-time 20 -b "$cookies" -c "$cookies" -o "$body" -w '%{http_code}' "$BASE_URL$page")
    if [ "$status" != 200 ]; then
      echo "Authenticated dashboard $page failed for $email: HTTP $status" >&2
      exit 1
    fi
    if ! grep -qi '<html' "$body"; then
      echo "Authenticated dashboard $page did not render HTML for $email" >&2
      exit 1
    fi
  done
  # A valid JWT must never grant a role belonging to another account class.
  if [ "$email" = 'admin@foodmobo.local' ]; then
    curl -fsS --max-time 15 -b "$cookies" -c "$cookies" -o "$body" "$BASE_URL/admin/discounts"
    grep -q 'FMC100' "$body" || { echo 'Admin discounts page missing seeded FMC100 offer' >&2; exit 1; }
    rejected=$(curl -sS --max-time 12 -b "$cookies" -o /dev/null -w '%{http_code}' "$BASE_URL/seller")
  else
    rejected=$(curl -sS --max-time 12 -b "$cookies" -o /dev/null -w '%{http_code}' "$BASE_URL/admin")
  fi
  [ "$rejected" = 403 ] || { echo "JWT role isolation failed for $email: $rejected" >&2; exit 1; }
  if ! grep -q 'action="/logout"' "$body"; then
    echo "Dashboard missing sign-out form for $email" >&2
    exit 1
  fi
  csrf=$(csrf_from_html "$body")
  local logout_status after_status
  logout_status=$(curl -sS --max-time 15 -b "$cookies" -c "$cookies" -o /dev/null -w '%{http_code}' \
    --data-urlencode "_csrf=$csrf" "$BASE_URL/logout")
  after_status=$(curl -sS --max-time 15 -b "$cookies" -c "$cookies" -o /dev/null -w '%{http_code}' "$BASE_URL$route")
  if [ "$logout_status" != 302 ] || [ "$after_status" != 302 ]; then
    echo "Sign out failed for $email: logout=$logout_status protected-after-logout=$after_status" >&2
    exit 1
  fi
  rm -f "$cookies" "$form" "$body"
  echo "Authenticated dashboard and logout smoke passed for $email."
}
check_role_dashboard 'admin@foodmobo.local' "$APP_SEED_ADMIN_PASSWORD" /admin /admin/reports
check_role_dashboard 'seller1@foodmobo.local' "$APP_SEED_SELLER_PASSWORD" /seller /seller/menu

# Seller workflow regression: authenticated menu creation, cover update and authorized upload.
# This intentionally uses disposable CI MongoDB; never writes to Render/Atlas.
SELLER_COOKIES=$(mktemp)
SELLER_FORM=$(mktemp)
curl -fsS --max-time 15 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/login"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
SELLER_LOGIN=$(curl -sS --max-time 15 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$SELLER_CSRF" --data-urlencode 'username=seller1@foodmobo.local' \
  --data-urlencode "password=$APP_SEED_SELLER_PASSWORD" "$BASE_URL/login")
[ "$SELLER_LOGIN" = 302 ] || { echo "Seller login for menu test failed: $SELLER_LOGIN" >&2; exit 1; }

curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller/menu/new"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
SELLER_CREATE=$(curl -sS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" -w '%{http_code}' \
  --data-urlencode "_csrf=$SELLER_CSRF" --data-urlencode 'name=CI Seller Test Food' \
  --data-urlencode 'categoryId=categories:1' --data-urlencode 'description=CI sample food to verify seller form processing.' \
  --data-urlencode 'price=125.00' --data-urlencode 'image=/images/foods/beef-rice.webp' \
  --data-urlencode 'available=true' "$BASE_URL/seller/menu/new")
[ "$SELLER_CREATE" = 302 ] || { echo "Seller menu create failed: $SELLER_CREATE"; head -c 400 "$SELLER_FORM"; exit 1; }
SELLER_FOOD_ID=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "const f=db.getSiblingDB('food_mobo_chain_test').foodItems.findOne({foodCartId:'foodCarts:1',name:'CI Seller Test Food'}); print(f ? f._id : 'MISSING')" | tail -n 1)
[ "$SELLER_FOOD_ID" != MISSING ] || { echo "New seller menu food not saved" >&2; exit 1; }

curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller/food-cart"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
SELLER_COVER_UPDATE=$(curl -sS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" -w '%{http_code}' \
  --data-urlencode "_csrf=$SELLER_CSRF" --data-urlencode 'name=Dhaka Biryani House' \
  --data-urlencode 'description=Freshly prepared biryani and rice meals for customers in Dhaka.' \
  --data-urlencode 'location=Dhanmondi 27' --data-urlencode 'cuisine=Rice & Bangladeshi' \
  --data-urlencode 'coverImage=/images/carts/street-bite.webp' \
  --data-urlencode 'deliveryFee=50' --data-urlencode 'estimatedDeliveryMinutes=35' "$BASE_URL/seller/food-cart")
[ "$SELLER_COVER_UPDATE" = 302 ] || { echo "Seller cover image update failed: $SELLER_COVER_UPDATE"; head -c 400 "$SELLER_FORM"; exit 1; }
SELLER_COVER=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "const c=db.getSiblingDB('food_mobo_chain_test').foodCarts.findOne({_id:'foodCarts:1'}); print(c ? c.coverImage : 'MISSING')" | tail -n 1)
[ "$SELLER_COVER" = '/images/carts/street-bite.webp' ] || { echo "Seller cover image not persisted: $SELLER_COVER" >&2; exit 1; }

# Empty file must reach the authorized upload handler and return 400 (not a 403 CSRF error).
curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller/food-cart"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
SELLER_UPLOAD_STATUS=$(curl -sS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" \
  -o "$SELLER_FORM" -w '%{http_code}' -F "_csrf=$SELLER_CSRF" \
  -F 'kind=carts' -F 'file=@/dev/null;filename=empty.jpg;type=image/jpeg' "$BASE_URL/seller/uploads/images")
[ "$SELLER_UPLOAD_STATUS" = 400 ] || { echo "Seller image upload blocked before validation: $SELLER_UPLOAD_STATUS" >&2; exit 1; }
# In CI Cloudinary is intentionally unconfigured. A real multipart selection must
# render an image field error (HTTP 200), never 403, and must not create a DB item.
curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller/menu/new"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
SELLER_MULTIPART=$(curl -sS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" -w '%{http_code}' \
  -F "_csrf=$SELLER_CSRF" -F 'name=CI Pending Cloudinary Upload' \
  -F 'categoryId=categories:1' -F 'description=Multipart seller food image saved in one request.' \
  -F 'price=110.00' -F 'available=true' \
  -F 'imageFile=@src/main/resources/static/images/foods/beef-rice.webp;type=image/webp' \
  "$BASE_URL/seller/menu/new")
[ "$SELLER_MULTIPART" = 200 ] || { echo "Multipart seller create incorrectly rejected: $SELLER_MULTIPART"; head -c 400 "$SELLER_FORM"; exit 1; }
grep -q 'Cloudinary is not configured' "$SELLER_FORM" || {
  echo "Cloudinary field error was not displayed to seller" >&2; exit 1;
}
PENDING_UPLOAD_COUNT=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "print(db.getSiblingDB('food_mobo_chain_test').foodItems.countDocuments({name:'CI Pending Cloudinary Upload'}))" | tail -n 1)
[ "$PENDING_UPLOAD_COUNT" = 0 ] || { echo "An unsuccessful Cloudinary upload created a food item" >&2; exit 1; }

curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller/food-cart"
SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
CART_MULTIPART=$(curl -sS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" -w '%{http_code}' \
  -F "_csrf=$SELLER_CSRF" -F 'name=Dhaka Biryani House' \
  -F 'description=Freshly prepared biryani and rice meals for customers in Dhaka.' \
  -F 'location=Dhanmondi 27' -F 'cuisine=Rice & Bangladeshi' \
  -F 'coverImage=/images/carts/street-bite.webp' \
  -F 'deliveryFee=50' -F 'estimatedDeliveryMinutes=35' \
  -F 'imageFile=@src/main/resources/static/images/foods/beef-rice.webp;type=image/webp' \
  "$BASE_URL/seller/food-cart")
[ "$CART_MULTIPART" = 200 ] || { echo "Multipart seller cover incorrectly rejected: $CART_MULTIPART"; head -c 400 "$SELLER_FORM"; exit 1; }
grep -q 'Cloudinary is not configured' "$SELLER_FORM" || {
  echo "Cover upload failure did not display a field error" >&2; exit 1;
}
STORED_COVER=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "print(db.getSiblingDB('food_mobo_chain_test').foodCarts.findOne({_id:'foodCarts:1'}).coverImage)" | tail -n 1)
[ "$STORED_COVER" = '/images/carts/street-bite.webp' ] || {
  echo "Failed cover upload unexpectedly changed the existing image" >&2; exit 1;
}
# Verify seller open/close updates persist in MongoDB and the dashboard renders controls.
for expected in false true; do
  curl -fsS --max-time 20 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" -o "$SELLER_FORM" "$BASE_URL/seller"
  grep -q 'seller-toggle-card' "$SELLER_FORM" || { echo 'Seller toggle control missing' >&2; exit 1; }
  SELLER_CSRF=$(csrf_from_html "$SELLER_FORM")
  TOGGLE_STATUS=$(curl -sS --max-time 15 -b "$SELLER_COOKIES" -c "$SELLER_COOKIES" \
    -o /dev/null -w '%{http_code}' --data-urlencode "_csrf=$SELLER_CSRF" \
    "$BASE_URL/seller/food-cart/toggle-open")
  [ "$TOGGLE_STATUS" = 302 ] || { echo "Seller availability update failed: $TOGGLE_STATUS" >&2; exit 1; }
  SAVED_OPEN=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
    "const c=db.getSiblingDB('food_mobo_chain_test').foodCarts.findOne({_id:'foodCarts:1'});print(c?String(c.open):'MISSING')" | tail -n 1)
  [ "$SAVED_OPEN" = "$expected" ] || { echo "Seller cart open=$SAVED_OPEN, expected $expected" >&2; exit 1; }
done
rm -f "$SELLER_COOKIES" "$SELLER_FORM"
echo 'Seller menu, cover updates and availability toggle passed.'

# End-to-end buyer checkout of two separate sellers in one basket.
BUYER_COOKIES=$(mktemp)
BUYER_FORM=$(mktemp)
curl -fsS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/login"
BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
BUYER_LOGIN=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$BUYER_CSRF" \
  --data-urlencode 'username=buyer@foodmobo.local' \
  --data-urlencode "password=$APP_SEED_BUYER_PASSWORD" "$BASE_URL/login")
[ "$BUYER_LOGIN" = 302 ] || { echo "Seeded buyer login failed: $BUYER_LOGIN" >&2; exit 1; }
for restricted in /seller /admin; do
  forbidden=$(curl -sS --max-time 12 -b "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' "$BASE_URL$restricted")
  [ "$forbidden" = 403 ] || { echo "Buyer JWT bypassed role restriction for $restricted ($forbidden)" >&2; exit 1; }
done
buyer_cookie_api=$(curl -sS --max-time 12 -b "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' "$BASE_URL/api/auth/me")
[ "$buyer_cookie_api" = 401 ] || { echo "Browser JWT cookie improperly authenticated API: $buyer_cookie_api" >&2; exit 1; }
buyer_csrf_status=$(curl -sS --max-time 12 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" -w '%{http_code}' -X POST "$BASE_URL/checkout")
[ "$buyer_csrf_status" = 403 ] || { echo "Checkout accepted missing CSRF token: $buyer_csrf_status" >&2; exit 1; }
grep -q 'Please refresh and try again' "$BUYER_FORM" || { echo 'Missing CSRF-specific 403 explanation' >&2; exit 1; }
curl -fsS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/profile"
BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
for food in foodItems:1 foodItems:8; do
  added=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
    --data-urlencode "_csrf=$BUYER_CSRF" \
    --data-urlencode "foodId=$food" --data-urlencode 'quantity=2' \
    --data-urlencode 'spiceLevel=REGULAR' "$BASE_URL/cart/add")
  [ "$added" = 302 ] || { echo "Cart add failed for $food: HTTP $added" >&2; exit 1; }
  # Read the fresh CSRF token after each modifying request (CookieCsrfTokenRepository).
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
  BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
done
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
grep -q 'cart-line--editable' "$BUYER_FORM" || { echo 'Responsive cart controls absent' >&2; exit 1; }
grep -q 'Update item' "$BUYER_FORM" || { echo 'Cart update action missing' >&2; exit 1; }
LINE_ID=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "const c=db.getSiblingDB('food_mobo_chain_test').shoppingCarts.findOne({buyerId:'users:2'});print(c.items[0]._id)" | tail -n 1)
[ -n "$LINE_ID" ] && [ "$LINE_ID" != undefined ] || { echo 'Embedded cart line ID not found' >&2; exit 1; }
for qty in 3 2; do
  BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
  UPDATE_STATUS=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" \
    -o /dev/null -w '%{http_code}' --data-urlencode "_csrf=$BUYER_CSRF" \
    --data-urlencode "quantity=$qty" --data-urlencode 'spiceLevel=REGULAR' \
    "$BASE_URL/cart/items/$LINE_ID/update")
  [ "$UPDATE_STATUS" = 302 ] || { echo "Cart update HTTP $UPDATE_STATUS" >&2; exit 1; }
  SAVED_QTY=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
    "const c=db.getSiblingDB('food_mobo_chain_test').shoppingCarts.findOne({buyerId:'users:2'});print(c.items.find(x=>x._id==='$LINE_ID').quantity)" | tail -n 1)
  [ "$SAVED_QTY" = "$qty" ] || { echo "Cart quantity $SAVED_QTY, expected $qty" >&2; exit 1; }
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
done
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First vendor absent from cart' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second vendor absent from cart' >&2; exit 1; }
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/checkout"
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First vendor absent from checkout' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second vendor absent from checkout' >&2; exit 1; }
# Reproduce the deployed staging database's historical promo records which omitted codeNormalized.
docker exec "$CONTAINER_NAME" mongosh --quiet --eval "
db.getSiblingDB('food_mobo_chain_test').discounts.updateOne({_id:'discounts:2'}, {\$unset:{codeNormalized:''}})" >/dev/null
PROMO_JSON=$(mktemp)
promo_status=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -o "$PROMO_JSON" -w '%{http_code}' \
  "$BASE_URL/checkout/discount-preview?code=fmc100")
[ "$promo_status" = 200 ] || { echo "Legacy FMC100 promo lookup failed: $promo_status" >&2; cat "$PROMO_JSON"; exit 1; }
python3 - "$PROMO_JSON" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as file: data=json.load(file)
assert data['valid'] is True and float(data['discount']) == 100.0, data
print("Legacy FMC100 promo preview passed.")
PY
welcome_status=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -o "$PROMO_JSON" -w '%{http_code}' \
  "$BASE_URL/checkout/discount-preview?code=WELCOME15")
[ "$welcome_status" = 200 ] || { echo "WELCOME15 preview failed: $welcome_status" >&2; exit 1; }
python3 - "$PROMO_JSON" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as file: data=json.load(file)
assert data['valid'] is True and float(data['discount']) == 123.0, data
print("WELCOME15 discount preview passed.")
PY
bad_promo_status=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -o "$PROMO_JSON" -w '%{http_code}' \
  "$BASE_URL/checkout/discount-preview?code=NOTREAL")
[ "$bad_promo_status" = 400 ] || { echo "Invalid promo was not rejected: $bad_promo_status" >&2; exit 1; }
grep -q 'not found' "$PROMO_JSON" || { echo 'Invalid promo error details missing' >&2; exit 1; }
rm -f "$PROMO_JSON"
# Invalid discount must remain on the checkout page as a field-level error,
# without writing any seller order or altering the customer's basket.
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/checkout"
INVALID_PROMO_CSRF=$(csrf_from_html "$BUYER_FORM")
INVALID_PROMO_SUBMIT=$(curl -sS --max-time 25 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" \
  -o "$BUYER_FORM" -w '%{http_code}' --data-urlencode "_csrf=$INVALID_PROMO_CSRF" \
  --data-urlencode 'deliveryAddress=Road 15, Dhanmondi, Dhaka' \
  --data-urlencode 'phone=01712345678' \
  --data-urlencode 'paymentMethod=CASH_ON_DELIVERY' \
  --data-urlencode 'discountCode=THISCODEDOESNOTEXIST' "$BASE_URL/checkout")
[ "$INVALID_PROMO_SUBMIT" = 200 ] || {
  echo "Invalid promo did not return checkout form with field error: $INVALID_PROMO_SUBMIT" >&2; exit 1;
}
grep -q 'Discount code was not found' "$BUYER_FORM" || {
  echo 'Inline invalid discount feedback was not rendered' >&2; exit 1;
}
invalid_created=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "print(db.getSiblingDB('food_mobo_chain_test').orders.countDocuments({buyerId:'users:2'}))" | tail -n 1)
[ "$invalid_created" = 0 ] || { echo 'Invalid promo caused a partial order write' >&2; exit 1; }
# Fetch a fresh CSRF token from a new checkout GET before submitting a valid order.
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/checkout"
grep -q 'checkoutFinalTotal' "$BUYER_FORM" || { echo 'Responsive checkout total markup missing' >&2; exit 1; }
grep -q 'checkout-seller-group' "$BUYER_FORM" || { echo 'Grouped order layout missing' >&2; exit 1; }
grep -q 'placeholder="e.g. A5XXX"' "$BUYER_FORM" || { echo 'Generic promo example missing' >&2; exit 1; }
CHECKOUT_CSRF=$(csrf_from_html "$BUYER_FORM")
MIXED_CHECKOUT=$(curl -sS --max-time 25 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$CHECKOUT_CSRF" --data-urlencode 'deliveryAddress=Road 15, Dhanmondi, Dhaka' \
  --data-urlencode 'phone=01712345678' --data-urlencode 'paymentMethod=CASH_ON_DELIVERY' \
  --data-urlencode 'discountCode=fmc100' \
  "$BASE_URL/checkout")
[ "$MIXED_CHECKOUT" = 302 ] || { echo "Multi-vendor checkout failed: $MIXED_CHECKOUT" >&2; exit 1; }

mongo_verify=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval "
const dbi=db.getSiblingDB('food_mobo_chain_test');
const rows=dbi.orders.find({buyerId:'users:2'}).toArray();
const sellers=new Set(rows.map(x=>x.foodCartId));
const cart=dbi.shoppingCarts.findOne({buyerId:'users:2'});
const discountSum=rows.reduce((sum,order)=>sum+Number(order.discountAmount.toString()),0);
if(rows.length!==2 || sellers.size!==2 || !sellers.has('foodCarts:1') || !sellers.has('foodCarts:2')
   || rows.some(x=>x.items.length!==1 || x.status!=='CONFIRMED' || x.discountCode!=='FMC100')
   || Math.abs(discountSum-100)>0.001 || !cart || cart.items.length!==0) { print('FAIL'); }
else { print('PASS'); }" | tail -n 1)
[ "$mongo_verify" = PASS ] || { echo "MongoDB multi-vendor order verification failed: $mongo_verify" >&2; exit 1; }
curl -fsS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/orders"
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First order missing from buyer history' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second order missing from buyer history' >&2; exit 1; }
# Test two seller-specific demo online payments from a single checkout.
# All writes use disposable local MongoDB, never the Render staging database.
for food in foodItems:1 foodItems:8; do
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
  BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
  ONLINE_ADD=$(curl -sS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
    --data-urlencode "_csrf=$BUYER_CSRF" --data-urlencode "foodId=$food" \
    --data-urlencode 'quantity=1' --data-urlencode 'spiceLevel=REGULAR' "$BASE_URL/cart/add")
  [ "$ONLINE_ADD" = 302 ] || { echo "Mixed online cart add failed: $ONLINE_ADD" >&2; exit 1; }
done
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/checkout"
ONLINE_CSRF=$(csrf_from_html "$BUYER_FORM")
ONLINE_HEADERS=$(mktemp)
ONLINE_CHECKOUT=$(curl -sS --max-time 30 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" \
  -D "$ONLINE_HEADERS" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$ONLINE_CSRF" \
  --data-urlencode 'deliveryAddress=Road 15, Dhanmondi, Dhaka' \
  --data-urlencode 'phone=01712345678' --data-urlencode 'paymentMethod=SSLCOMMERZ' \
  "$BASE_URL/checkout")
[ "$ONLINE_CHECKOUT" = 302 ] || { echo "Mixed online checkout failed: $ONLINE_CHECKOUT" >&2; exit 1; }
FIRST_PAYMENT_LOCATION=$(grep -i '^location:' "$ONLINE_HEADERS" | tail -n 1 | tr -d '\r' | sed 's/^[Ll]ocation: *//')
rm -f "$ONLINE_HEADERS"
case "$FIRST_PAYMENT_LOCATION" in
  /payment/*|http://*/payment/*|https://*/payment/*) ;;
  *) echo "Mixed seller checkout did not open first payment: $FIRST_PAYMENT_LOCATION" >&2; exit 1 ;;
esac
ONLINE_FIRST=$(printf '%s' "$FIRST_PAYMENT_LOCATION" | sed 's@.*/@@')
PENDING_COUNT=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "print(db.getSiblingDB('food_mobo_chain_test').orders.countDocuments({buyerId:'users:2',status:'PENDING_PAYMENT'}))" | tail -n 1)
[ "$PENDING_COUNT" = 2 ] || { echo "Expected 2 pending seller payments, found $PENDING_COUNT" >&2; exit 1; }
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/orders"
grep -q 'Payment pending' "$BUYER_FORM" || { echo 'Missing payment instructions on order list' >&2; exit 1; }
grep -q 'Pay now' "$BUYER_FORM" || { echo 'Missing Pay now button' >&2; exit 1; }

for step in 1 2; do
  if [ "$step" = 1 ]; then ONLINE_ORDER="$ONLINE_FIRST"; else
    ONLINE_ORDER=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
      "const o=db.getSiblingDB('food_mobo_chain_test').orders.findOne({buyerId:'users:2',status:'PENDING_PAYMENT'});print(o?o.orderNumber:'MISSING')" | tail -n 1)
  fi
  [ "$ONLINE_ORDER" != MISSING ] || { echo "No pending seller payment for step $step" >&2; exit 1; }
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/payment/$ONLINE_ORDER"
  grep -q 'Complete demo payment' "$BUYER_FORM" || { echo 'Missing demo payment button' >&2; exit 1; }
  INVALID_PAYMENT=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" \
    -o "$BUYER_FORM" -w '%{http_code}' -X POST "$BASE_URL/payment/$ONLINE_ORDER/demo-complete")
  [ "$INVALID_PAYMENT" = 403 ] || { echo "Payment accepted without CSRF token: $INVALID_PAYMENT" >&2; exit 1; }
  grep -q 'Please refresh and try again' "$BUYER_FORM" || { echo 'Payment 403 still looks like a role error' >&2; exit 1; }
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/payment/$ONLINE_ORDER"
  PAYMENT_CSRF=$(csrf_from_html "$BUYER_FORM")
  PAYMENT_RESULT=$(curl -sS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" \
    -o /dev/null -w '%{http_code}' --data-urlencode "_csrf=$PAYMENT_CSRF" \
    "$BASE_URL/payment/$ONLINE_ORDER/demo-complete")
  [ "$PAYMENT_RESULT" = 302 ] || { echo "Seller $step payment failed: $PAYMENT_RESULT" >&2; exit 1; }
done
PAID_COUNT=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval \
  "print(db.getSiblingDB('food_mobo_chain_test').orders.countDocuments({buyerId:'users:2',status:'CONFIRMED','payment.status':'PAID','payment.method':'SSLCOMMERZ'}))" | tail -n 1)
[ "$PAID_COUNT" = 2 ] || { echo "Expected 2 paid seller orders, found $PAID_COUNT" >&2; exit 1; }
PAID_PAGE=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
  "$BASE_URL/payment/$ONLINE_FIRST")
[ "$PAID_PAGE" = 302 ] || { echo "Paid order reopened its payment form: $PAID_PAGE" >&2; exit 1; }
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/orders"
if grep -q '>Pay now<' "$BUYER_FORM"; then echo 'Paid order still presents Pay now' >&2; exit 1; fi
echo 'Two seller-specific demo payments completed and persisted successfully.'
rm -f "$BUYER_COOKIES" "$BUYER_FORM"
echo 'JWT roles, CSRF, seeded discounts, multi-seller checkout and online demo payment lifecycle passed.'
echo 'MongoDB replica-set startup, public Thymeleaf pages, role protection and CSRF smoke checks passed.'