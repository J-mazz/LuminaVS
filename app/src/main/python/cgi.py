"""
Minimal shim for the removed stdlib `cgi` module in Python 3.13+ so Chaquopy's pip can import it.
Only functions/constants used by pip are provided.
"""

import urllib.parse as _urlparse

def parse_header(line):
    parts = line.split(";")
    key = parts[0].strip()
    pdict = {}
    for item in parts[1:]:
        if "=" in item:
            k, v = item.split("=", 1)
            pdict[k.strip()] = _urlparse.unquote(v.strip().strip('"'))
    return key, pdict

def _parseparam(s):
    return s

FieldStorage = object  # placeholder; pip only needs parse_header

__all__ = ["parse_header", "FieldStorage"]