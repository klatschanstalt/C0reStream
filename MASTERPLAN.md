# C0reStream – roadmap and acceptance

Verified status 2026-11-27. CI run `45934346757`: 47 unit tests, 9 instrumented tests per API level (30 and 36), zero lint errors/warnings, signed release published with a verified certificate fingerprint.

Delivered:

- Stable toolchain (JDK 17, AGP 9.5.3, Gradle 9.9.2, Compose, Media3, Room 2.8.5, WorkManager, DataStore) and version catalog.
- Material 3 responsive UI, Light/Dark/AMOLED, German/English/French, Android TV launcher entry and Compose focus navigation.
- Media3 live playback with mini-player, background service, PiP, track/quality selection, sleep timer, retry, catch-up and resume.
- M3U/M3U8 and Xtream sources, stable channel IDs, deduplication, groups, favourites, local import and transactional refresh.
- XMLTV EPG (plain/gzip), guide cache, reminders and catch-up links; movies, series, episodes, watchlist and resume.
- Encrypted credentials/URLs, Keystore-backed Room v4 migration, hardened parsers, per-hop redirect guard, encrypted backup/restore, signed update verification.

Open or not certified:

- Physical-device validation beyond the API 30/36 emulator matrix; Android 17 and 18 compatibility are not certified.
- Optional features not implemented: casting, multiview, authorized recording, profiles, PIN and QR import.

Binding decisions: minimum Android 11 (API 30); HTTP and HTTPS IPTV sources supported without mandatory opt-in, HTTPS preferred, no HTTPS-to-HTTP redirects; no ads, tracking or bundled streams.
