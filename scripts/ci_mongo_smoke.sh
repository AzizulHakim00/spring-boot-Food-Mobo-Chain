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

export MONGODB_URI='mongodb://127.0.0.1:27017/food_mobo_chain_test?directConnection=true&replicaSet=rs0'
export SPRING_PROFILES_ACTIVE=prod
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

echo 'MongoDB replica-set startup, public Thymeleaf pages, role protection and CSRF smoke checks passed.'