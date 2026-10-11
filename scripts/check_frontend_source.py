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

    # Checkout/regression layout contract: the legacy .summary-card > div rule
    # applies display:flex to direct children. These more-specific grid rules
    # must be present or prices and totals will overlap in a single row.
    checkout = (TEMPLATES / "customer/order/checkout.html").read_text(encoding="utf-8")
    register = (TEMPLATES / "auth/register.html").read_text(encoding="utf-8")
    css = (STATIC / "css/app.css").read_text(encoding="utf-8")
    promo_script = (STATIC / "js/checkout-discount.js").read_text(encoding="utf-8")

    def required(condition: bool, explanation: str) -> None:
        if not condition:
            problems.append(explanation)

    required("checkout-summary > .checkout-sellers" in css
             and "checkout-summary > .checkout-total-breakdown" in css,
             "Checkout summary lacks overrides for legacy direct-child flex layout")
    required("checkout-summary .checkout-total-row" in css
             and "grid-template-columns:minmax(0,1fr) max-content" in css,
             "Checkout money columns must remain separate and non-overlapping")
    required("auth-registration-fields" in register
             and "registration-password-field" in register
             and "registration-confirm-field" in register
             and "auth-registration-fields > .field" in css,
             "Buyer registration password fields no longer share aligned tracks")
    required("checkout-delivery-fields" in checkout
             and "checkout-delivery-fields > .field > label" in css,
             "Checkout required labels may push phone and promo inputs out of alignment")
    required("checkout-promo-feedback" in checkout
             and "id=\"promoFeedback\" role=\"status\" aria-live=\"polite\"></small>" in checkout,
             "Promo status must start empty and remain accessible to screen readers")
    required("Have a promo code? Enter it here" not in checkout
             and "Click Apply to calculate the discount" not in promo_script,
             "Promo field must not display unsolicited helper text beneath the input")
    required("checkout-promo-feedback:not(:empty)" in css
             and "position:fixed" in css,
             "Promo feedback must be a temporary overlay, not a new form row")

    for issue in problems:
        print("ERROR:", issue, file=sys.stderr)
    print(f"Audited {len(pages)} Thymeleaf pages and {len(styles)} stylesheets: "
          f"{len(problems)} frontend source problems.")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
