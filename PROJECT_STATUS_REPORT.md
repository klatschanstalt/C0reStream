# PROJECT_STATUS_REPORT

Stand 2026-11-27 – verifizierter Gesamtstand nach Abschluss der Implementierungsphase.

CI-Lauf `45934346757` (Commit `c37eaca`, `main`) vollständig grün:

- `verify`: 47 Unit-Tests erfolgreich, `:app:lintDebug` mit 0 Fehlern und 0 Warnungen, Debug- und Release-Build inklusive R8 erfolgreich.
- `instrumented`: 9 Android-Tests je API 30 und API 36 erfolgreich (0 Fehler), inklusive Room-v4-Migration/DAO-Tests und Refresh-Regressionsschutz.
- `publish-preview`: signaturfreies Debug-Preview mit geprüftem SHA-256 veröffentlicht (bestehende Pre-Releases unverändert).
- `publish-production`: signierte Release-APK mit `ANDROID_*`-Secrets gebaut; `apksigner`-Prüfung und Vergleich des Zertifikats-Fingerabdrucks mit `ANDROID_CERT_SHA256` erfolgreich.

Produktionsfreigabe: [C0reStream v1.3.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.3.0) mit `C0reStream.apk` und `SHA256SUMS`.

- APK-Größe: 5.786.466 Bytes; SHA-256 `e3a98be25e6abdd32f5cefc08bf7dfb7379f5ace8da1c5e158d073e0be4b5bad` (lokal erneut geprüft).
- Zertifikat: CN=C0reStream, O=C0re, C=DE; RSA 4096; SHA-256-Fingerabdruck `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`; APK-Signatur v2.
- `update.json` verweist auf diese APK und denselben SHA-256; `UpdateInstaller` prüft vor der Installation zusätzlich Paketname und Signaturzertifikat.

Android 11 (API 30) und Android 16 (API 36) sind per Emulator getestet; weitere API-Stufen sind kompilierungsverifiziert, aber nicht gerätezertifiziert. Android 18 ist nicht zertifiziert; Vorbereitung erfolgt nur über offizielle APIs und Feature-Erkennung.

Bekannte Einschränkung (verifiziert): Das Repository ist derzeit privat. Unauthentifizierte requests an `raw.githubusercontent.com/klatschanstalt/C0reStream/main/update.json` und an die Release-Assets liefern HTTP 404 (mit Authentifizierung: 200). Die In-App-Prüfung kann Endnutzern daher erst nach einer Owner-Entscheidung zur öffentlichen Bereitstellung des Manifests/der Assets zur Verfügung stehen. Die Sichtbarkeit wurde nicht geändert; die Signatur- und Checksummenprüfung der veröffentlichten APK ist unabhängig davon verifiziert.
