# MongoDB migration tool (Phase 1)

This folder contains a **safe local export** from the Food Mobo Chain MySQL dump to
MongoDB Extended JSON Lines. Phase 2 includes a converted MongoDB Java backend candidate, but it has not yet passed full Maven compilation or been verified against a live MongoDB. See `../docs/PHASE2_STATUS.md` before deployment.

## Requirements

- Python 3.10+ (standard library only)
- Your original `food_mobo_chain.sql` outside the public repository
- Optional for later actual import: `mongoimport` (MongoDB Database Tools) and `mongosh`
- A **new, empty** MongoDB Atlas database when importing

## 1. Export safely, offline

```bash
python migration/sql_to_mongo.py \
  --input /private/path/food_mobo_chain.sql \
  --output /private/path/foodmobo-mongo-export \
  --source-timezone Asia/Dhaka
```

The `--source-timezone` is an assumption inferred from the original MySQL JDBC
configuration; verify the real time zone before importing. The tool never contacts
MongoDB and never prints emails, password hashes or phone numbers to the console.
It aborts on dangling SQL foreign-key-style references, corrupt order/payment
amounts, missing payment/delivery records for existing orders, invalid review
targets, and several violated uniqueness/integrity rules.

**CRITICAL: the `.jsonl` files contain real account/password-hash and address/phone
records. Keep them private. Do not commit the original SQL dump, migrated data,
MONGODB_URI, Atlas user/password, or `.env` files to public GitHub.**

## 2. Verify the exported files

```bash
python migration/verify_export.py --input /private/path/foodmobo-mongo-export
```

## 3. Test exporter

```bash
python -m unittest discover -s migration/tests -v
```

To additionally test the real local SQL dump (privately):

```bash
FMC_SQL_DUMP=/private/path/food_mobo_chain.sql python -m unittest discover -s migration/tests -v
```

## 4. Import on your machine (NOT inside Render)

Before running, carefully select the Atlas database; never import into an existing
production database. Verify that the target is EMPTY and that you have a private
backup. `mongoimport --mode=insert` is intentional: rerunning should fail rather
than overwriting changed live data.

```bash
export MONGODB_URI='mongodb+srv://DB_USER:DB_PASSWORD@CLUSTER.mongodb.net/food_mobo_chain?retryWrites=true&w=majority'

for collection in users categories foodCarts foodItems shoppingCarts orders discounts reviews favoriteFoods favoriteCarts notifications passwordResetTokens; do
  mongoimport --uri "$MONGODB_URI" \
    --collection "$collection" --type=json \
    --file "/private/path/foodmobo-mongo-export/${collection}.jsonl" \
    --mode=insert --stopOnError
done

mongosh "$MONGODB_URI" --file migration/mongo_indexes.js
```

Prefer interactive `mongosh` login or environment variables if the shell could log
secrets; never paste a real URI into GitHub issues or screenshots. With the URI in
an environment variable, the command arguments may still be visible in process
lists on shared machines. Use `--config` secret files with protected permissions
if needed.

The examples use `mongodb+srv` and Atlas, but the exporter works offline.
Some MongoDB tools cannot create an empty collection from an empty `.jsonl` file;
create those collections explicitly if desired. Index creation is safe for empty
collections.

## 5. Post-import checks

Check indexes, count each collection, verify one known order's line totals and
its payment/delivery states, then confirm 10 accounts can still authenticate
using their existing BCrypt hashes **after the converted backend has passed build and integration tests**.
Do not assume the converted Java application works without staging integration tests.

## Rollback

Preserve original `FoodMoboChain.zip` + `food_mobo_chain.sql` offline. The exporter
never mutates the SQL database. Revert the separate Java migration branch to the
MySQL build if something fails; do not automatically delete or reimport live data.
