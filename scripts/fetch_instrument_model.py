#!/usr/bin/env python3
"""Package MobileCLIP2-S0 image encoder and precomputed instrument text embeddings."""
import json
import pathlib
import shutil
import urllib.request

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

SOURCE = "https://huggingface.co/plhery/mobileclip2-onnx/resolve/main/onnx/s0/"
ASSETS = pathlib.Path("app/src/main/assets")
ASSETS.mkdir(parents=True, exist_ok=True)

INSTRUMENTS = [
    "accordion", "bagpipes", "balalaika", "banjo", "bass guitar", "bassoon",
    "berimbau", "bongo drums", "cello", "clarinet", "conga drums", "didgeridoo",
    "djembe", "double bass", "drum kit", "erhu", "flute", "french horn",
    "gamelan", "guitar", "guqin", "guzheng", "harmonica", "harp", "harpsichord",
    "horn", "kalimba", "kora", "mandolin", "marimba", "mridangam", "oboe",
    "ocarina", "organ", "oud", "piano", "pipa", "santoor", "sarangi",
    "saxophone", "shamisen", "sheng", "sitar", "steelpan", "tabla",
    "tambourine", "timpani", "trombone", "trumpet", "tuba", "ukulele",
    "veena", "viola", "violin", "xylophone"
]

def fetch(url, destination):
    req = urllib.request.Request(url, headers={"User-Agent": "ColmeyAI-CI/2.0"})
    with urllib.request.urlopen(req, timeout=180) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)
    if destination.stat().st_size < 1_000_000:
        raise RuntimeError("Missing or truncated ONNX model: " + url)

fetch(SOURCE + "vision_model.onnx", ASSETS / "instrument.onnx")
text_path = ASSETS / "_text_model.onnx"
fetch(SOURCE + "text_model.onnx", text_path)
with urllib.request.urlopen(SOURCE + "preprocessor_config.json", timeout=30) as response:
    processor = json.load(response)
(ASSETS / "instrument_preprocessor.json").write_text(json.dumps(processor))

tokenizer_file = ASSETS / "_tokenizer.json"
with urllib.request.urlopen("https://huggingface.co/plhery/mobileclip2-onnx/resolve/main/tokenizer.json", timeout=90) as response, tokenizer_file.open("wb") as out:
    shutil.copyfileobj(response, out)
tokenizer = Tokenizer.from_file(str(tokenizer_file))
tokenizer.enable_truncation(max_length=77)
tokenizer.enable_padding(length=77, pad_id=0, pad_token="<pad>")
session = ort.InferenceSession(str(text_path), providers=["CPUExecutionProvider"])
input_info = session.get_inputs()
print("Text model inputs:", [(i.name, i.shape, i.type) for i in input_info])
prompts = ["a photograph of a " + name + " musical instrument" for name in INSTRUMENTS]
tokens = np.asarray([item.ids for item in tokenizer.encode_batch(prompts)], dtype=np.int64)
feeds = {}
for item in input_info:
    if item.name == "input_ids":
        feeds[item.name] = tokens
    elif item.name == "attention_mask":
        feeds[item.name] = (tokens != 0).astype(np.int64)
    else:
        raise RuntimeError("Unsupported text model input " + item.name)
outputs = session.run(None, feeds)
output_names = [output.name for output in session.get_outputs()]
print("Text outputs:", [(name, value.shape) for name, value in zip(output_names, outputs)])
candidate = next((value for name, value in zip(output_names, outputs) if name in ("text_embeds", "text_features", "embeddings")), None)
if candidate is None:
    candidate = next((value for value in outputs if value.ndim == 2 and value.shape[0] == len(INSTRUMENTS)), None)
if candidate is None:
    raise RuntimeError("No batch-aligned text embedding output")
vectors = np.asarray(candidate, dtype=np.float32)
if vectors.ndim != 2 or vectors.shape[0] != len(INSTRUMENTS):
    raise RuntimeError("Unexpected text embedding dimensions " + str(vectors.shape))
vectors /= np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-9)
(ASSETS / "instrument_embeddings.json").write_text(json.dumps([
    {"label": name, "vector": vector.tolist()}
    for name, vector in zip(INSTRUMENTS, vectors)
]))
text_path.unlink()
tokenizer_file.unlink()
ort.InferenceSession(str(ASSETS / "instrument.onnx"), providers=["CPUExecutionProvider"])
print("Vision model parsed successfully")
print("Packaged MobileCLIP2-S0 and", len(INSTRUMENTS), "instrument text embeddings")
