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

Toolchain: JDK 17, Android SDK 37.1, build-tools 37.0.0, Gradle wrapper 9.9.2. Production signing requires `C0RE_KEYSTORE_FILE`, `C0RE_KEYSTORE_PASSWORD`, `C0RE_KEY_ALIAS` and `C0RE_KEY_PASSWORD`; without them release builds stay unsigned.

## Releases

CI builds and publishes checksummed debug previews from `main`. Production APKs are published only after unit/instrumented tests, lint, durable signing and signature verification succeed. See [Releases](https://github.com/klatschanstalt/C0reStream/releases).

Android 11–17 compatibility is validated by compilation plus the instrumented test matrix; device-specific results are recorded separately in `PROJECT_STATUS_REPORT.md`. Android 18 work is limited to documented, feature-detected preparation until official SDKs and system images are available.

See `MASTERPLAN.md`, `SECURITY.md` and `PROJECT_STATUS_REPORT.md`.
