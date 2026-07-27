#!/usr/bin/env python3
# 开发用静态服务器：禁用缓存，避免浏览器拿到旧版 JS
# 用法：python serve.py [端口，默认 3000]
# python -m http.server 只发 Last-Modified，浏览器启发式缓存会导致代码更新后不生效，
# 这里对每个响应加 Cache-Control: no-cache（每次都回源 revalidate，未变则 304，开销极小）。
import functools
import http.server
import os
import sys


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-cache")
        super().end_headers()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
    root = os.path.dirname(os.path.abspath(__file__))
    handler = functools.partial(NoCacheHandler, directory=root)
    print(f"serving {root} at http://localhost:{port} (no-cache)")
    http.server.ThreadingHTTPServer(("", port), handler).serve_forever()
