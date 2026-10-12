#!/usr/bin/env python3
"""Re-verify private MongoDB Extended JSONL export, after export or transfer."""
import argparse
import json
from pathlib import Path
from sql_to_mongo import SCHEMA, validate_export


def verify(source: Path) -> dict:
    documents = {}
    for collection in sorted({c for c in SCHEMA.values() if c}):
        path = source / f'{collection}.jsonl'
        if not path.is_file():
            raise FileNotFoundError(f'Missing export collection file: {path}')
        documents[collection] = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]
    return validate_export(documents)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.input), indent=2))
