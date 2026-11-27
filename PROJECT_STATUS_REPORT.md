# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

CI-Läufe `46047078119` (Integration) und `46049714666` (Release, Push-Trigger) vollständig grün:

- 150 Unit-Tests ohne Fehler (der 100-Sekunden-Langzeit-Test ist im CI standardmäßig übersprungen).
- 11 instrumentierte Tests je API 30 und API 36 ohne Fehler.
- `:app:lintDebug`: 0 Fehler, 0 Warnungen; Release-Build mit R8, `signResourceManifest`, `apksigner` und SHA-256-Prüfung erfolgreich.
- Langzeit-Test `LiveStreamEnduranceTest` lokal mit `-Dc0re.longstream=true` tatsächlich ausgeführt: 99,436 Sekunden durchgehende Wiedergabe, 0 Fehler. Damit ist der ehemalige 90-Sekunden-Call-Timeout verifiziert behoben.

## Produktionsrelease

- [C0reStream v1.7.0](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.7.0), [direkte APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 8, versionName 1.7.0, minSdk 30, targetSdk 37.
- 6.634.709 Bytes (nur Information, keine feste Größengrenze mehr); SHA-256 `2b2a6ad95f0aded905d9118b7843138643ff3ab6aa7e306faee699691d509dcf`.
- Signatur v2, RSA 4096, Fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`. APK ohne Authentifizierung geladen; `sha256sum --check` und `apksigner` erfolgreich.
- Update von v1.6.0 kompatibel: versionCode monoton (8 > 7), identisches Zertifikat, HTTPS-URL, geprüfte Prüfsumme. Öffentliche URLs (Repository, Release, APK, SHA256SUMS, Ressourcenmanifest und -signatur) liefern HTTP 200. v1.3.0–v1.6.0 bleiben verfügbar; keine Pre-Releases.

## Umgesetzt

- RTSP/RTP-Unicast über Media3 `RtspMediaSource`; `rtspu` wird auf RTSP/TCP abgebildet. Parsing, URL-Validierung, Datenbank, Player und Backups unterstützen RTSP/UDP; HTTP-Sicherheit (Redirect-Guard, HTTPS→HTTP-Block) bleibt unverändert.
- FFmpeg-Extension-Renderer-Modus aktiviert (Systemdecoder bevorzugt, Erweiterung nur Fallback). Das Modul `media3-decoder-ffmpeg` wird nicht öffentlich publiziert; ohne lokal gebaute AAR bleibt der Pfad inert. Keine Codec-Versprechen ohne Nachweis.
- Erweiterte, zugangsdatenfreie Diagnostik: Audio-/Video-MIME, Decoder, Samplerate/Kanäle, Framerate, HDR/Farbraum, Bitrate, Buffer, Frame-Drops; begrenzte Decoder-Fehlerklassifikation ohne Endlosschleifen.
- Die ehemalige APK-Zielgröße <5 MB ist dauerhaft aufgehoben und aus Dokumentation/AGENTS entfernt.

## Nicht verifiziert / offen

- Rohes UDP- und Multicast-Playback (nur validiert), `MulticastLock` und `CHANGE_WIFI_MULTICAST_STATE` nicht integriert; RTSP-Authentifizierung mit immateriellen Zugangsdaten aus Sicherheitsgründen abgelehnt.
- Casting, Multiview, Timeshift/Ringpuffer und Stalker-/MAG-Portale nicht implementiert (Receiver-ID/Zertifizierung, Decoder-Limits, fehlende autorisierte Testzugänge). Keine DRM-Umgehung.
- Keine Gerätetests jenseits API 30/36; Android 17/18 nicht zertifiziert. 5-/30-/60-Minuten-Langzeittests, Netzwerkwechsel und Keystore-Gerätefehler nicht durchgeführt.
- Neben APK-Größe und Langzeit-Stream wurden keine belastbaren Startzeit-, RAM-, CPU- oder Akku-Messungen erhoben.
