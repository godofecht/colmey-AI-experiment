#!/usr/bin/env python3
"""Download reproducible real-world instrument photographs for Android emulator tests.

Fixtures are test-only assets and are never included in the production APK.
Each photo is sourced from Wikimedia Commons; licensing and attribution below.
"""
import pathlib
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
    request = urllib.request.Request(url, headers={"User-Agent": "ColmeyAI-Tests/1.0 (instrument image validation)"})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read(15_000_000)
    with Image.open(io.BytesIO(data)) as image:
        image = image.convert("RGB")
        if min(image.size) < 200:
            raise RuntimeError(f"Fixture too small: {local} {image.size}")
        image.thumbnail((960, 960))
        image.save(OUT / local, "JPEG", quality=85)
    print(f"{local}: {len(data)} bytes, {author}, {license_name}, {url}")
print("Downloaded", len(PHOTOS), "real instrument photographs")
