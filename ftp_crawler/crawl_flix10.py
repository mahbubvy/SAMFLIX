#!/usr/bin/env python3
"""Build catalogue files for the DHAKA-FLIX-10 sections that PotFlix's bundled
catalogue never covered (anime/cartoons, documentaries, awards, wrestling).

Writes one JSON file per section into app/src/main/assets/database/dhakaflix/.
Each title becomes a folder entry; the app lists its episodes when it is opened.

Run from the repository root:  python ftp_crawler/crawl_flix10.py
"""
import html
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HOST = "http://172.16.50.10"
ROOT = "/DHAKA-FLIX-10/"
OUT = Path("app/src/main/assets/database/dhakaflix")
SECTIONS = {
    "anime.json": ("Anime & Cartoon TV Series/", "Anime Series"),
    "documentary.json": ("Documentary/", "Documentary Series"),
    "awards.json": ("Awards & TV Shows/", "Awards & TV Series"),
    "wrestling.json": ("WWE & AEW Wrestling/", "Wrestling Series"),
}
MAX_DEPTH = 3
WORKERS = 3  # Shared ISP server: stay gentle.
LINK = re.compile(r'<td class="fb-n"><a href="([^"]+)">([^<]+)</a>')
YEAR = re.compile(r"\(.*?((?:19|20)\d{2}).*?\)")
IMAGE = (".jpg", ".jpeg", ".png", ".webp")


def list_dir(path):
    """Return [(href, display name)] for a folder, retrying once on failure."""
    for attempt in range(2):
        try:
            with urllib.request.urlopen(HOST + path, timeout=30) as response:
                body = response.read().decode("utf-8", "replace")
            return [
                (html.unescape(href), html.unescape(name).strip())
                for href, name in LINK.findall(body)
                if name != "Parent Directory" and href != ".."
            ]
        except OSError as error:
            if attempt:
                print(f"  failed {path}: {error}", file=sys.stderr)
            time.sleep(1)
    return []


def find_titles(path, depth=0):
    """Walk index folders such as 'Anime-TV Series A - F' down to titled folders."""
    titles = []
    for href, name in list_dir(path):
        if not href.endswith("/"):
            continue
        if YEAR.search(name):
            titles.append((href, name))
        elif depth < MAX_DEPTH:
            titles.extend(find_titles(href, depth + 1))
    return titles


def to_entry(item, category):
    href, name = item
    poster = next(
        (h for h, _ in list_dir(href) if h.lower().endswith(IMAGE)),
        None,
    )
    return {
        "title": name.split("(")[0].strip() or name,
        "year": YEAR.search(name).group(1),
        "category": category,
        "posterUrl": HOST + poster if poster else None,
        "url": HOST + href,
        "tmdbId": None,
        "overview": None,
        "rating": None,
    }


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for filename, (section, category) in SECTIONS.items():
        start = ROOT + urllib.parse.quote(section)
        titles = find_titles(start)
        with ThreadPoolExecutor(WORKERS) as pool:
            entries = list(pool.map(lambda item: to_entry(item, category), titles))
        entries.sort(key=lambda entry: entry["title"].lower())
        with open(OUT / filename, "w", encoding="utf-8") as output:
            json.dump(entries, output, ensure_ascii=False, indent=2)
        with_poster = sum(1 for entry in entries if entry["posterUrl"])
        print(f"{filename}: {len(entries)} titles, {with_poster} with posters", flush=True)


if __name__ == "__main__":
    main()
