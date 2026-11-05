#!/usr/bin/env python3
"""Download reproducible real-world instrument photographs for Android emulator tests.

Fixtures are test-only assets and are never included in the production APK.
Each photo is sourced from Wikimedia Commons; licensing and attribution below.
"""
import pathlib
import time
import urllib.error
import urllib.parse
import urllib.request
from PIL import Image
import io

OUT = pathlib.Path("app/src/androidTest/assets/instrument_photos")
OUT.mkdir(parents=True, exist_ok=True)

# filename, Commons filename, attribution, license
PHOTOS = [
    ("guitar.jpg", "Acoustic Guitar.jpg", "RachelTara", "CC BY-SA 3.0"),
    ("violin.jpg", "Violin front view.jpg", "LJ", "Public domain"),
    ("piano.jpg", "PianoKeyboard3.jpg", "Raysonho", "CC0 1.0"),
]

for local, commons, author, license_name in PHOTOS:
    url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(commons)
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "ColmeyAI-TestFixtures/1.1 (https://github.com/godofecht/colmey-AI-experiment)",
            "Accept": "image/*",
        },
    )
    # Wikimedia can temporarily rate-limit shared CI runner IP addresses.
    # Retry only transient responses; never replace real photos with mock data.
    for attempt in range(1, 6):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                data = response.read(15_000_001)
            if len(data) > 15_000_000:
                raise ValueError(f"Image exceeds fixture size limit: {commons}")
            break
        except urllib.error.HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 5:
                raise
            delay = min(60, 5 * (2 ** (attempt - 1)))
            retry_after = exc.headers.get("Retry-After") if exc.headers else None
            if retry_after:
                try:
                    delay = max(delay, min(120, float(retry_after)))
                except ValueError:
                    pass
            print(f"{commons}: HTTP {exc.code}; retry {attempt}/4 after {delay:g}s", flush=True)
            time.sleep(delay)
        except urllib.error.URLError as exc:
            if attempt == 5:
                raise
            delay = min(60, 5 * (2 ** (attempt - 1)))
            print(f"{commons}: {exc}; retry {attempt}/4 after {delay}s", flush=True)
            time.sleep(delay)
    with Image.open(io.BytesIO(data)) as image:
        image = image.convert("RGB")
        if min(image.size) < 200:
            raise RuntimeError(f"Fixture too small: {local} {image.size}")
        image.thumbnail((960, 960))
        image.save(OUT / local, "JPEG", quality=85)
    print(f"{local}: {len(data)} bytes, {author}, {license_name}, {url}")
print("Downloaded", len(PHOTOS), "real instrument photographs")
