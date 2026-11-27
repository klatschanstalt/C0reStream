# C0reStream – roadmap and acceptance

Verified status 2026-11-27: v1.4.0, CI runs `45945401657` and `45946882051` green (63 unit tests, 10 instrumented tests per API level 30/36, zero lint errors/warnings, signed APK with verified fingerprint).

Delivered:

- Stable toolchain (JDK 17, AGP 9.5.3, Gradle 9.9.2, Compose, Media3, Room 2.8.5, WorkManager, DataStore) and version catalog.
- Material 3 responsive UI, Light/Dark/AMOLED, German/English/French, Android TV launcher entry and Compose focus navigation.
- Media3 live playback with mini-player, background service, PiP, track/quality selection, sleep timer, retry, catch-up and resume.
- M3U/M3U8 and Xtream sources, stable channel IDs, deduplication, groups, favourites, local import and transactional refresh.
- XMLTV EPG (plain/gzip), guide cache, reminders and catch-up links; movies, series, episodes, watchlist and resume.
- Encrypted credentials/URLs, Keystore-backed Room v5 migration, hardened parsers, per-hop redirect guard, encrypted backup/restore, signed update verification.
- Signed resource manifests, PackageInstaller updates, profiles with PIN, QR import, authorized recording, Apache-2.0 license and notices.

Open or not certified:

- Physical-device validation beyond the API 30/36 emulator matrix; Android 17 and 18 are not device-certified.
- Casting needs a registered receiver ID and Play Services certification; multiview is not implemented; no DRM circumvention.
- APK size 6,027,029 B exceeds the <5 MB goal; measured justification in `PROJECT_STATUS_REPORT.md`.

Binding decisions: minimum Android 11 (API 30); HTTP and HTTPS IPTV sources supported without mandatory opt-in, HTTPS preferred, no HTTPS-to-HTTP redirects; no ads, tracking or bundled streams.

## Prioritätsauftrag (2026-11-27): Import, Player, Performance

Verbindlich, ohne Verlust bestehender Funktionen und Nutzerdaten:

1. Senderimport vollständig und transparent: M3U/M3U8/Xtream, Encoding/BOM/gzip, Limits, Gruppierung, Reihenfolge, Sendernummern, tvg-id, Streamvarianten und identische URLs. Unterschiedliche Sender darf Dedup nicht zusammenführen; Zähler für empfangen/importiert/zusammengeführt/fehlerhaft; stabile IDs und Datenmigration erhalten.
2. Immersives Vollbild mit Modi Auto/Original/Fit/Fill/Zoom/Stretch/16:9/4:3/21:9, korrekter Skalierung, Insets, Rotation, Pixel- und Display-Seitenverhältnis; globale und senderspezifische Einstellungen.
3. Media3-Modernisierung: stabile APIs, Hardwaredecoder mit Fallback, HLS/DASH/MPEG-TS/progressive Medien, Track- und Audioauswahl, Puffer, Lifecycle und Diagnose; keine unerfüllten Codec-/Dolby-Versprechen.
4. Kategorisiertes Einstellungscenter mit Expertenmodus, Reset, DE/EN/FR und verständlichen Hilfetexten.
5. Performanzprofile (Automatik/Performance/Maximale Qualität/Low Latency/Stabile Wiedergabe/Energiesparen), schnelle Senderwechsel, Logos-Cache, geringer RAM/CPU/Akkuverbrauch, Diagnose ohne Zugangsdaten.
6. Regressionstests für große Playlists, Doppel-URLs mit verschiedenen Sendern, Gruppierung, Reihenfolge, Importgrenzen, Refresh, Bildformate, Vollbild, Rotation, Audio-/Decoder-Fallback und persistente Einstellungen.

CI und Veröffentlichung bleiben während der Implementierung pausiert. Nach gebündelter Verifikation wird eine signierte Nachfolgeversion ohne Pre-Releases veröffentlicht; die aktuelle öffentliche APK bleibt bis dahin verfügbar.
