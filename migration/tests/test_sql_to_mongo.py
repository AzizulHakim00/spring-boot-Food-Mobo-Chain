"""Security and integrity tests for the one-time SQL -> MongoDB converter."""
from copy import deepcopy
from decimal import Decimal
import os
from pathlib import Path
import sys
import tempfile
import unittest
from zoneinfo import ZoneInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from sql_to_mongo import parse_sql_value, parse_insert_rows, parse_mysql_dump, build_documents, validate_export, dump_export, bson_date
from verify_export import verify


class MigrationParserTest(unittest.TestCase):
    def test_quoted_values_preserve_commas_semicolons_and_escapes(self):
        rows = parse_insert_rows("(1,'food, spicy; hot',_binary '\\0',NULL),(2,'O\\'Brien',_binary '\\x');", 0)
        self.assertEqual(rows[0], [1, 'food, spicy; hot', '\x00', None])
        self.assertEqual(rows[1][1], "O'Brien")

    def test_decimal_is_never_parsed_as_float(self):
        value, _ = parse_sql_value('123456.78,', 0)
        self.assertIsInstance(value, Decimal)
        self.assertEqual(str(value), '123456.78')

    def test_mysql_bit_values(self):
        self.assertEqual(parse_sql_value("_binary '\\0',", 0)[0], '\x00')
        self.assertEqual(parse_sql_value("_binary '\\1',", 0)[0], '1')

    def test_date_timezone_explicit(self):
        date = bson_date('2026-09-27 18:50:01.123456', ZoneInfo('Asia/Dhaka'))
        self.assertEqual(date, {'$date': '2026-09-27T12:50:01.123Z'})

    def test_real_dump_when_available(self):
        path = os.environ.get('FMC_SQL_DUMP')
        if not path:
            self.skipTest('Set FMC_SQL_DUMP to run full private dump test')
        data = parse_mysql_dump(Path(path).read_text(encoding='utf-8'))
        self.assertEqual(len(data), 16)
        docs = build_documents(data, ZoneInfo('Asia/Dhaka'))
        self.assertEqual(len(docs['users']), 10)
        self.assertEqual(len(docs['foodItems']), 42)
        self.assertEqual(len(docs['orders']), 1)
        summary = validate_export(docs)
        self.assertEqual(summary['collections']['notifications'], 8)
        order = docs['orders'][0]
        self.assertEqual(order['items'][0]['unitPrice']['$numberDecimal'], '260.00')
        self.assertEqual(order['subtotal']['$numberDecimal'], '780.00')
        self.assertEqual(order['total']['$numberDecimal'], '840.00')
        self.assertEqual(order['payment']['amount']['$numberDecimal'], '840.00')
        self.assertEqual(order['delivery']['status'], 'DELIVERED')
        # A cart's items must be embedded in its owner document.
        with_item = deepcopy(data)
        with_item['shopping_carts'][0]['food_cart_id'] = 1
        with_item['cart_items'].append({'id': 1, 'cart_id': 1, 'food_item_id': 2, 'quantity': 2, 'spice_level': 'REGULAR'})
        with_item_docs = build_documents(with_item, ZoneInfo('Asia/Dhaka'))
        self.assertEqual(with_item_docs['shoppingCarts'][0]['items'][0]['foodItemId'], 'foodItems:2')
        self.assertEqual(with_item_docs['shoppingCarts'][0]['items'][0]['quantity'], 2)
        # Historical order snapshots may not use the current mutable food price.
        revised = deepcopy(data)
        revised['food_items'][1]['price'] = Decimal('300.00')
        revised_docs = build_documents(revised, ZoneInfo('Asia/Dhaka'))
        self.assertEqual(revised_docs['orders'][0]['items'][0]['unitPrice']['$numberDecimal'], '260.00')
        # Mutation check: catch a dangling reference before writing an export.
        invalid = deepcopy(data)
        invalid['food_items'][0]['food_cart_id'] = 99999
        with self.assertRaisesRegex(ValueError, 'references missing'):
            build_documents(invalid, ZoneInfo('Asia/Dhaka'))
        # Mutation check: reject a quantity-zero cart item, even if the SQL DDL permits it.
        invalid = deepcopy(data)
        invalid['cart_items'].append({'id': 1, 'cart_id': 1, 'food_item_id': 2, 'quantity': 0, 'spice_level': 'REGULAR'})
        with self.assertRaisesRegex(ValueError, 'quantity outside'):
            build_documents(invalid, ZoneInfo('Asia/Dhaka'))
        # Mutation check: detect a corrupted order snapshot before accepting history.
        invalid = deepcopy(data)
        invalid['order_items'][0]['subtotal'] = Decimal('1.00')
        with self.assertRaisesRegex(ValueError, 'invalid price/quantity/subtotal'):
            build_documents(invalid, ZoneInfo('Asia/Dhaka'))
        with tempfile.TemporaryDirectory() as private_dir:
            dump_export(Path(path), Path(private_dir), 'Asia/Dhaka')
            self.assertEqual(verify(Path(private_dir)), summary)


if __name__ == '__main__':
    unittest.main()
