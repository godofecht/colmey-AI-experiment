# Colmey AI — offline instrument recognition (Android)

Photograph an instrument or choose an image to identify it offline and view its historical origin.

## Recognition model
The app now uses **MobileCLIP2-S0** image-text retrieval, comparing a photograph against 55 instrument names and descriptions. This includes sitar, tabla, oud, erhu, kora, guzheng and other instruments. The Android app displays the ten highest relative scores. These scores are softmax-normalized similarities, not calibrated identification probabilities.

- Source: https://huggingface.co/plhery/mobileclip2-onnx
- Model: MobileCLIP2-S0 vision encoder; the text encoder runs at build time to create the instrument search index.
- The model license is Apple's research-oriented Apple Sample Code License. Check licensing before commercial redistribution.

The ONNX model and its preprocessor configuration and precomputed text embeddings are fetched by `scripts/fetch_instrument_model.py` and bundled in the APK at build time. The model does not download or upload photos during use. The update checker separately accesses GitHub Releases when connected to the internet.

## Build and tests

Requires Android SDK 35, JDK 17, Gradle 8.10.2 and internet during the build to fetch model weights.

```sh
python3 -m pip install numpy onnxruntime transformers tokenizers
python3 scripts/fetch_instrument_model.py
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

GitHub Actions downloads the model, builds and lints the app, runs unit tests and launches an emulator to verify that the actual bundled ONNX model can process pixels. The release job is gated on those tests.

**Accuracy is not yet established:** A blank-image inference smoke test only proves the model runs. Real-world accuracy needs a held-out photographic benchmark with representative lighting, backgrounds, and instrument types. The 55-name retrieval index is not exhaustive, and scores are not calibrated probabilities.

## Releases and signing
- CI builds are available at https://github.com/godofecht/colmey-AI-experiment/actions
- Versioned prereleases appear at https://github.com/godofecht/colmey-AI-experiment/releases
- Stable signing instructions: [docs/ANDROID_SIGNING.md](docs/ANDROID_SIGNING.md)

Origins describe instrument families, not the photographed object's provenance.
