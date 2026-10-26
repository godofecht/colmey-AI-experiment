#!/usr/bin/env python3
"""Fetch the exact specialist model and preprocessing metadata for reproducible Android builds."""
import json
import os
import pathlib
import urllib.request

SOURCE = "onnx-community/musical-instruments-ONNX"
BASE = "https://huggingface.co/" + SOURCE + "/resolve/main/"
OUT = pathlib.Path("app/src/main/assets")
OUT.mkdir(parents=True, exist_ok=True)

def fetch(url, path):
    req = urllib.request.Request(url, headers={"User-Agent": "ColmeyAI-CI/1.0"})
    with urllib.request.urlopen(req, timeout=120) as response, path.open("wb") as output:
        import shutil
        shutil.copyfileobj(response, output)
    if path.stat().st_size == 0:
        raise RuntimeError("Empty downloaded model asset: " + url)

def metadata(name, fallback):
    try:
        fetch(BASE + name, OUT / fallback)
    except Exception:
        fetch("https://huggingface.co/larynx1982/musical-instruments/resolve/main/" + name, OUT / fallback)

metadata("config.json", "instrument_config.json")
metadata("preprocessor_config.json", "instrument_preprocessor.json")
config = json.loads((OUT / "instrument_config.json").read_text())
assert len(config["id2label"]) == 5, "Unexpected classifier label count"
assert set(s.lower() for s in config["id2label"].values()) == {"drums", "guitar", "piano", "saxophone", "violin"}
processor = json.loads((OUT / "instrument_preprocessor.json").read_text())
assert "image_mean" in processor and "image_std" in processor, "Missing preprocessing normalization"

for filename in ("onnx/model_quantized.onnx", "onnx/model.onnx"):
    try:
        fetch(BASE + filename, OUT / "instrument.onnx")
        if (OUT / "instrument.onnx").stat().st_size < 1_000_000:
            raise RuntimeError("ONNX model unexpectedly small")
        print("Bundled", filename, (OUT / "instrument.onnx").stat().st_size, "bytes")
        break
    except Exception as exc:
        print("Model candidate unavailable:", filename, exc)
else:
    raise RuntimeError("No ONNX image classifier could be downloaded; failing build")
