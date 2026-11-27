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
- Casting and multiview are not implemented: Chromecast requires a registered receiver application ID and Google Play services certification, and simultaneous multi-player decoding has not been validated on target devices. No DRM circumvention is attempted.
- The APK-size target of <5 MB is not met with the requested feature set; see `PROJECT_STATUS_REPORT.md` for measured sizes and justified trade-offs.

Binding decisions: minimum Android 11 (API 30); HTTP and HTTPS IPTV sources supported without mandatory opt-in, HTTPS preferred, no HTTPS-to-HTTP redirects; no ads, tracking or bundled streams.

## Aktiver Auftrag (2026-11-27): Ultra-Light, Self-Updating & Feature Expansion

Verbindlich, auf Basis der verifizierten v1.3.0: APK auf <5 MB optimieren; nicht ausführbare Ressourcen signiert, versioniert und offline-fähig nachladen; Selbstupdate für Ressourcen und APK mit Delta-Fallback; Speicherbereinigung ohne Nutzerdatenverlust; Casting, Multiview, autorisierte Aufnahmen, Profile, PIN und QR-Import unter Plattformgrenzen prüfen; Material-3-Design, Branding und Grafikaufbereitung; ausschließlich vorhandene `ANDROID_*` Secrets und `ANDROID_CERT_SHA256`; Apache-2.0-Lizenz und vollständige Lizenzhinweise. CI und Veröffentlichung bleiben während der Implementierung pausiert; nach gebündelter Verifikation wird die nächste signierte Version ohne Pre-Releases veröffentlicht. v1.3.0 bleibt bis dahin öffentlich verfügbar.
