# Colmey AI — offline instrument explorer (Android)

Android app to photograph or choose an image of an instrument and get a **best-effort** identification and brief cultural history.

## Privacy
The app uses the **bundled** Google ML Kit image-labeling model. Classification runs on-device; it does not upload photographs or require an account or network connection.

## Important limitations
This is an early prototype, **not** a universal musical instrument recognizer. The generic model may fail to recognise uncommon, traditional, or visually similar instruments. A label's confidence is not the probability that its historical origin is correct. Historical text is a curated summary of instrument families, **not** evidence about the photographed object's manufacture or ownership. No recognition result is better than a wrong invented origin.

## Build
Requires Android SDK 35, JDK 17 and Gradle 8.10.2.

```sh
gradle :app:assembleDebug :app:testDebugUnitTest
```

Install `app/build/outputs/apk/debug/app-debug.apk` on Android 7.0+.

GitHub Actions builds an APK artifact on every push. A successful build of `main` creates or updates the **v0.1.0 prerelease debug APK** under GitHub Releases. GitHub Actions must be enabled and the workflow must have permission to write repository contents. Debug APKs are not production-signed Play Store releases.

## Next milestone
Replace generic image labels with an evaluated offline instrument-specific model (e.g. quantized TFLite classifier), add an instrument taxonomy with sourced provenance, top-k results, uncertainty, and field-device tests. The current prototype is deliberately conservative about unsupported identifications.
