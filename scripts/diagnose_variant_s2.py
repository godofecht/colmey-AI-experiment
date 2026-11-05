#!/usr/bin/env python3
"""Evaluate MobileCLIP2-S2 on unchanged real-photo fixtures without altering the APK.

Run in the Android CI instrumentation job after existing photo and S0 model fetches.
The alternative vision/text ONNX models are from the same S2 export and are
discarded after diagnostics. Production remains S0 until separately verified.
"""
import json
import pathlib
import shutil
import tempfile
import urllib.request

import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

from diagnose_instrument_model import preprocess

BASE = "https://huggingface.co/plhery/mobileclip2-onnx/resolve/main/"
ASSETS = pathlib.Path("app/src/main/assets")
PHOTOS = pathlib.Path("app/src/androidTest/assets/instrument_photos")
REFERENCE_LABELS = ("guitar", "violin", "piano")
BOS = 49406
EOS = 49407
CONTEXT = 77


def fetch(relative_path, output):
    request = urllib.request.Request(
        BASE + relative_path,
        headers={"User-Agent": "ColmeyAI-CI/2.0 (real-photo model variant audit)"},
    )
    with urllib.request.urlopen(request, timeout=180) as response, output.open("wb") as target:
        shutil.copyfileobj(response, target)
    if output.stat().st_size < 100:
        raise RuntimeError("Truncated S2 export " + relative_path)


def main():
    records = json.loads((ASSETS / "instrument_embeddings.json").read_text())
    labels = [record["label"] for record in records]
    if len(labels) != 55:
        raise RuntimeError("Unexpected class count")

    with tempfile.TemporaryDirectory(prefix="mobileclip2-s2-") as temporary:
        directory = pathlib.Path(temporary)
        vision_path = directory / "vision_model.onnx"
        text_path = directory / "text_model.onnx"
        config_path = directory / "preprocessor_config.json"
        tokenizer_path = directory / "tokenizer.json"
        for relative, output in (
            ("onnx/s2/vision_model.onnx", vision_path),
            ("onnx/s2/text_model.onnx", text_path),
            ("onnx/s2/preprocessor_config.json", config_path),
            ("tokenizer.json", tokenizer_path),
        ):
            fetch(relative, output)
            print(f"S2 diagnostic downloaded {relative}: {output.stat().st_size} bytes", flush=True)

        vision = ort.InferenceSession(str(vision_path), providers=["CPUExecutionProvider"])
        text = ort.InferenceSession(str(text_path), providers=["CPUExecutionProvider"])
        token_encoder = Tokenizer.from_file(str(tokenizer_path))
        encoded = token_encoder.encode_batch(
            ["a photograph of a " + label + " musical instrument" for label in labels],
            add_special_tokens=False,
        )
        tokens = np.full((len(labels), CONTEXT), EOS, dtype=np.int64)
        for index, item in enumerate(encoded):
            body = item.ids
            if len(body) > CONTEXT - 2:
                raise RuntimeError("S2 prompt exceeds CLIP context: " + labels[index])
            tokens[index, 0] = BOS
            tokens[index, 1:len(body) + 1] = body
            tokens[index, len(body) + 1] = EOS

        text_inputs = text.get_inputs()
        if len(text_inputs) != 1 or text_inputs[0].name != "input_ids":
            raise RuntimeError("Unexpected S2 text encoder inputs: " + repr([x.name for x in text_inputs]))
        vectors = np.asarray(text.run(["text_embeds"], {"input_ids": tokens})[0], dtype=np.float32)
        vectors /= np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-9)
        if vectors.shape != (len(labels), 512):
            raise RuntimeError("Unexpected S2 text embedding shape " + repr(vectors.shape))

        config = json.loads(config_path.read_text())
        correct = 0
        for expected in REFERENCE_LABELS:
            tensor = preprocess(PHOTOS / (expected + ".jpg"), config)
            image = np.asarray(
                vision.run(["image_embeds"], {"pixel_values": tensor})[0], dtype=np.float32
            )[0]
            image /= max(float(np.linalg.norm(image)), 1e-9)
            cosines = vectors @ image
            order = np.argsort(-cosines)
            rank = int(np.where(np.asarray(labels)[order] == expected)[0][0]) + 1
            correct += int(rank <= 5)
            print(
                f"S2 reference: expected={expected} rank={rank} "
                f"top5={[labels[index] for index in order[:5]]} "
                f"correct_cosine={cosines[labels.index(expected)]:.5f} "
                f"top_cosine={cosines[order[0]]:.5f}",
                flush=True,
            )
        print(f"S2 Python ONNX top5={correct}/{len(REFERENCE_LABELS)}", flush=True)


if __name__ == "__main__":
    main()
