# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-28 (produktive HTTPS-Distribution auf dem eigenen Server; keine neue APK-Version).

## Ergebnis

Die veröffentlichte v1.7.2-APK wird unverändert direkt von `stream.c0re.pw` ausgeliefert. `update.json` auf GitHub und auf dem eigenen Server verweisen auf dieselbe, vollständig geprüfte Datei. Die bestehende Webseite ist unverändert, der Verteilungsprozess ist automatisiert und idempotent. Es wurde **keine** neue APK gebaut, kein Tag und kein Release-Artefakt überschrieben; `version.properties` bleibt `1.7.2` / `versionCode 10`.

## Ursachen der gemeldeten Update-Blockade (quellenfest an den Tags)

- **Ressourcenpfad:** `ResourceManager.kt:22–25` enthält in v1.7.0/v1.7.1/v1.7.2 hartcodierte GitHub-URLs (`releases/latest/download/resources.json|sig`). v1.7.0/v1.7.1 nutzen den strengen Redirect-Guard; GitHubs 302 zu `release-assets.githubusercontent.com` trägt `sig`/`jwt`, daher genau `Credential-bearing cross-origin redirect blocked` (`NetworkPolicy.kt:271–279`). Der Operator-Log gehört zu diesem Pfad. `apkUrl` berührt ihn nicht. Nur v1.7.2 erlaubt den Hop über `releaseAssetClient()`.
- **APK-Pfad mit GitHub-Release-URL:** v1.7.0/v1.7.1 nutzten `downloadClient()`; derselbe Hop blockierte vor SHA-256-, Paketnamen-, Versions- und Zertifikatsprüfung.
- **Warum GitHub Raw nicht half:** der Raw-Pfad entfernte den 302-Hop und besteht alle Download- und Integritätsprüfungen (live 200, 0 Redirects; Bytes `0a466930…c512e`). Es blieben zwei quellenfest nachweisbare, unveränderliche Client-Grenzen:
  1. v1.7.0/v1.7.1 behandeln `STATUS_PENDING_USER_ACTION` als Fehler (`UpdateInstallReceiver.kt:18–34` im Tag v1.7.1); die Meldung „Installation fehlgeschlagen“ kann erscheinen, während das System noch auf die Bestätigung wartet. v1.7.2 korrigiert das.
  2. Ohne „Install unknown apps“-Berechtigung zeigt v1.7.1 nur einen generischen Fehler; die Berechtigung wird vor der Übergabe nicht geprüft.
  Welche Stufe das Betreibergerät tatsächlich erreichte, ist **offen** — es liegt kein stufenbezogenes Gerätelog vor.
- **v1.7.2-Gegenrichtung:** `releaseAssetClient()` verlangt einen Initialrequest unter `github.com/klatschanstalt/C0reStream/releases/` (`NetworkPolicy.kt:330–332, 358–371`). Die eigene Server-URL wird deshalb abgelehnt. Ein einzelner `apkUrl` kann v1.7.0/v1.7.1 und v1.7.2 nicht gleichzeitig bedienen. Da v1.7.2 die aktuelle Version ist und kein Update benötigt, bleibt die Auswirkung auf künftige Releases als Risiko dokumentiert; keine Prüfung wurde deaktiviert.

## Verifikation der veröffentlichten Artefakte

| Tag | APK-SHA-256 | Bytes | Badging | Zertifikat |
| --- | --- | --- | --- | --- |
| v1.7.0 | `2b2a6ad9…509dcf` | 6.634.709 | `pw.c0re.stream`, Code 8, 1.7.0, min 30/target 37 | `65FB26B0…E8543` |
| v1.7.1 | `82444a5e…67f49` | 6.600.726 | `pw.c0re.stream`, Code 9, 1.7.1, min 30/target 37 | `65FB26B0…E8543` |
| v1.7.2 | `0a466930…c512e` | 6.661.994 | `pw.c0re.stream`, Code 10, 1.7.2, min 30/target 37 | `65FB26B0…E8543` |

- Alle `SHA256SUMS` stimmen; `apksigner verify --print-certs` meldet keine Fehler, alle drei nutzen dasselbe Produktionszertifikat. v1.7.2 ist v2-signiert; `apksigner` ist hier das maßgebliche Werkzeug (`keytool -jarfile` meldet „keine signierte JAR“).
- `resources.json` (151 Byte, `configVersion 1`, leere `entries`) und `resources.sig` (1024 Hex-Zeichen) wurden mit `openssl dgst -sha256 -verify` gegen das aus der APK extrahierte Zertifikat geprüft: `Verified OK`. SPKI-SHA-256 `4b6a3c36…8993c1`, Zertifikats-SHA-256 `65FB26B0…E8543`.
- `update.json` (`main` und Own-Server) nennt 1.7.2 und `0a466930…c512e`; `notes` (Pflicht für v1.7.0/v1.7.1) und `notes_de/en/fr` sind vollständig. Der veraltete Prüfsummenwert im Tag-Commit v1.7.2 betrifft nur den Tag; Clients lesen `main`.

## Eigene HTTPS-Distribution

| Endpunkt | Status | Redirects | MIME | Cache |
| --- | --- | --- | --- | --- |
| `/distribution/update.json` | 200 | 0 | `application/json; charset=utf-8` | `no-cache, must-revalidate` |
| `/distribution/releases/v1.7.2/C0reStream.apk` | 200 | 0 | `application/vnd.android.package-archive` | `public, max-age=31536000, immutable` |
| `/distribution/releases/v1.7.2/SHA256SUMS` | 200 | 0 | `text/plain; charset=utf-8` | `public, max-age=31536000, immutable` |
| `/distribution/resources/resources.json` | 200 | 0 | `application/json; charset=utf-8` | `no-cache, must-revalidate` |
| `/distribution/resources/resources.sig` | 200 | 0 | `text/plain; charset=utf-8` | `no-cache, must-revalidate` |

- Ein isolierter `handle /distribution/*`-Block in `/etc/caddy/stream.c0re.pw.caddy` liefert direkt aus `/var/lib/c0re-distribution/current` — ohne Redirect, Authentifizierung, Token oder Abhängigkeit vom Webroot-Symlink. Die Konfiguration ist vor Änderungen gesichert, `caddy validate` war grün vor dem Reload und die Quelle liegt identisch in `ops/caddy/stream.c0re.pw.Caddyfile`.
- Range `bytes=0-15` → `206`, `content-range: bytes 0-15/6661994`, 16 Bytes. `/distribution/`, `/distribution/releases/`, `/distribution/resources/` und fehlende Dateien liefern eine echte `404` ohne Verzeichnisauflistung und ohne Website-`index.html`-Fallback.
- Alle ausgelieferten Bytes sind SHA-256-identisch mit den Kanonikateien. HSTS, CSP und `nosniff` bleiben aktiv. Keine Offenlegung von Quellcode, Secrets oder Serverkonfiguration.
- **Webseite unverändert:** `index.html`, `style.css`, `main.js` haben vor und nach dem Reload identische SHA-256-Werte (`1afdc939…ccde0e0`, `06b18ed4…bf3686a`, `178a52fb…d931`), `/` und die Assets liefern 200 mit unveränderten Headern. `https://ham3l.cloud/` blieb mit 200/17032 Bytes unverändert. Keine DNS-Änderung, keine neue Subdomain, kein zweiter Siteblock, keine Berechtigungserweiterung.
- Kanonische Daten liegen außerhalb des Webroots (`/var/lib/c0re-distribution/`); Staging, Lock, State und Protokolle ebenfalls. Ein Webseiten-Deployment kann die Distribution nicht löschen. Der Webroot-Symlink `/var/www/stream.c0re.pw/distribution` zeigt auf den kanonischen Baum.

## Automatisierte Distribution

- `ops/distribution/{distribute.sh,c0re-distribution.service,c0re-distribution.timer}` läuft als `ham3l` (`NoNewPrivileges=true`, `ProtectSystem=full`), ohne Token und ohne URL-/Signatur-Parameter im Journal. Timer: `OnBootSec=5min`, `OnUnitActiveSec=30min`, `Persistent=true`.
- Ablauf: öffentliche GitHub-API → numerische Versions- und `versionCode`-Prüfung → Download nur der vier signierten Assets nach Staging → SHA-256 aus `SHA256SUMS`, Zertifikatsfingerprint, Paketname, `versionName`/`versionCode`, Manifest-Signatur → atomare Installation und Symlink-Umschaltung unter `flock`, Rollback bei Fehler → HTTPS-Prüfung (200, 0 Redirects, exakte Bytes) → atomare Aktivierung von `update.json` → erneute öffentliche Prüfung → Sync der GitHub-Metadaten aus einem isolierten Worktree ohne Force-Push → kompakte, geheimnisfreie Statusdatei.
- Idempotent: ein erneuter Lauf ohne neuere stabile Release ändert nichts. Verhindert parallele Läufe, Downgrades, identische Versionen und Überschreiben vorhandener Versionsverzeichnisse. Bei Fehlern bleibt die letzte funktionierende Distribution aktiv.
- Real ausgeführt wurde der Pfad „keine neue Release“: `status=ok`, `action=none`, `detail=no-new-release`, `active=1.7.2/10`, Timer aktiv. Der Installations-/Rollback-Pfad ist statisch geprüft (`bash -n`) und logisch nachgezogen, aber **nicht** mit einer echten neuen Release ausgeführt — dafür wurde bewusst kein Test-Tag und kein Artefakt erzeugt.

## Release-Pipeline

- Die Produktions-APK wird genau **einmal** gebaut und signiert. `SHA256SUMS` und die in `update.json` verwendete Prüfsumme stammen aus diesem Artefakt; nach der Prüfsummenermittlung gibt es keinen Gradle-Aufruf mehr. Dasselbe Artefakt wird auf GitHub veröffentlicht.
- Nach der Veröffentlichung werden der öffentliche GitHub-Re-Download (Bytes, `SHA256SUMS`, Zertifikat, Paketname, `versionCode`, `versionName`) und der Own-Server-APK-Endpoint (exakte Bytes) geprüft. Erst dann wird `update.json` mit einem normalen `[skip ci]`-Commit aktiviert. Kein zweiter Build, keine manuelle Prüfsummenkorrektur, kein Mirror im Repository.
- `[skip ci]`-Guard auf allen Jobs verhindert Release-Schleifen aus Metadaten-Commits. Tag-Guard, PR-verify-only und nicht veröffentlichender `workflow_dispatch` bleiben erhalten. Gradle Dependency Verification bleibt intakt.
- Die redundante Kopie `downloads/v1.7.2/C0reStream.apk` wurde erst nach `cmp`/SHA-Nachweis gegen den öffentlichen Endpoint entfernt; `.gitignore` schließt APKs, Staging und temporäre Downloads aus. Keine Git-Historie umgeschrieben.

## Verifikation

- Hermetisch ausgeführt: `255` Unit-Tests, `0` Fehler, `0` Exceptions, `1` bewusst übersprungen; `:app:lintDebug` 0 Issues. Enthalten sind 8 neue Tests in `DistributionDeliveryTest.kt` (direkte HTTPS-URL ohne Redirect, HTTPS→HTTP-Block, cross-origin `sig`/`jwt`/`username`/`password`-Block, `update.json`-Felder, numerische Versionierung, Manifest-Signatur mit Produktionszertifikat) sowie der Test, der die Own-Server-URL für das v1.7.2-Release-Asset-Profil nachweislich ablehnt.
- Kryptografie extern geprüft: `sha256sum -c SHA256SUMS` → `OK`; `openssl dgst -sha256 -verify … resources.json` → `Verified OK`; Zertifikatsfingerprint `65FB26B0…E8543`.
- Vorherige, unverändert gültige CI-Läufe: `46102219126` (PR: Verify + instrumentierte Tests API 30/36 grün), `46103012176` (main: grün, Publish durch bestehenden Tag korrekt übersprungen), `46096463279` (voller Release-Lauf grün; 29 instrumentierte Tests je API 30/36).
- Ein physisches Gerät oder ein lokaler Emulator stand nicht zur Verfügung; die Systeminstallation und der Erhalt von Nutzerdaten konnten hier nicht ausgeführt werden.

## Weiterhin unvollständig / nicht verifiziert (bewusst offen)

Der Betreiberbefund gilt **nicht** als erledigt, solange die praktische Geräteabnahme fehlt. Die vollständige Liste steht in `MASTERPLAN.md`.

| Punkt | Status | Grund |
| --- | --- | --- |
| Praktische Update-Abnahme auf physischem Gerät (v1.7.0/v1.7.1 → v1.7.2) | **offen / unbestätigt** | Metadaten, Bytes, SHA-256, Paketname, versionCode und Zertifikat geprüft; Installation, Versionsanzeige und Nutzerdaten nicht auf Gerät bestätigt. |
| Installationsrückmeldung v1.7.0/v1.7.1 (`PENDING_USER_ACTION`) | **offen / technische Grenze** | Quellenfest nachgewiesen, ohne neue APK nicht behebbar; Geräte-Code nicht verifiziert. |
| Ressourcen-Updates v1.7.0/v1.7.1 auf eigenen Server | **offen / technische Grenze** | URLs hartcodiert, `baseUrl` signiert; ohne neue APK nicht umstellbar. Veröffentlichtes leeres Manifest bleibt gültig. |
| v1.7.2-Kompatibilität bei künftiger APK-Version | **offen / Risiko** | v1.7.2 akzeptiert nur GitHub-Release-URLs, alte Clients nur credential-freie URLs; kein einzelner `apkUrl` bedient beide. |
| Installations-/Rollback-Pfad des Verteilers mit echter neuer Release | **offen / nicht ausgeführt** | Nur der „keine neue Release“-Pfad lief real; kein Test-Tag, keine Artefaktüberschreibung. |
| In-App-Update bis Installationsabschluss | **offen / unvollständig** | Download/Integrität/Signatur/Übergabe instrumentiert, Systeminstallation nicht automatisierbar. |
| Feindiagnostik UPD-001…UPD-014 in der App | **offen** | Würde neue APK erfordern; in `SECURITY.md` dokumentiert, v1.7.2 zeigt grobe Stufenmeldungen. |
| PIN-Gate und Picture-in-Picture | **offen / unvollständig** | App-seitiges Pausieren geprüft; PiP-Verhalten auf Gerät ungeprüft. |
| Externe Playlist-Übergabe, Backup-UI, visuelle Schrift-/Kontrast-/TV-Prüfung | **offen / unvollständig** | Nur instrumentiert bzw. automatisiert geprüft. |
| API-37-Emulator, Android 17/18, Langzeit- und Messwertprüfung | **offen / unvollständig** | Keine verlässliche Matrix und keine Gerätebasis. |
| Abhängigkeitsverifikation nach Updates | **offen / bei Bedarf** | `gradle/verification-metadata.xml` bei künftigen Dependency-Änderungen regenerieren. |
| FFmpeg, Casting, Stalker/MAG, UDP/Multicast, Timeshift, Multiview, RTMP/SRT, DRM | **offen / außerhalb dieses Releases** | Keine vertrauenswürdige Quelle, Autorisierung oder Gerätematrix; keine DRM-Umgehung. |
