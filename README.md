# C0reStream

Android IPTV and media client for user-provided, authorized streams. Application ID `pw.c0re.stream`, minimum Android 11 (API 30), target/compile API 37.1.

## Features

- M3U/M3U8 playlists and Xtream Codes sources, stable channel IDs, deduplication, automatic refresh.
- Live TV with Media3 (HLS/DASH/MPEG-TS), mini-player, background playback, PiP, audio/subtitle/quality selection, sleep timer, retry and resume.
- XMLTV EPG (plain or gzip), programme guide, reminders and catch-up URLs.
- VOD movies, series and episodes with watchlist and resume positions.
- Encrypted backup/restore (PBKDF2-HMAC-SHA256, AES-256-GCM) including settings, favourites, history, progress, watchlist and reminders.
- Material 3 responsive UI, Light/Dark/AMOLED, Android TV launcher, German/English/French.
- Credentials and stream URLs encrypted at rest with an Android Keystore key; URL/parser limits and HTTPS-downgrade protection.

HTTP sources remain supported by explicit owner decision; HTTPS is preferred and credentials are still encrypted. No ads, tracking or bundled streams.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Toolchain: JDK 17, Android SDK 37.1, build-tools 37.0.0, Gradle wrapper 9.9.2. Production signing requires `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` (the `C0RE_*` names are accepted as aliases); without them release builds stay unsigned. CI verifies the certificate SHA-256 fingerprint against `ANDROID_CERT_SHA256` before publishing.

## Releases

CI builds and publishes checksummed debug previews from `main`. The signed production APK is published as [C0reStream v1.3.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.3.0) with `SHA256SUMS` and a verified certificate fingerprint. Verify the checksum and signature before installation.

Android 11–17 compatibility work covers compilation against API 37.1 plus instrumented tests on an API 30/36 emulator matrix in CI. Other API levels and physical-device behaviour are not certified; results are recorded in `PROJECT_STATUS_REPORT.md`. Android 18 work is limited to documented, feature-detected preparation until official SDKs and system images are available.

See `MASTERPLAN.md`, `SECURITY.md` and `PROJECT_STATUS_REPORT.md`.
