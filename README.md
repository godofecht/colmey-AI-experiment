# Colmey AI — offline instrument recognition (Android)

Photograph an instrument or choose an image to identify it offline and view its historical origin.

## Recognition model
The app now uses a specialist **ViT image classifier**, not the generic Google ML Kit labeler:
- Source: https://huggingface.co/larynx1982/musical-instruments
- Android ONNX conversion: https://huggingface.co/onnx-community/musical-instruments-ONNX
- Supported image classes: drums, guitar, piano, saxophone, violin.
- Other instruments are available through the offline manual browser, **not automatic identification**.

The ONNX model and its `config.json` and `preprocessor_config.json` are fetched by `scripts/fetch_instrument_model.py` and bundled in the APK at build time. The model does not download or upload photos during use. The update checker separately accesses GitHub Releases when connected to the internet.

## Build and tests

Requires Android SDK 35, JDK 17, Gradle 8.10.2 and internet during the build to fetch model weights.

```sh
python3 scripts/fetch_instrument_model.py
gradle :app:assembleDebug :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

GitHub Actions downloads the model, builds and lints the app, runs unit tests and launches an emulator to verify that the actual bundled ONNX model can process pixels. The release job is gated on those tests.

**Accuracy is not yet established:** The source model reports a self-evaluated accuracy of ~95.5%, which is not an independent Colmey AI evaluation. A blank-image inference smoke test only proves the model runs. Real-world accuracy needs a held-out photographic benchmark with representative lighting, backgrounds, and instrument types. A five-class model cannot recognize every instrument.

## Releases and signing
- CI builds are available at https://github.com/godofecht/colmey-AI-experiment/actions
- Versioned prereleases appear at https://github.com/godofecht/colmey-AI-experiment/releases
- Stable signing instructions: [docs/ANDROID_SIGNING.md](docs/ANDROID_SIGNING.md)

Origins describe instrument families, not the photographed object's provenance.
