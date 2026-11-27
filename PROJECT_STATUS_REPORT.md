# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

- CI-Lauf `45934346757` (Commit `c37eaca`): 47 Unit-Tests, 9 instrumentierte Tests je API 30 und API 36, `:app:lintDebug` mit 0 Fehlern und 0 Warnungen, Release-Build mit R8 erfolgreich.
- CI-Lauf `45936785169`: `verify` und beide instrumentierten Jobs erneut grün; `publish-production` wurde nach der Verifikation bewusst abgebrochen, um dieselbe Version nicht erneut zu veröffentlichen.
- Nachfolgende Commits betreffen nur Dokumentation und Workflow-Konfiguration (`[skip ci]`).

## Produktionsrelease

- [C0reStream v1.3.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.3.0), [direkte APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 4, versionName 1.3.0, minSdk 30, targetSdk 37.
- 5.786.466 Bytes; SHA-256 `e3a98be25e6abdd32f5cefc08bf7dfb7379f5ace8da1c5e158d073e0be4b5bad` (mit `sha256sum --check` bestätigt).
- APK-Signatur v2, Zertifikat CN=C0reStream, O=C0re, C=DE, RSA 4096, Fingerabdruck `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543` (mit `apksigner` bestätigt).
- `update.json` verweist versionsgebunden auf dieselbe APK und Prüfsumme; die APK nutzt zum Abruf keinen GitHub-Token und prüft vor der Installation Prüfsumme, Paketname und Signatur.

## Öffentliche Prüfung nach Sichtbarkeitswechsel

Ohne GitHub-Authentifizierung HTTP 200 für Repository-Seite, README, `update.json`, Release-Seite, `SHA256SUMS` und direkten APK-Download. Erneut geladene APK: `sha256sum --check` OK, Signaturfingerabdruck und Paketmetadaten wie oben.

## Sicherheit und Bereinigung

- Gitleaks: 57 Commits, 0 Funde. Scan lokaler Ressourcen und aller 39 öffentlicher Workflow-Logs: 10 Treffer, alle falsch positiv (JDK-Konfiguration bzw. öffentliche `apksigner`-Schlüssel-Digests). Keine historischen Geheimnisse gefunden; keine Rotation und kein Force-Push erforderlich.
- 9 Pre-Releases samt Tags gelöscht; verbleibendes Release: `v1.3.0`.
- 77 nicht mehr benötigte Actions-Artefakte gelöscht; 4 Artefakte des verifizierten Laufs `45934346757` behalten.
- Lokale Build-Verzeichnisse und Caches entfernt; keine Build-Artefakte, APKs oder Schlüssel im Git.

## Nicht zertifiziert / offen

- Physische Geräte jenseits der API-30/36-Emulator Matrix; Android 17 und 18 sind nicht gerätezertifiziert.
- Keine Projektlizenz vom Eigentümer festgelegt; `THIRD_PARTY_NOTICES.md` dokumentiert die Lizenzen der direkten Abhängigkeiten.
