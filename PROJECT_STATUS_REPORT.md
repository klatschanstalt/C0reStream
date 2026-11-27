# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

CI-Läufe `46035485381` (Integration) und `46037771079` (Release, Push-Trigger) vollständig grün:

- 123 Unit-Tests ohne Fehler; `:app:lintDebug` mit 0 Fehlern und 0 Warnungen.
- 11 instrumentierte Tests je API 30 und API 36 ohne Fehler (inklusive 6.000-Sender-Paging-Test).
- Release-Build mit R8, `signResourceManifest`, `apksigner`- und SHA-256-Prüfung erfolgreich.

## Produktionsrelease

- [C0reStream v1.6.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.6.0), [direkte APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 7, versionName 1.6.0, minSdk 30, targetSdk 37.
- 6.552.789 Bytes; SHA-256 `4787f7ff5766b8db548aabbff527f710167dd1ba28b06767971f0e38c51a97ee`.
- Signatur v2, RSA 4096, Fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`. APK ohne Authentifizierung geladen; `sha256sum --check` und `apksigner` erfolgreich.
- `update.json` verweist versionsgebunden auf v1.6.0 und dieselbe Prüfsumme; versionCode 7 > 6 (v1.5.0), identisches Signaturzertifikat, HTTPS-URL ohne Token. Öffentliche URLs (Repository, Release, APK, SHA256SUMS, Ressourcenmanifest und -signatur) liefern HTTP 200.
- v1.3.0, v1.4.0 und v1.5.0 bleiben verfügbar; keine Pre-Releases.

## Behobene P0-/P1-Fehler

- Senderverlust durch `LIMIT 500`/`LIMIT 5000` und In-Memory-Filter: Room-Paging ohne Ergebnisobergrenzen, kombinierte Gruppen-/Textfilter in SQLite, `orderIndex`-Reihenfolge und stabile IDs erhalten; wahre Trefferzahl separat per `COUNT(*)`.
- 90-Sekunden-Streamabbruch: getrennte OkHttp-Profile; Wiedergabe/Aufnahme ohne absoluten `callTimeout` (nur Connect/Read begrenzt), gemeinsamer Connection-Pool und Cache für Clients.
- Redirect-Sicherheit: Origin-Wechsel inklusive Port, sensible Header/Query-Parameter und Response-Schließung bei ungültigen Zielen; HTTPS→HTTP weiterhin blockiert, HTTP-Quellen erlaubt.
- Keystore: klassifizierte Fehler statt Exception beim Lesen, kein stummes Neuerstellen eines Schlüssels, Platzhalter mit Erhalt der ursprünglichen Ciphertext-Bytes; keine automatische Löschung von Daten.
- Player: FILL und ZOOM nicht mehr identisch, per-Kanal-Einstellungen konsistent, echte Aufnahme-Parameter der Quelle, Diagnostik mit Farb-/ Größeninformationen.

## APK-Größe

v1.5.0: 6.200.405 Bytes; signierter Design-Verifikationsbuild 6.434.349 Bytes; v1.6.0: 6.552.789 Bytes (+118.440 Bytes durch Paging 3). Das <5-MB-Ziel bleibt nicht erreicht. Weitere Performancekennzahlen (Startzeit, Senderwechsel, RAM/CPU) wurden nicht gemessen.

## Nicht verifiziert / offen

- Kein kontinuierlicher realer Stream >90 Sekunden in kontrollierter Umgebung durchgeführt; der fehlende `callTimeout` ist per Unit-Test belegt.
- Keine Gerätetests jenseits der API-30/36-Emulatoren; Android 17/18 nicht zertifiziert.
- Keystore-Fehlerpfade benötigen instrumentierte Gerätetests; Visuelles zu themed Icons/Cutouts/Foldables/TV weiterhin nicht geräteverifiziert.
- RTSP/Multicast, Casting, Multiview, Timeshift und Stalker-Portale sind nicht implementiert (Plattform-, Zertifizierungs- bzw. Ressourcengrenzen); keine DRM-Umgehung.
