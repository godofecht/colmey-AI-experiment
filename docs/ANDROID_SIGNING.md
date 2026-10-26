# Stable Android signing for in-place updates

Android only installs an update over an existing APK if the application ID matches, the version code increases, and the signing certificate is compatible. GitHub Actions' automatically generated debug keystores do **not** provide a stable certificate.

Generate one private keystore locally (do not commit it):

```sh
keytool -genkeypair -v -keystore colmey-release.jks -alias colmey -keyalg RSA -keysize 3072 -validity 10000
base64 < colmey-release.jks | tr -d '\n'
```

In repository **Settings → Secrets and variables → Actions**, add four repository secrets:

- `COLMEY_KEYSTORE_BASE64`: the one-line base64 output above
- `COLMEY_KEYSTORE_PASSWORD`: keystore password
- `COLMEY_KEY_ALIAS`: `colmey`
- `COLMEY_KEY_PASSWORD`: key password

Back up the keystore and passwords securely. Losing them means Android cannot update existing installations signed with that certificate.

The CI pipeline runs build, lint, unit tests and emulator tests before the release job. The release job signs `app-release.apk` with the secret-backed key, verifies the signature using `apksigner`, then publishes `colmey-ai.apk` to a new `v0.1.<run-number>` GitHub Release.

**Important:** A previously installed debug-signed build has a different certificate and cannot be upgraded in place to the newly signed release. Uninstall that older preview once, then install the stable-signed release. Future releases with the same keystore and higher version code can update it in place.

The app checks the public GitHub Releases API at startup and offers a user-approved download. It does not silently install applications, and offline instrument identification does not require an internet connection.
