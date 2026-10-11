#!/usr/bin/env python3
"""Check every Thymeleaf page and bundled browser asset without needing a web server."""
from __future__ import annotations

from html.parser import HTMLParser
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = ROOT / "src/main/resources/templates"
STATIC = ROOT / "src/main/resources/static"
ASSET = re.compile(r"@\{/((?:images|css|js)/[a-zA-Z0-9_./-]+)\}")
ID = re.compile(r'\sid="([a-zA-Z0-9_-]+)"')


class FormParser(HTMLParser):
    def __init__(self, name: str):
        super().__init__(convert_charrefs=True)
        self.name = name
        self.depth = 0
        self.errors: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "form":
            if self.depth:
                self.errors.append(f"{self.name}: nested HTML <form> tags")
            self.depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag == "form":
            if self.depth == 0:
                self.errors.append(f"{self.name}: unmatched closing </form>")
            else:
                self.depth -= 1


def css_balanced(content: str) -> bool:
    # Ignore comments and strings; braces inside CSS content literals are not rules.
    content = re.sub(r"/\*.*?\*/", "", content, flags=re.S)
    depth = 0
    quote = None
    escaped = False
    for char in content:
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth < 0:
                return False
    return depth == 0 and quote is None


def main() -> int:
    problems: list[str] = []
    pages = sorted(TEMPLATES.rglob("*.html"))
    for page in pages:
        html = page.read_text(encoding="utf-8")
        parser = FormParser(str(page.relative_to(ROOT)))
        parser.feed(html)
        problems.extend(parser.errors)
        if parser.depth:
            problems.append(f"{parser.name}: {parser.depth} unclosed HTML <form> tags")
        for asset in ASSET.findall(html):
            if not (STATIC / asset).is_file():
                problems.append(f"{parser.name}: missing /{asset}")
        ids = ID.findall(html)
        for element_id in sorted(set(ids)):
            if ids.count(element_id) > 1:
                problems.append(f"{parser.name}: duplicate static id={element_id!r}")

    styles = sorted((STATIC / "css").glob("*.css"))
    for css in styles:
        if not css_balanced(css.read_text(encoding="utf-8")):
            problems.append(f"{css.relative_to(ROOT)}: unbalanced CSS blocks")

    for issue in problems:
        print("ERROR:", issue, file=sys.stderr)
    print(f"Audited {len(pages)} Thymeleaf pages and {len(styles)} stylesheets: "
          f"{len(problems)} frontend source problems.")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
