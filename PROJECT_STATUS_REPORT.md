# PROJECT_STATUS_REPORT

Stand 2026-11-27 (lokal verifiziert, Implementierungsphase abgeschlossen; Geräte-/CI-Lauf steht aus).

Verifiziert mit JDK 17.0.21.1, Android SDK 37.1, build-tools 37.0.0 und Gradle 9.9.2:

- `:app:testDebugUnitTest` – 42 Tests erfolgreich (ChannelKey, M3U, XMLTV, Catch-up, NetworkPolicy, UpdateChecker, BackupCrypto).
- `:app:lintDebug` – 0 Fehler, 0 Warnungen.
- `:app:assembleDebug` und `:app:assembleRelease` (R8/Resource-Shrinking) erfolgreich.
- Room-Schema v4 mit Migration 3→4 und Legacy-Datei-Umbenennung `ham3l.db` → `c0restream.db`; Schema exportiert unter `app/schemas/`.
- Signierter Produktionsbuild, Emulator-/Gerätetests, CI-Lauf und Update-Veröffentlichung sind noch nicht erfolgt. Es sind keine Produktions-Signaturgeheimnisse hinterlegt; der Release-Build ist daher unsigniert.

Offen bis zur Produktionsfreigabe: instrumentierte Tests auf Android 11–17 (API-30/36-Matrix in CI vorbereitet), tatsächlicher CI-Lauf, unabhängige Sicherheitsprüfung, dauerhafte Schlüsselverwahrung, signierte APK mit SHA-256 und verifizierter Updatepfad. Android 18 ist nicht zertifiziert; Vorbereitung nur über offizielle APIs und Feature-Erkennung.

Keine Produktionsfreigabe. Vorhandene Pre-Releases bleiben unverändert.
