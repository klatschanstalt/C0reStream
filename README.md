# C0reStream

<p align="center"><img src="assets/branding/logo-transparent.png" alt="C0reStream" width="96"></p>

**Your Streams. Your Rules.**

[![Build](https://github.com/klatschanstalt/C0reStream/actions/workflows/build-apk.yml/badge.svg)](https://github.com/klatschanstalt/C0reStream/actions/workflows/build-apk.yml)
[![Release](https://img.shields.io/github/v/release/klatschanstalt/C0reStream)](https://github.com/klatschanstalt/C0reStream/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/klatschanstalt/C0reStream/total)](https://github.com/klatschanstalt/C0reStream/releases)

C0reStream is an Android media client for user-provided, authorized IPTV playlists and VOD libraries. It plays the streams you supply; it does **not** provide IPTV content, subscriptions, accounts or any bundled streams.

## Features

- **Playlists** — M3U/M3U8, Xtream Codes, local file import, stable channel IDs, deduplication, groups, favourites, automatic refresh, transparent import statistics and paged access to every channel.
- **Player** — Media3 HLS/DASH/MPEG-TS, mini-player, background playback, PiP, immersive fullscreen with nine scaling modes, audio/subtitle/video-track selection, sleep timer, reconnect, catch-up and VOD resume.
- **EPG** — XMLTV (plain or gzip), programme guide, cache, reminders and catch-up links.
- **VOD** — movies, series and episodes with search, watchlist and resume positions.
- **Library** — encrypted backup/restore including settings, favourites, history, progress, watchlist and reminders.
- **Interface** — Material 3, Light/Dark/AMOLED, responsive layout, German/English/French, Android TV launcher entry and keyboard/D-pad focus navigation.
- **Settings** — categorized center with expert mode, reset and persistent global/per-channel playback preferences.
- **Library** — local profiles with PIN protection, QR playlist import and user-authorized recording of plain streams.
- **Updates** — signed resource manifests and APK updates from public GitHub Release assets, plus automatic cleanup of stale downloads.
- **Privacy** — credentials and URLs encrypted with an Android Keystore key, bounded parsers, URL validation and HTTPS-downgrade protection.

HTTP sources remain supported by owner decision; HTTPS is preferred and plaintext transport risk is shown in settings. No ads, analytics or tracking.

## Requirements

- Android 11 (API 30) or newer. Target API 37, compile SDK 37.1.
- A network connection to the media sources you own or are authorized to use.
- Android TV: launcher banner and focus navigation are present; TV hardware is not part of the automated test matrix.

## Install and verify

1. Download the current signed APK: [C0reStream.apk (latest)](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk) or the [version-bound v1.6.0 APK](https://github.com/klatschanstalt/C0reStream/releases/download/v1.6.0/C0reStream.apk).
2. Verify the checksum with [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS):

   ```bash
   sha256sum --check SHA256SUMS
   ```

   Version 1.6.0 SHA-256: `4787f7ff5766b8db548aabbff527f710167dd1ba28b06767971f0e38c51a97ee` (6,552,789 bytes).
3. Install the APK (Android may require allowing installation from unknown sources). The signing certificate fingerprint is `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`.

## Updates

The app reads the public `update.json` from `main` without any GitHub token. Non-executable resources use separately signed `resources.json`/`resources.sig` release assets with checksums, ETags, resumable downloads and rollback. If a newer APK is available, it downloads the version-bound APK over HTTPS and installs it only after SHA-256, package-name and signing-certificate checks plus your confirmation. You can always use the [permanent latest-release link](https://github.com/klatschanstalt/C0reStream/releases/latest) manually.

## Build

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest   # requires a connected device or emulator
```

Toolchain: JDK 17, Android SDK 37.1, build-tools 37.0.0, Gradle wrapper 9.9.2. Production signing requires `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`; CI checks the certificate fingerprint against `ANDROID_CERT_SHA256` before publishing. Only signed production releases are published.

## Security and license

See [SECURITY.md](SECURITY.md) for report channels and implemented controls. Verified CI evidence is in [PROJECT_STATUS_REPORT.md](PROJECT_STATUS_REPORT.md). Branding sources, launcher art and the GitHub social preview (`assets/branding/github-social-preview.png`) are in `assets/branding/`; generated raster previews are also in `docs/`.

C0reStream is licensed under the Apache License 2.0 (`LICENSE`). Full direct and transitive dependency notices are in `NOTICE.md` and `THIRD_PARTY_NOTICES.md`. No IPTV content, subscriptions or bundled streams are included.
