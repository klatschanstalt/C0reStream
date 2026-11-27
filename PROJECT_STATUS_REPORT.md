# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

CI-Lauf `46011852896` (Commit `733ee83`, Push-Trigger aktiv) vollständig grün:

- 91 Unit-Tests ohne Fehler; `:app:lintDebug` mit 0 Fehlern und 0 Warnungen.
- 10 instrumentierte Tests je API 30 und API 36 ohne Fehler; Release-Build mit R8, `signResourceManifest` und signierte Veröffentlichung erfolgreich.

## Produktionsrelease

- [C0reStream v1.5.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.5.0), [direkte APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 6, versionName 1.5.0, minSdk 30, targetSdk 37.
- 6.200.405 Bytes; SHA-256 `f34f18e63880add27e9baeab8ec79ff07d129ab7299a74bc843698283f78f0ad`.
- Zertifikat-Fingerabdruck `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`, RSA 4096, APK-Signatur v2. APK ohne Authentifizierung geladen; `sha256sum --check` und `apksigner` bestanden.
- `update.json`, `resources.json` und `resources.sig` öffentlich per HTTPS erreichbar; signierte Ressourcen und APK-Updates benötigen keinen GitHub-Token. v1.3.0 und v1.4.0 bleiben verfügbar; keine Pre-Releases.

## Umgesetzt und geprüft

- Senderimport: M3U/M3U8/Xtream mit BOM/CRLF/gzip, Sendernummern, tvg-id/tvg-name, Gruppen und Streamvarianten; stabile IDs über Tokenwechsel; keine Zusammenführung unterschiedlicher Sender bei identischer URL; Anzeige von empfangen/importiert/zusammengeführt/abgelehnt samt Grundcodes; Shrink-Guard und transaktionale Aktualisierung.
- Room v6 speichert Anbieterreihenfolge sowie tvg-Name, Sendernummer und Qualität; bestehende Favoriten, Verlauf und IDs bleiben erhalten.
- Vollbild: Auto/Original/Fit/Fill/Zoom/Stretch/16:9/4:3/21:9, Pixel-Seitenverhältnis und immersive Insets; globale und senderspezifische Einstellungen wirken über das Einstellungscenter.
- Media3: Hardwaredecoder mit Fallback, profilabhängiges Buffering, Audiofokus, A/V-Offset, echte Codec-/Decoder-Diagnose ohne Zugangsdaten; begrenzter Logo-Cache für große Listen.

## Nicht zertifiziert

- Physische Geräte jenseits der API-30/36-Emulatoren; Android 17 und 18 nicht gerätezertifiziert.
- Casting benötigt registrierte Receiver-ID/Play-Services-Zertifizierung; Multiview nicht implementiert; keine DRM-Umgehung.
- Echte Geräte-Screenshots und extern generierte Grafiken nicht verfügbar; Branding liegt unter `docs/`.
- APK-Größe 6.200.405 B über dem <5-MB-Ziel; gemessene Begründung im vorherigen Statusbericht und MASTERPLAN unverändert gültig.
