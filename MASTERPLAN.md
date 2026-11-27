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
- APK size 6,200,405 B exceeds the <5 MB goal; measured justification in `PROJECT_STATUS_REPORT.md`.

Binding decisions: minimum Android 11 (API 30); HTTP and HTTPS IPTV sources supported without mandatory opt-in, HTTPS preferred, no HTTPS-to-HTTP redirects; no ads, tracking or bundled streams.

## Abgeschlossener Auftrag (2026-11-27): Design, Branding & Responsive UI/UX

Branding-Assets integriert: adaptive/monochrome Launcher-Icons, Splash-/Fenster-Theme, zentrale Design-Tokens und wiederverwendbare Brand-Komponenten. Alle Screens und die Player-Oberfläche sind darauf umgestellt; Navigation wählt je nach Breite und Höhe Bottom-Bar/Rail/Drawer. Geprüft in CI-Lauf `46022423220` (siehe Statusbericht). Nicht geräteverifiziert: echte Screenshots, physische Geräte, themed-icon alignment.

## Abgeschlossener Prioritätsauftrag (2026-11-27): Import, Player, Performance

Senderimport mit vollständiger Statistik, stabilen IDs und Anbieterreihenfolge (Room v6), Vollbildmodi inklusive Pixel-/Anamorph-Behandlung, modernisierter Media3-Player mit Decoder-Fallback und Diagnose, kategorisiertes Einstellungscenter mit DE/EN/FR und Performanzprofilen. Geprüft in CI-Lauf `46011852896`; Version `1.5.0` ist signiert veröffentlicht. Nicht umgesetzt: Casting/Multiview (Plattform-/Zertifizierungsgrenzen), echte Geräte-Screenshots, Gerätezertifizierung jenseits API 30/36.
