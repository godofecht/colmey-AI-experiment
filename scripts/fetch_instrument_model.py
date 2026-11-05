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
session = ort.InferenceSession(str(text_path), providers=["CPUExecutionProvider"])
input_info = session.get_inputs()
print("Text model inputs:", [(i.name, i.shape, i.type) for i in input_info])
prompts = ["a photograph of a " + name + " musical instrument" for name in INSTRUMENTS]

# The ONNX text tower was exported using OpenCLIP's tokenizer, which requires
# explicit CLIP BOS/EOS tokens and an exact 77-token context. The downloaded
# raw tokenizers JSON does not guarantee equivalent special-token postprocessing.
# Encode only the BPE body, then construct the sequence deterministically.
CLIP_BOS = 49406
CLIP_EOS = 49407
CLIP_CONTEXT = 77
encoded = tokenizer.encode_batch(prompts, add_special_tokens=False)
tokens = np.full((len(INSTRUMENTS), CLIP_CONTEXT), CLIP_EOS, dtype=np.int64)
attention_masks = np.zeros_like(tokens)
for index, item in enumerate(encoded):
    body = item.ids
    if len(body) > CLIP_CONTEXT - 2:
        raise RuntimeError("Prompt exceeds CLIP context: " + prompts[index])
    tokens[index, 0] = CLIP_BOS
    tokens[index, 1 : len(body) + 1] = body
    tokens[index, len(body) + 1] = CLIP_EOS
    attention_masks[index, : len(body) + 2] = 1

assert tokens.shape == attention_masks.shape == (len(INSTRUMENTS), 77)
assert np.all(tokens[:, 0] == CLIP_BOS)
assert np.all(np.sum(attention_masks, axis=1) >= 3)
assert np.all(np.sum(attention_masks, axis=1) <= CLIP_CONTEXT)
print("CLIP token sanity: first IDs", tokens[0, :12].tolist(),
      "first EOS index", int(np.sum(attention_masks[0])) - 1, flush=True)
feeds = {}
for item in input_info:
    if item.name == "input_ids":
        feeds[item.name] = tokens
    elif item.name == "attention_mask":
        feeds[item.name] = attention_masks
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
vision = ort.InferenceSession(str(ASSETS / "instrument.onnx"), providers=["CPUExecutionProvider"])
vision_inputs = vision.get_inputs()
vision_outputs = vision.get_outputs()
print("Vision inputs:", [(i.name, i.shape, i.type) for i in vision_inputs])
print("Vision outputs:", [(o.name, o.shape, o.type) for o in vision_outputs])
if len(vision_inputs) != 1 or vision_inputs[0].name != "pixel_values":
    raise RuntimeError("Unexpected vision model input signature")
if not any(o.name == "image_embeds" for o in vision_outputs):
    raise RuntimeError("Vision model does not expose image_embeds")
if vectors.shape[1] != 512:
    raise RuntimeError("Expected MobileCLIP2-S0 512-dimensional embeddings")
print("Vision model parsed and embeddings validated")

# Audit fixed CLIP prompt templates against actual photos *without* selecting
# or tuning a template on these three acceptance fixtures. This distinguishes
# poorly aligned text prompts from image preprocessing/model export problems.
photo_dir = pathlib.Path("app/src/androidTest/assets/instrument_photos")
reference_labels = ("guitar", "violin", "piano")
if all((photo_dir / (label + ".jpg")).is_file() for label in reference_labels):
    from diagnose_instrument_model import preprocess

    image_vectors = []
    for label in reference_labels:
        image_tensor = preprocess(photo_dir / (label + ".jpg"), processor)
        image_vector = np.asarray(vision.run(["image_embeds"], {"pixel_values": image_tensor})[0][0], dtype=np.float32)
        image_vector /= max(float(np.linalg.norm(image_vector)), 1e-9)
        image_vectors.append(image_vector)

    templates = (
        "{}",
        "a {}",
        "a photo of a {}",
        "an image of a {}",
        "a close-up photo of a {}",
        "a person playing a {}",
        "the musical instrument {}",
        "a photograph of a {} musical instrument",
    )
    for template in templates:
        audit_prompts = [template.format(name) for name in INSTRUMENTS]
        audit_encoded = tokenizer.encode_batch(audit_prompts, add_special_tokens=False)
        audit_tokens = np.full((len(INSTRUMENTS), CLIP_CONTEXT), CLIP_EOS, dtype=np.int64)
        audit_masks = np.zeros_like(audit_tokens)
        for index, item in enumerate(audit_encoded):
            if len(item.ids) > CLIP_CONTEXT - 2:
                raise RuntimeError("Benchmark prompt exceeds CLIP context: " + audit_prompts[index])
            audit_tokens[index, 0] = CLIP_BOS
            audit_tokens[index, 1 : len(item.ids) + 1] = item.ids
            audit_tokens[index, len(item.ids) + 1] = CLIP_EOS
            audit_masks[index, : len(item.ids) + 2] = 1
        audit_feeds = {}
        for text_input in input_info:
            if text_input.name == "input_ids":
                audit_feeds[text_input.name] = audit_tokens
            elif text_input.name == "attention_mask":
                audit_feeds[text_input.name] = audit_masks
            else:
                raise RuntimeError("Unsupported benchmark text input " + text_input.name)
        audit_vectors = np.asarray(session.run(["text_embeds"], audit_feeds)[0], dtype=np.float32)
        audit_vectors /= np.maximum(np.linalg.norm(audit_vectors, axis=1, keepdims=True), 1e-9)
        ranks = []
        for label, image_vector in zip(reference_labels, image_vectors):
            scores = audit_vectors @ image_vector
            order = np.argsort(-scores)
            rank = int(np.where(np.asarray(INSTRUMENTS)[order] == label)[0][0]) + 1
            ranks.append(rank)
        print(f"Prompt audit: template={template!r} ranks={dict(zip(reference_labels, ranks))} "
              f"top5={sum(rank <= 5 for rank in ranks)}/{len(ranks)}", flush=True)

text_path.unlink()
tokenizer_file.unlink()
print("Packaged MobileCLIP2-S0 and", len(INSTRUMENTS), "instrument text embeddings")
