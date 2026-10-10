#!/usr/bin/env python3
"""Convert the Food Mobo Chain MySQL dump to MongoDB Extended JSON Lines.

Offline, standard-library only. Does NOT write to MongoDB and never prints PII.
Use a fresh empty database for the subsequent `mongoimport` step.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import json
from pathlib import Path
import re
from zoneinfo import ZoneInfo

SCHEMA = {
    "users": "users",
    "categories": "categories",
    "food_carts": "foodCarts",
    "food_items": "foodItems",
    "shopping_carts": "shoppingCarts",
    "cart_items": None,
    "customer_orders": "orders",
    "order_items": None,
    "payments": None,
    "deliveries": None,
    "discounts": "discounts",
    "reviews": "reviews",
    "favorite_foods": "favoriteFoods",
    "favorite_carts": "favoriteCarts",
    "notifications": "notifications",
    "password_reset_tokens": "passwordResetTokens",
}
# Fields in each source table that must be preserved as BSON Decimal128.
DECIMALS = {
    "food_carts": ("delivery_fee",),
    "food_items": ("price",),
    "customer_orders": ("delivery_fee", "discount_amount", "subtotal", "total"),
    "order_items": ("subtotal", "unit_price"),
    "payments": ("amount",),
    "discounts": ("value", "minimum_order", "maximum_discount"),
}
DATES = {
    "users": ("created_at", "updated_at"),
    "food_carts": ("created_at", "updated_at"),
    "food_items": ("created_at", "updated_at"),
    "shopping_carts": ("updated_at",),
    "customer_orders": ("created_at", "updated_at"),
    "payments": ("created_at", "paid_at"),
    "deliveries": ("dispatched_at", "delivered_at"),
    "discounts": ("starts_at", "ends_at"),
    "reviews": ("created_at",),
    "notifications": ("created_at",),
    "password_reset_tokens": ("created_at", "expires_at"),
}
BOOLEANS = {
    "users": ("enabled",),
    "categories": ("active",),
    "food_carts": ("approved", "open_flag"),
    "food_items": ("archived", "available", "featured", "spicy_supported"),
    "discounts": ("active",),
    "reviews": ("approved", "hidden"),
    "notifications": ("read_flag",),
    "password_reset_tokens": ("used",),
}
PREFIXES = {
    "users": "users", "categories": "categories", "food_carts": "foodCarts",
    "food_items": "foodItems", "shopping_carts": "shoppingCarts", "cart_items": "cartItems",
    "customer_orders": "orders", "order_items": "orderItems", "payments": "payments",
    "deliveries": "deliveries", "discounts": "discounts", "reviews": "reviews",
    "favorite_foods": "favoriteFoods", "favorite_carts": "favoriteCarts",
    "notifications": "notifications", "password_reset_tokens": "passwordResetTokens",
}
TABLE_COLUMN_RE = re.compile(r"CREATE TABLE `([^`]+)` \((.*?)\n\) ENGINE=", re.S)
INSERT_START_RE = re.compile(r"INSERT INTO `([^`]+)` VALUES ")


def parse_sql_value(source: str, start: int) -> tuple[object, int]:
    """Read one MySQL dump value, preserving quoted strings and escaped commas."""
    i = start
    if source.startswith("_binary ", i):
        i += len("_binary ")
    if i >= len(source):
        raise ValueError("Unexpected end of SQL value")
    if source[i] in ("'", '"'):
        quote = source[i]
        i += 1
        out: list[str] = []
        escapes = {"0": "\x00", "b": "\b", "n": "\n", "r": "\r", "t": "\t", "Z": "\x1a"}
        while i < len(source):
            ch = source[i]
            if ch == "\\":
                i += 1
                if i >= len(source):
                    raise ValueError("Bad SQL escape")
                out.append(escapes.get(source[i], source[i]))
            elif ch == quote:
                if i + 1 < len(source) and source[i + 1] == quote:
                    out.append(quote)
                    i += 1
                else:
                    return "".join(out), i + 1
            else:
                out.append(ch)
            i += 1
        raise ValueError("Unterminated SQL string")
    while i < len(source) and source[i] not in ",);":
        i += 1
    token = source[start:i].strip()
    if token == "NULL":
        return None, i
    if re.fullmatch(r"[-+]?\d+", token):
        return int(token), i
    try:
        return Decimal(token), i
    except InvalidOperation as ex:
        raise ValueError(f"Unsupported SQL token: {token!r}") from ex


def parse_insert_rows(sql: str, start: int) -> list[list[object]]:
    rows = []
    i = start
    while i < len(sql):
        while i < len(sql) and sql[i].isspace():
            i += 1
        if sql[i] == ';':
            return rows
        if sql[i] != '(':
            raise ValueError(f"Expected '(' for INSERT row at byte/char {i}")
        i += 1
        row = []
        while True:
            while sql[i].isspace():
                i += 1
            value, i = parse_sql_value(sql, i)
            row.append(value)
            while sql[i].isspace():
                i += 1
            if sql[i] == ')':
                i += 1
                break
            if sql[i] != ',':
                raise ValueError("Expected comma in INSERT row")
            i += 1
        rows.append(row)
        while sql[i].isspace():
            i += 1
        if sql[i] == ',':
            i += 1
            continue
        if sql[i] == ';':
            return rows
        raise ValueError("Expected row separator or semicolon")
    raise ValueError("Unterminated INSERT statement")


def parse_mysql_dump(sql: str) -> dict[str, list[dict]]:
    columns: dict[str, list[str]] = {}
    for table, ddl in TABLE_COLUMN_RE.findall(sql):
        columns[table] = re.findall(r"(?m)^  `([^`]+)` ", ddl)
    missing = set(SCHEMA) - set(columns)
    if missing:
        raise ValueError(f"Missing SQL CREATE TABLE statements: {sorted(missing)}")
    unexpected = set(columns) - set(SCHEMA)
    if unexpected:
        raise ValueError(f"New source tables require explicit migration mapping: {sorted(unexpected)}")

    rows: dict[str, list[dict]] = {table: [] for table in columns}
    for match in INSERT_START_RE.finditer(sql):
        table = match.group(1)
        if table not in rows:
            raise ValueError(f"Unknown SQL INSERT table: {table}")
        for values in parse_insert_rows(sql, match.end()):
            if len(values) != len(columns[table]):
                raise ValueError(f"SQL column/value count mismatch in {table}: {len(values)} vs {len(columns[table])}")
            rows[table].append(dict(zip(columns[table], values)))
    for table, table_rows in rows.items():
        ids = [row['id'] for row in table_rows]
        if len(ids) != len(set(ids)):
            raise ValueError(f"Duplicate primary IDs in {table}")
    return rows


def cid(table: str, old_id: object) -> str | None:
    if old_id is None:
        return None
    return f"{PREFIXES[table]}:{old_id}"


def camel(key: str) -> str:
    parts = key.split('_')
    return parts[0] + ''.join(part[:1].upper() + part[1:] for part in parts[1:])


def bson_date(value: str, source_zone: ZoneInfo) -> dict:
    parsed = datetime.fromisoformat(value).replace(tzinfo=source_zone)
    utc = parsed.astimezone(timezone.utc)
    # BSON Date supports millisecond precision (MySQL DATETIME(6) is microsecond).
    return {'$date': utc.isoformat(timespec='milliseconds').replace('+00:00', 'Z')}


def typed(table: str, row: dict, source_zone: ZoneInfo) -> dict:
    result = {}
    for key, value in row.items():
        if key == 'id':
            result['_id'] = cid(table, value)
        elif key in DECIMALS.get(table, ()) and value is not None:
            result[camel(key)] = {'$numberDecimal': str(value)}
        elif key in DATES.get(table, ()) and value is not None:
            result[camel(key)] = bson_date(value, source_zone)
        elif key in BOOLEANS.get(table, ()):
            if isinstance(value, str) and len(value) == 1:
                # mysqldump formats MySQL BIT(1) as _binary '\0' or literal 0x01.
                result[camel(key)] = ord(value) != 0
            elif isinstance(value, int) and value in (0, 1):
                result[camel(key)] = bool(value)
            else:
                raise ValueError(f"Unexpected BIT value in {table}.{key}: {value!r}")
        else:
            result[camel(key)] = value
    return result


def remap(table: str, doc: dict, key: str, ref_table: str) -> None:
    value = doc.pop(key)
    doc[key[:-2] + 'Id'] = cid(ref_table, value) if value is not None else None


def build_documents(rows: dict[str, list[dict]], source_zone: ZoneInfo) -> dict[str, list[dict]]:
    idx = {table: {row['id']: row for row in table_rows} for table, table_rows in rows.items()}
    errors = []
    def require_ref(table: str, row: dict, field: str, target: str) -> None:
        value = row[field]
        if value is not None and value not in idx[target]:
            errors.append(f"{table}#{row['id']}.{field} references missing {target}#{value}")

    references = {
        'food_carts': {'owner_id': 'users'},
        'food_items': {'category_id': 'categories', 'food_cart_id': 'food_carts'},
        'shopping_carts': {'buyer_id': 'users', 'food_cart_id': 'food_carts'},
        'cart_items': {'cart_id': 'shopping_carts', 'food_item_id': 'food_items'},
        'customer_orders': {'buyer_id': 'users', 'food_cart_id': 'food_carts'},
        'order_items': {'order_id': 'customer_orders'},
        'payments': {'order_id': 'customer_orders'},
        'deliveries': {'order_id': 'customer_orders'},
        'reviews': {'buyer_id': 'users', 'food_cart_id': 'food_carts', 'food_item_id': 'food_items'},
        'favorite_foods': {'user_id': 'users', 'food_item_id': 'food_items'},
        'favorite_carts': {'user_id': 'users', 'food_cart_id': 'food_carts'},
        'notifications': {'user_id': 'users'},
        'password_reset_tokens': {'user_id': 'users'},
    }
    for table, fields in references.items():
        for row in rows[table]:
            for field, target in fields.items():
                require_ref(table, row, field, target)

    for row in rows['reviews']:
        if (row['food_cart_id'] is None) == (row['food_item_id'] is None):
            errors.append(f"reviews#{row['id']} must point to exactly one review target")
        if not 1 <= row['rating'] <= 5:
            errors.append(f"reviews#{row['id']} has rating outside 1..5")
    for row in rows['cart_items']:
        if row['quantity'] < 1 or row['quantity'] > 20:
            errors.append(f"cart_items#{row['id']} quantity outside 1..20")
        cart = idx['shopping_carts'].get(row['cart_id'])
        food = idx['food_items'].get(row['food_item_id'])
        if cart is not None and food is not None and (
                cart['food_cart_id'] is None or cart['food_cart_id'] != food['food_cart_id']):
            errors.append(f"cart_items#{row['id']} does not belong to cart's selected seller")
    for row in rows['food_items']:
        if Decimal(row['price']) <= 0:
            errors.append(f"food_items#{row['id']} nonpositive price")
    for row in rows['order_items']:
        if row['quantity'] < 1 or Decimal(row['unit_price']) * row['quantity'] != Decimal(row['subtotal']):
            errors.append(f"order_items#{row['id']} invalid price/quantity/subtotal snapshot")
    if errors:
        raise ValueError("Migration preflight failed:\n" + '\n'.join(errors[:50]))

    result: dict[str, list[dict]] = {collection: [] for collection in SCHEMA.values() if collection}
    for table, collection in SCHEMA.items():
        if collection is None:
            continue
        for row in rows[table]:
            doc = typed(table, row, source_zone)
            for field, target_table in references.get(table, {}).items():
                remap(table, doc, camel(field), target_table)
            if table in ('shopping_carts', 'customer_orders'):
                doc['version'] = 0  # Required by Java @Version when an imported document is first updated.
            if table == 'users':
                doc['emailNormalized'] = doc['email'].strip().lower()
            if table == 'discounts':
                doc['codeNormalized'] = doc['code'].strip().upper()
            if table == 'categories':
                doc['nameNormalized'] = doc['name'].strip().casefold()
            if table == 'food_carts':
                # Source DB uses open_flag; matching Thymeleaf model is 'open'.
                doc['open'] = doc.pop('openFlag')
            if table == 'notifications':
                doc['read'] = doc.pop('readFlag')
            result[collection].append(doc)

    cart_items = defaultdict(list)
    for row in rows['cart_items']:
        doc = typed('cart_items', row, source_zone)
        remap('cart_items', doc, 'foodItemId', 'food_items')
        doc.pop('cartId')
        cart_items[row['cart_id']].append(doc)
    for row, doc in zip(rows['shopping_carts'], result['shoppingCarts']):
        doc['items'] = cart_items[row['id']]

    order_items = defaultdict(list)
    for row in rows['order_items']:
        doc = typed('order_items', row, source_zone)
        doc.pop('orderId')
        doc['foodItemIdSnapshot'] = cid('food_items', doc['foodItemIdSnapshot'])
        order_items[row['order_id']].append(doc)
    payment_rows = defaultdict(list)
    for row in rows['payments']:
        payment_rows[row['order_id']].append(row)
    delivery_rows = defaultdict(list)
    for row in rows['deliveries']:
        delivery_rows[row['order_id']].append(row)
    for row, doc in zip(rows['customer_orders'], result['orders']):
        doc['items'] = order_items[row['id']]
        for source, field, lookup in [
            ('payments', 'payment', payment_rows),
            ('deliveries', 'delivery', delivery_rows),
        ]:
            candidates = lookup[row['id']]
            if len(candidates) != 1:
                raise ValueError(f"Order {row['id']} requires exactly one {source} record (found {len(candidates)})")
            embedded = typed(source, candidates[0], source_zone)
            embedded.pop('orderId')
            doc[field] = embedded
        # Snapshot math stays unchanged. Do not recompute old prices from foods.
        sum_items = sum((Decimal(item['subtotal']) for item in order_items_as_source(rows, row['id'])), Decimal(0))
        total = Decimal(row['subtotal']) - Decimal(row['discount_amount']) + Decimal(row['delivery_fee'])
        if sum_items != Decimal(row['subtotal']) or total != Decimal(row['total']):
            raise ValueError(f"Order {row['id']} totals fail SQL snapshot reconciliation")
        if Decimal(payment_rows[row['id']][0]['amount']) != Decimal(row['total']):
            raise ValueError(f"Order {row['id']} payment amount differs from order total")
    return result


def order_items_as_source(rows: dict[str, list[dict]], order_id: int) -> list[dict]:
    return [r for r in rows['order_items'] if r['order_id'] == order_id]


def validate_export(docs: dict[str, list[dict]]) -> dict:
    id_sets = {name: {x['_id'] for x in entries} for name, entries in docs.items()}
    if any(len(id_sets[name]) != len(entries) for name, entries in docs.items()):
        raise ValueError('Duplicate MongoDB _id detected')

    refs = {
        'foodCarts': {'ownerId': 'users'},
        'foodItems': {'foodCartId': 'foodCarts', 'categoryId': 'categories'},
        'shoppingCarts': {'buyerId': 'users', 'foodCartId': 'foodCarts'},
        'orders': {'buyerId': 'users', 'foodCartId': 'foodCarts'},
        'reviews': {'buyerId': 'users', 'foodCartId': 'foodCarts', 'foodItemId': 'foodItems'},
        'favoriteFoods': {'userId': 'users', 'foodItemId': 'foodItems'},
        'favoriteCarts': {'userId': 'users', 'foodCartId': 'foodCarts'},
        'notifications': {'userId': 'users'},
        'passwordResetTokens': {'userId': 'users'},
    }
    for collection, reference_fields in refs.items():
        for doc in docs[collection]:
            for field, target in reference_fields.items():
                value = doc.get(field)
                if value is not None and value not in id_sets[target]:
                    raise ValueError(f"Dangling reference in {collection}.{field}")
    for cart in docs['shoppingCarts']:
        for item in cart['items']:
            if item['foodItemId'] not in id_sets['foodItems']:
                raise ValueError('Cart contains missing food item')
    for name, field in [('users', 'emailNormalized'), ('categories', 'nameNormalized'),
                        ('categories', 'slug'), ('foodCarts', 'slug'),
                        ('discounts', 'codeNormalized'), ('orders', 'orderNumber')]:
        values = [doc[field] for doc in docs[name]]
        if len(values) != len(set(values)):
            raise ValueError(f'Duplicate unique key for {name}.{field}')
    for name, fields in [('foodCarts', ('ownerId',)), ('shoppingCarts', ('buyerId',)),
                         ('favoriteFoods', ('userId', 'foodItemId')),
                         ('favoriteCarts', ('userId', 'foodCartId'))]:
        values = [tuple(doc[field] for field in fields) for doc in docs[name]]
        if len(values) != len(set(values)):
            raise ValueError(f'Duplicate unique key for {name}.{fields}')
    for order in docs['orders']:
        for item in order['items']:
            if item['foodItemIdSnapshot'] not in id_sets['foodItems']:
                # Existing order snapshots must survive deleting/archiving foods. We do not
                # demand the current food exist here; only check the self-contained snapshot.
                if not (item.get('foodName') and item.get('unitPrice')):
                    raise ValueError('Historical order snapshot is incomplete')
    return {'collections': {k: len(v) for k, v in docs.items()},
            'ordersWithEmbeddedItems': len(docs['orders']),
            'shoppingCartsWithEmbeddedItems': len(docs['shoppingCarts'])}


def dump_export(source: Path, output: Path, source_timezone: str) -> dict:
    rows = parse_mysql_dump(source.read_text(encoding='utf-8'))
    zone = ZoneInfo(source_timezone)
    docs = build_documents(rows, zone)
    results = validate_export(docs)
    output.mkdir(parents=True, exist_ok=True)
    for collection, entries in docs.items():
        path = output / f'{collection}.jsonl'
        with path.open('w', encoding='utf-8') as f:
            for doc in entries:
                f.write(json.dumps(doc, ensure_ascii=False, separators=(',', ':')) + '\n')
    summary = {'sourceTableCounts': {k: len(v) for k, v in rows.items()},
               **results,
               'sourceTimezone': source_timezone,
               'sensitive': 'Export JSONL contains private user information and password hashes; never commit or upload publicly.'}
    (output / 'migration_summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True, help='Original MySQL dump')
    parser.add_argument('--output', type=Path, required=True, help='Private output directory outside git')
    parser.add_argument('--source-timezone', default='Asia/Dhaka', help='Time zone of original DATETIME values')
    args = parser.parse_args()
    summary = dump_export(args.input, args.output, args.source_timezone)
    print('Export validated. Collection counts (no personal data):')
    print(json.dumps(summary['collections'], indent=2))
    print('Files are PRIVATE. Do not upload them to public GitHub.')


if __name__ == '__main__':
    main()
