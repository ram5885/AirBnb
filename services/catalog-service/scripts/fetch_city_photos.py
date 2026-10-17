#!/usr/bin/env python3
"""
Downloads one iconic photo per city in src/main/resources/data/locations.csv from Wikipedia /
Wikimedia Commons, into src/main/resources/data/city-photos/, and writes city-photos.csv with each
photo's source and licence (CityPhotoLoader seeds these into the city_photo table on startup).

Per city it tries the article "City, State" and then "City", takes the article's lead image and, when
that is a map, flag, seal or similar, the first real photo on the page. Cities that already have a
photo are skipped, so the script can be re-run to fill gaps. Usage (from the project root):

    python3 scripts/fetch_city_photos.py
"""
import csv
import html
import json
import os
import re
import shutil
import ssl
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data"
OUT_DIR = DATA / "city-photos"
MANIFEST = DATA / "city-photos.csv"
WIDTH = 640  # downloaded size; shrink() then stores them at 480px, JPEG quality 60
USER_AGENT = "AirBnbHotelDemo/1.0 (city photo seeding; https://github.com/ram5588)"
NOT_A_PHOTO = re.compile(
    r"map|locator|location|flag|seal|emblem|logo|coat[_ ]of[_ ]arms|symbol|icon|signature|"
    r"diagram|chart|graph|plan\b|\.svg$|\.gif$|\.tif", re.I)
# python.org builds on macOS ship without CA certificates; fall back to certifi or the system bundle.
try:
    import certifi
    SSL_CONTEXT = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL_CONTEXT = ssl.create_default_context(
        cafile="/etc/ssl/cert.pem" if os.path.exists("/etc/ssl/cert.pem") else None)
FIELDS = ["city", "state", "file", "source_page", "image_page", "artist", "license"]


def get_json(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=30, context=SSL_CONTEXT) as r:
                return json.load(r)
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))


def get_bytes(url):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60, context=SSL_CONTEXT) as r:
                return r.read()
        except Exception:
            if attempt == 3:
                raise
            time.sleep(3 * (attempt + 1))


def wiki(params):
    params = {"format": "json", "formatversion": "2", **params}
    return get_json("https://en.wikipedia.org/w/api.php?" + urllib.parse.urlencode(params))


def find_article(city, state):
    """First of "City, State" / "City" that is a real (non-disambiguation) article about a place."""
    for title in (f"{city}, {state}", city):
        page = wiki({"action": "query", "titles": title, "redirects": 1,
                     "prop": "pageimages|pageprops|info", "piprop": "name", "inprop": "url"})["query"]["pages"][0]
        if page.get("missing") or "disambiguation" in page.get("pageprops", {}):
            continue
        return page
    return None


def candidate_images(page):
    """Lead image first, then the article's other images in page order."""
    names = []
    if page.get("pageimage"):
        names.append(page["pageimage"])
    media = get_json("https://en.wikipedia.org/api/rest_v1/page/media-list/"
                     + urllib.parse.quote(page["title"].replace(" ", "_"), safe=""))
    for item in media.get("items", []):
        if item.get("type") == "image" and item.get("title", "").startswith("File:"):
            names.append(item["title"][5:])
    seen, result = set(), []
    for n in names:
        n = n.replace("_", " ")
        if n not in seen and not NOT_A_PHOTO.search(n):
            seen.add(n)
            result.append(n)
    return result


def image_info(file_name):
    """Thumbnail URL, file page and licence metadata, or None for small/non-photo files."""
    pages = get_json("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "format": "json", "formatversion": "2", "titles": "File:" + file_name,
        "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata", "iiurlwidth": WIDTH}))["query"]["pages"]
    if not pages or "imageinfo" not in pages[0]:
        return None
    info = pages[0]["imageinfo"][0]
    if info.get("mime") not in ("image/jpeg", "image/png") or info.get("width", 0) < 400 \
            or info["width"] < info["height"]:  # landscape photos only, like the cards
        return None
    meta = info.get("extmetadata", {})

    def text(key):
        raw = meta.get(key, {}).get("value", "")
        return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", raw))).strip()[:200]

    return {"url": info.get("thumburl") or info["url"], "image_page": info["descriptionurl"],
            "artist": text("Artist") or "Unknown", "license": text("LicenseShortName") or "See image page"}


def shrink(path):
    """Re-encode as a 480px-wide JPEG (the cards are ~170px) with macOS sips, to keep the repo small."""
    if not shutil.which("sips"):
        return path
    target = path.with_suffix(".jpg")
    subprocess.run(["sips", "-Z", "480", "-s", "format", "jpeg", "-s", "formatOptions", "60",
                    str(path), "--out", str(target)], check=True, capture_output=True)
    if target != path:
        path.unlink()
    return target


def slug(city, state):
    return re.sub(r"[^a-z0-9]+", "-", f"{city} {state}".lower()).strip("-")


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    rows = {}
    if MANIFEST.exists():
        with MANIFEST.open(newline="", encoding="utf-8") as f:
            rows = {(r["city"], r["state"]): r for r in csv.DictReader(f) if (OUT_DIR / r["file"]).exists()}

    with (DATA / "locations.csv").open(newline="", encoding="utf-8-sig") as f:
        cities = [(r["City"].strip(), r["State"].strip()) for r in csv.DictReader(f)]

    missing = []
    for i, (city, state) in enumerate(cities, 1):
        if (city, state) in rows:
            continue
        try:
            page = find_article(city, state)
            picked = None
            for name in (candidate_images(page) if page else [])[:12]:
                picked = image_info(name)
                if picked:
                    break
            if not picked:
                missing.append(f"{city}, {state}")
                print(f"[{i}/{len(cities)}] {city}, {state}: no suitable photo", flush=True)
                continue
            data = get_bytes(picked["url"])
            ext = ".png" if data[:4] == b"\x89PNG" else ".jpg"
            path = OUT_DIR / (slug(city, state) + ext)
            path.write_bytes(data)
            file = shrink(path).name
            rows[(city, state)] = {"city": city, "state": state, "file": file, "source_page": page["fullurl"],
                                   "image_page": picked["image_page"], "artist": picked["artist"],
                                   "license": picked["license"]}
            print(f"[{i}/{len(cities)}] {city}, {state}: {file}", flush=True)
        except Exception as e:  # keep going; a re-run retries this city
            missing.append(f"{city}, {state}")
            print(f"[{i}/{len(cities)}] {city}, {state}: failed ({e})", file=sys.stderr, flush=True)
        time.sleep(0.3)  # be polite to the Wikimedia APIs

        with MANIFEST.open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=FIELDS)
            w.writeheader()
            w.writerows(sorted(rows.values(), key=lambda r: (r["state"], r["city"])))

    print(f"\n{len(rows)} of {len(cities)} cities have a photo.")
    if missing:
        print("Without a photo:", "; ".join(missing))


if __name__ == "__main__":
    main()
