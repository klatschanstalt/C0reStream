# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-28 (Update-Reparatur: Metadaten/CI, keine neue APK-Version).

## Bestätigte Fehlerursache der gemeldeten Update-Blockade

Der Betreiberbefund wurde gegen die tatsächlich veröffentlichten Tags und Artefakte erneut untersucht. Die zuvor angenommene Ursache (GitHub-Redirect) ist bestätigt, aber **nicht rückwirkend durch die v1.7.2-Codeänderung behoben**:

- **v1.7.0 (`versionCode 8`) und v1.7.1 (`versionCode 9`):** `UpdateInstaller.downloadAndVerify` verwendet `NetworkPolicy.downloadClient()`. Dessen `redirectTarget()` (v1.7.1 `NetworkPolicy.kt:275–277`) wirft bei einem cross-origin Hop mit sensitivem Query-Parameter `Credential-bearing cross-origin redirect blocked`. `sig` steht bereits seit v1.6.0 auf der Sperrliste (Einführung in `97ed550`); `jwt` kam erst in v1.7.2 hinzu.
- Die öffentliche Update-URL `https://github.com/.../releases/download/v1.7.2/C0reStream.apk` liefert **einen** 302-Hop zu `release-assets.githubusercontent.com/...` mit `sig`, `jwt` und Azure-SAS-Parametern. Der blockierende Schritt ist genau dieser Hop, noch vor SHA-256-, Paketnamen-, Versions- und Zertifikatsprüfung.
- **v1.7.2 (`versionCode 10`):** nutzt das eng begrenzte `releaseAssetClient()`. Der Fix existiert ausschließlich in v1.7.2-Code und kann die bereits installierte v1.7.1-APK nicht verändern. Er akzeptiert aber nur Initial-URLs unter `github.com/klatschanstalt/C0reStream/releases/`.
- **Technische Grenze:** Ein einzelner `apkUrl` kann v1.7.0/v1.7.1 (credential-freie URL nötig) und v1.7.2 (nur GitHub-Release-Pfad erlaubt) nicht gleichzeitig bedienen. Da v1.7.2 die aktuelle veröffentlichte Version ist und kein Update benötigt, wurde die Blockade metadata-seitig für die alten Versionen behoben, ohne eine neue APK zu veröffentlichen.
- **Weitere geprüfte Stellen (nicht die Blockade):** `REQUEST_INSTALL_PACKAGES` ist deklariert und wird vor der Übergabe über `canRequestPackageInstalls()` geprüft. Die Installation läuft über `PackageInstaller`-Session mit `openWrite` und `commit(callback)` — ohne `FileProvider` und ohne `content://`-URI; `FileProvider` wird nur für den M3U-Export genutzt. Downloads landen in einer `.part`-Datei und werden erst nach vollständigem, begrenztem Lesen atomar zum Ziel umbenannt; bei Fehlern wird die temporäre Datei gelöscht. `UpdateInstallReceiver` behandelt `STATUS_SUCCESS` als einziges Erfolgszeichen. Diese Mechanismen bleiben unverändert.

## Verifikation der veröffentlichten Artefakte

| Tag | `update.json` bei Tag | SHA-256 der APK | Bytes | Badging | Zertifikat |
| --- | --- | --- | --- | --- | --- |
| v1.7.0 | 1.7.0, `0000…0000` | `2b2a6ad9…509dcf` | 6.634.709 | `pw.c0re.stream`, Code 8, 1.7.0, min 30/target 37 | `65FB26B0…E8543` |
| v1.7.1 | 1.7.1, `0000…0000` | `82444a5e…67f49` | 6.600.726 | `pw.c0re.stream`, Code 9, 1.7.1, min 30/target 37 | `65FB26B0…E8543` |
| v1.7.2 | 1.7.2, `f72b1a76…8dc0` (veraltet) | `0a466930…c512e` | 6.661.994 | `pw.c0re.stream`, Code 10, 1.7.2, min 30/target 37 | `65FB26B0…E8543` |

- Alle drei `SHA256SUMS`-Assets stimmen mit der jeweils heruntergeladenen APK überein; `apksigner verify --print-certs` meldet keine Fehler, alle drei nutzen dasselbe Produktionszertifikat.
- `update.json` auf `main` nennt 1.7.2 und `0a466930…c512e`, identisch mit `SHA256SUMS` und der veröffentlichten APK. Der veraltete Checksum-Wert im Tag v1.7.2 betrifft nur den Tag-Commit; Clients lesen `main`.
- `update.json` selbst ist unter `https://raw.githubusercontent.com/klatschanstalt/C0reStream/main/update.json` mit HTTP 200 und **0** Redirects öffentlich erreichbar.

## Rückwärtskompatibilität wiederhergestellt

- Die exakte, signierte v1.7.2-APK liegt als credential-freie HTTPS-Kopie unter `downloads/v1.7.2/C0reStream.apk` im Repository. `sha256sum` und Bytevergleich gegen die heruntergeladene Release-APK sind identisch (`0a466930…c512e`, 6.661.994 Bytes).
- `update.json` verweist jetzt auf `https://raw.githubusercontent.com/klatschanstalt/C0reStream/main/downloads/v1.7.2/C0reStream.apk`. Dieser Pfad wird ohne Redirect und ohne `sig`/`jwt` ausgeliefert; ein Hermes-Test (`ReleaseAssetRedirectTest`) bestätigt, dass das strikte General-Profil (v1.7.0/v1.7.1) eine solche direkte URL akzeptiert.
- Sämtliche bestehenden Prüfungen bleiben aktiv: SHA-256, Paketname `pw.c0re.stream`, versionCode 10 > 9 bzw. > 8 und identisches Signaturzertifikat. Keine Deinstallation, keine Datenmigration, kein HTTP-Download, kein neues Zertifikat, keine Änderung an Tags oder veröffentlichten Artefakten.
- **Nicht auf Gerät bestätigt:** der eigentliche Installationsabschluss und der Erhalt vorhandener Nutzerdaten benötigen ein physisches Gerät. Der Betreiberbefund bleibt daher **offen**, bis die praktische Abnahme erfolgreich gemeldet wurde.

## Update-Pfade (tatsächlich geprüft)

- **v1.7.0 → v1.7.2 und v1.7.1 → v1.7.2:** Metadaten, Redirect-Kette, APK-Bytes, SHA-256, Paketname, versionCode und Zertifikat wurden anhand der veröffentlichten Artefakte geprüft; der blockierende 302-Hop ist durch die credential-freie Spiegel-URL eliminiert. Die Systeminstallation selbst ist hier nicht automatisierbar (kein Emulator, kein Gerät) und bleibt offen.
- **v1.7.2 → nächste Version:** in diesem Auftrag nicht anwendbar, weil bewusst keine neue APK veröffentlicht wird (Begründung oben). Ein künftiger Release darf die Spiegel-URL nicht unverändert übernehmen, ohne vorher zu klären, wie v1.7.2-Clients sie akzeptieren; das ist als offenes Risiko erfasst.

## Automatisierte Prüfsummenermittlung

- `release-notes.json` enthält die DE/EN/FR-Notes der vorbereiteten Version. `update.json` beschreibt weiterhin die letzte **veröffentlichte** Version, bis das Artefakt geprüft ist.
- Der Release-Job baut und signiert genau **eine** APK, ermittelt daraus `SHA256SUMS` und die Prüfsumme, veröffentlicht dieselben Bytes, lädt die öffentliche APK erneut herunter und verifiziert Prüfsumme, `SHA256SUMS`, Zertifikatsfingerprint, Paketnamen und Version. Erst danach erzeugt er `update.json` und die credential-freie Spiegelkopie und committet beides als normalen `[skip ci]`-Commit.
- Es gibt keinen zweiten Build nach der Prüfsummenermittlung. Tags und Release-Artefakte bleiben unverändert; ein bereits existierender Tag führt zu `published=false`, ohne Veröffentlichung oder Metadaten-Commit. Manueller Dispatch baut und lädt hoch, veröffentlicht aber nicht. PRs führen nur Verifikation aus.
- Bitidentische Reproduzierbarkeit ist nicht erforderlich: AGP bettet die Git-Revision in `META-INF/version-control-info.textproto` ein; ein Metadaten-Commit ändert `pg-map-id` und APK-SHA. Der neue Ablauf bricht die Schleife verfahrensmäßig, nicht durch Byte-Gleichheit.

## Weitere verifizierte Befunde (unverändert aus der vorherigen Abnahme)

- CI-Lauf `46096463279` grün; 246 Unit-Tests (0 Fehler, 1 bewusst übersprungen), 29 instrumentierte Tests je API 30/36 (0 Fehler), Lint 0/0, signierter R8-Release, `apksigner`, `sha256sum --check`, Manifest-Signatur.
- PIN-Verwaltung verifiziert die alte PIN in der Geschäftslogik; `ProfileGate` bleibt bis zur Authentifizierung fail-closed; sensible Query-Namen (inkl. Xtream-Pfade) und Header werden über alle Hops geschützt; lokaler Import, Backup-Teilwiederherstellung, Player-Retry, XMLTV-Offsets, leere signierte Kataloge, Kontrast, `ACTION_SEND` und History-Leerzustand sind mit Regressionstests abgedeckt.
- README (DE/EN/FR), `SECURITY.md` (Diagnose-Codes UPD-001…UPD-014 und sicherer manueller Updatepfad), `AGENTS.md`, `MASTERPLAN.md` und `PROJECT_STATUS_REPORT.md` wurden synchron aktualisiert.

## Weiterhin unvollständig / nicht verifiziert (bewusst offen)

Der Betreiberbefund gilt **nicht** als erledigt, solange die praktische Geräteabnahme fehlt. Die vollständige Liste steht in `MASTERPLAN.md`.

| Punkt | Status | Grund |
| --- | --- | --- |
| Praktische Update-Abnahme auf physischem Gerät (v1.7.0/v1.7.1 → v1.7.2) | **offen / unbestätigt** | Metadaten und Artefakte geprüft, Spiegel-URL credential-frei; Installation, Versionsanzeige und Nutzerdaten nicht auf Gerät bestätigt. |
| In-App-Update bis Installationsabschluss | **offen / unvollständig** | Download/Integrität/Signatur/Übergabe instrumentiert, Systeminstallation nicht automatisierbar. |
| v1.7.2-Kompatibilität bei künftiger APK-Version | **offen / Risiko** | v1.7.2 akzeptiert nur GitHub-Release-URLs, v1.7.0/v1.7.1 nur credential-freie URLs; ein künftiger Release braucht eine clientseitige Lösung oder gestaffelte Metadaten. |
| Feindiagnostik UPD-001…UPD-014 in der App | **offen** | Code- und Meldungsänderungen würden eine neue APK erfordern; in diesem Auftrag nicht veröffentlicht. Taxonomie und Meldungen sind in `SECURITY.md` dokumentiert; v1.7.2 zeigt bereits grobe Stufenmeldungen. |
| PIN-Gate und Picture-in-Picture | **offen / unvollständig** | App-seitiges Pausieren geprüft; PiP-Verhalten auf Gerät ungeprüft. |
| Externe Playlist-Übergabe, Backup-UI, visuelle Schrift-/Kontrast-/TV-Prüfung | **offen / unvollständig** | Nur instrumentiert bzw. automatisiert geprüft. |
| API-37-Emulator, Android 17/18, Langzeit- und Messwertprüfung | **offen / unvollständig** | Keine verlässliche Matrix und keine Gerätebasis. |
| Abhängigkeitsverifikation nach Updates | **offen / bei Bedarf** | `gradle/verification-metadata.xml` bei künftigen Dependency-Änderungen regenerieren. |
| FFmpeg, Casting, Stalker/MAG, UDP/Multicast, Timeshift, Multiview, RTMP/SRT, DRM | **offen / außerhalb dieses Releases** | Keine vertrauenswürdige Quelle, Autorisierung oder Gerätematrix; keine DRM-Umgehung. |
