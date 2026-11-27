# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

CI-Lauf `45945401657` (Commit `bda9a97`) vollständig grün:

- 63 Unit-Tests ohne Fehler; `:app:lintDebug` mit 0 Fehlern und 0 Warnungen.
- 10 instrumentierte Tests je API 30 und API 36 ohne Fehler, inklusive Room-v5-Profile.
- Release-Build mit R8, `signResourceManifest` und signierte Veröffentlichung erfolgreich.

## Produktionsrelease

- [C0reStream v1.4.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.4.0), [direkte APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 5, versionName 1.4.0, minSdk 30, targetSdk 37.
- 6.027.029 Bytes; SHA-256 `69048c17214ea307425f98a1cf3b0aca159a9c27f0d2f0134c8c4e115b66a20f`.
- Zertifikat-Fingerabdruck `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`, RSA 4096, APK-Signatur v2. APK ohne Authentifizierung geladen; `sha256sum --check` und `apksigner` erfolgreich.
- Release-Assets `resources.json` und `resources.sig` sind mit demselben Produktionsschlüssel signiert. `update.json` verweist versionsgebunden auf diese APK und denselben SHA-256; die App nutzt keinen GitHub-Token.
- v1.3.0 bleibt unverändert öffentlich verfügbar.

## Umgesetzt und geprüft

- Signierte Ressourcen-Manifeste mit SHA-256, ETag, Range-Fortsetzung, atomarer Aktivierung, Rollback und Offline-Fallback; Bereinigung veralteter/temporärer Dateien.
- Automatische Update-Prüfung beim Start und alle 12 Stunden; Installation über Android `PackageInstaller` mit Systembestätigung; Prüfung von Prüfsumme, Paketname, Signatur und monotoner Version.
- Lokale Profile mit PBKDF2-PIN und Sperre, QR-Playlist-Import, benutzerautorisierte Aufnahme nicht-DRM-Streams.
- Apache License 2.0 mit `LICENSE`, `NOTICE.md` und `THIRD_PARTY_NOTICES.md`.

## APK-Größe – gemessen und begründet

Ziel <5 MB nicht erreicht. Gemessene Werte: verifizierte v1.3.0 5.786.466 Bytes, v1.4.0 6.027.029 Bytes (+240.563 Bytes für die neuen Funktionen). `classes.dex` belegt 5,15 MB, `resources.arsc` 520 KB; Compose (1,38 MB) und Media3 (1,26 MB) dominieren die DEX. Eingesparte, risikoarme Posten: doppelte Public-Suffix-Daten, 32-Bit-x86-Bibliotheken und Versionsmarker (~56 KB). Eine weitere Reduktion um ~0,8 MB würde den Verzicht auf Compose-/Media3-Funktionen oder die neue QR/PIN-Funktionalität erfordern; das wurde zugunsten der geforderten Features nicht vorgenommen.

## Nicht umgesetzt / nicht zertifiziert

- Casting benötigt eine registrierte Chromecast-Receiver-ID und Play-Services-Zertifizierung; Multiview ist auf Zielgeräten nicht validiert. Keine DRM-Umgehung.
- Delta-Updates: kein messbarer Nutzen gegenüber der vollständigen APK belegt; Fallback ist die vollständige signierte APK.
- Physische Geräte jenseits der API-30/36-Emulatoren, Android 17 und 18 sind nicht zertifiziert.
- Echte App-Screenshots und ein generiertes Social Preview konnten nicht erstellt werden (Bildgenerierung nicht verfügbar); lokal erzeugte Branding-Grafiken liegen unter `docs/`.
