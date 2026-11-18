#!/usr/bin/env python3
"""Compare bundled MobileCLIP text vectors with direct Python ONNX image inference.

This is a diagnostic, not a substitute for the Android emulator acceptance test.
It executes the same exported vision model against the real downloaded photos.
"""
import json
import pathlib

import numpy as np
import onnxruntime as ort
from PIL import Image

ASSETS = pathlib.Path("app/src/main/assets")
PHOTOS = pathlib.Path("app/src/androidTest/assets/instrument_photos")
LABELS = ("guitar", "violin", "piano")


def preprocess(path, config):
    target = config.get("crop_size", {"height": 256, "width": 256})
    target_width = int(target["width"])
    target_height = int(target["height"])
    shortest_edge = int(config.get("size", {}).get("shortest_edge", min(target_width, target_height)))
    if min(target_width, target_height, shortest_edge) <= 0:
        raise ValueError("Invalid CLIP preprocessing dimensions")
    with Image.open(path) as source:
        image = source.convert("RGB")
    width, height = image.size
    scale = shortest_edge / min(width, height)
    resized = image.resize((max(target_width, round(width * scale)),
                            max(target_height, round(height * scale))), resample=Image.Resampling.BICUBIC)
    left = (resized.width - target_width) // 2
    top = (resized.height - target_height) // 2
    image = resized.crop((left, top, left + target_width, top + target_height))
    pixels = np.asarray(image, dtype=np.float32)
    if config.get("do_rescale", True):
        pixels *= float(config.get("rescale_factor", 1 / 255))
    if config.get("do_normalize", True):
        mean = np.asarray(config.get("image_mean", [0, 0, 0]), dtype=np.float32)
        std = np.asarray(config.get("image_std", [1, 1, 1]), dtype=np.float32)
        pixels = (pixels - mean) / std
    return np.transpose(pixels, (2, 0, 1))[None, :, :, :].copy()


def main():
    config = json.loads((ASSETS / "instrument_preprocessor.json").read_text())
    records = json.loads((ASSETS / "instrument_embeddings.json").read_text())
    labels = [record["label"] for record in records]
    vectors = np.asarray([record["vector"] for record in records], dtype=np.float32)
    vectors /= np.maximum(np.linalg.norm(vectors, axis=1, keepdims=True), 1e-9)

    pairwise = vectors @ vectors.T
    off_diagonal = pairwise[~np.eye(len(labels), dtype=bool)]
    print(f"Text embedding audit: labels={len(labels)} dimensions={vectors.shape[1]} "
          f"off_diagonal_cosine_mean={off_diagonal.mean():.5f} "
          f"off_diagonal_cosine_std={off_diagonal.std():.5f}", flush=True)
    session = ort.InferenceSession(str(ASSETS / "instrument.onnx"), providers=["CPUExecutionProvider"])
    input_name = session.get_inputs()[0].name
    output_names = [value.name for value in session.get_outputs()]
    output_name = "image_embeds" if "image_embeds" in output_names else output_names[0]
    correct = 0
    for expected in LABELS:
        tensor = preprocess(PHOTOS / f"{expected}.jpg", config)
        image_features = np.asarray(session.run([output_name], {input_name: tensor})[0], dtype=np.float32)[0]
        image_features /= max(float(np.linalg.norm(image_features)), 1e-9)
        similarities = vectors @ image_features
        order = np.argsort(-similarities)
        rank = int(np.where(np.asarray(labels)[order] == expected)[0][0]) + 1
        correct += int(rank <= 5)
        print(f"Python ONNX reference: expected={expected} rank={rank} "
              f"top5={[labels[index] for index in order[:5]]} "
              f"expected_cosine={similarities[labels.index(expected)]:.5f} "
              f"top_cosine={similarities[order[0]]:.5f}", flush=True)
    print(f"Python ONNX top5={correct}/{len(LABELS)}", flush=True)


if __name__ == "__main__":
    main()
