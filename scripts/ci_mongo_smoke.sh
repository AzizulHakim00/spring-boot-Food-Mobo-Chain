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
echo 'Sanitized food catalog seed and MongoDB database-name isolation passed.'

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
curl -fsS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/profile"
BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
for food in foodItems:1 foodItems:8; do
  added=$(curl -sS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
    --data-urlencode "_csrf=$BUYER_CSRF" \
    --data-urlencode "foodId=$food" --data-urlencode 'quantity=1' \
    --data-urlencode 'spiceLevel=REGULAR' "$BASE_URL/cart/add")
  [ "$added" = 302 ] || { echo "Cart add failed for $food: HTTP $added" >&2; exit 1; }
  # Read the fresh CSRF token after each modifying request (CookieCsrfTokenRepository).
  curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
  BUYER_CSRF=$(csrf_from_html "$BUYER_FORM")
done
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/cart"
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First vendor absent from cart' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second vendor absent from cart' >&2; exit 1; }
curl -fsS --max-time 20 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/checkout"
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First vendor absent from checkout' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second vendor absent from checkout' >&2; exit 1; }
CHECKOUT_CSRF=$(csrf_from_html "$BUYER_FORM")
MIXED_CHECKOUT=$(curl -sS --max-time 25 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o /dev/null -w '%{http_code}' \
  --data-urlencode "_csrf=$CHECKOUT_CSRF" --data-urlencode 'deliveryAddress=Road 15, Dhanmondi, Dhaka' \
  --data-urlencode 'phone=01712345678' --data-urlencode 'paymentMethod=CASH_ON_DELIVERY' \
  "$BASE_URL/checkout")
[ "$MIXED_CHECKOUT" = 302 ] || { echo "Multi-vendor checkout failed: $MIXED_CHECKOUT" >&2; exit 1; }

mongo_verify=$(docker exec "$CONTAINER_NAME" mongosh --quiet --eval "
const dbi=db.getSiblingDB('food_mobo_chain_test');
const rows=dbi.orders.find({buyerId:'users:2'}).toArray();
const sellers=new Set(rows.map(x=>x.foodCartId));
const cart=dbi.shoppingCarts.findOne({buyerId:'users:2'});
if(rows.length!==2 || sellers.size!==2 || !sellers.has('foodCarts:1') || !sellers.has('foodCarts:2')
   || rows.some(x=>x.items.length!==1 || x.status!=='CONFIRMED')
   || !cart || cart.items.length!==0) { print('FAIL'); }
else { print('PASS'); }" | tail -n 1)
[ "$mongo_verify" = PASS ] || { echo "MongoDB multi-vendor order verification failed: $mongo_verify" >&2; exit 1; }
curl -fsS --max-time 15 -b "$BUYER_COOKIES" -c "$BUYER_COOKIES" -o "$BUYER_FORM" "$BASE_URL/orders"
grep -q 'Dhaka Biryani House' "$BUYER_FORM" || { echo 'First order missing from buyer history' >&2; exit 1; }
grep -q 'Street Bite' "$BUYER_FORM" || { echo 'Second order missing from buyer history' >&2; exit 1; }
rm -f "$BUYER_COOKIES" "$BUYER_FORM"
echo 'Authenticated multi-vendor cart, atomic checkout, two seller orders and logout checks passed.'
echo 'MongoDB replica-set startup, public Thymeleaf pages, role protection and CSRF smoke checks passed.'