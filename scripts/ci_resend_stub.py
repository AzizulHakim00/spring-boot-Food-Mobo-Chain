#!/usr/bin/env python3
"""CI-only Resend-compatible HTTP server. Never runs on the deployed site."""
from __future__ import annotations
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys
import threading

OUT = sys.argv[1]


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/emails" or self.headers.get("Authorization") != "Bearer re_ci_only_key":
            self.send_error(403)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > 16000:
                raise ValueError("invalid length")
            data = json.loads(self.rfile.read(length))
            if not isinstance(data.get("text"), str) or not data.get("to"):
                raise ValueError("invalid payload")
            # The CI-only file is ephemeral and deleted in the trap after assertions.
            with threading.Lock():
                with open(OUT, "a", encoding="utf-8") as file:
                    file.write(json.dumps(data) + "\n")
            reply = json.dumps({"id": "ci-message-accepted"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(reply)))
            self.end_headers()
            self.wfile.write(reply)
        except (ValueError, json.JSONDecodeError):
            self.send_error(400)

    def log_message(self, *args):
        pass  # Never log raw verification or reset URLs.


ThreadingHTTPServer(("127.0.0.1", 8977), Handler).serve_forever()
