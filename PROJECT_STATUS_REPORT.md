# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-28.

## Verifikation

CI-Läufe `46085609132` und `46086472574` vollständig grün (Unit-Tests, Lint, instrumentierte Tests API 30/36, signierter Release):

- 154 Unit-Tests: 0 Fehler. Ein Test (`LiveStreamEnduranceTest`) ist im CI bewusst übersprungen und wird nicht als bestanden gezählt; der 99-Sekunden-Lauf vom 2026-11-27 mit 0 Fehlern bleibt der letzte tatsächliche Nachweis und wurde ohne konkreten Anlass nicht wiederholt.
- 12 instrumentierte Tests je API 30 und API 36: 0 Fehler (zuletzt geprüft in `46086472574`, Artefakt `instrumented-reports-api30/36`).
- `:app:lintDebug`: 0 Fehler, 0 Warnungen; Kotlin-Kompilierung ohne Warnungen. Lokal zusätzlich R8-Release-Build mit Resource Shrinking erfolgreich.
- OSV-Abfrage aller 120 aufgelösten Laufzeit-Artefakte: 0 Meldungen. Gitleaks über das Projekt: 0 Funde. `apksigner`, `sha256sum --check` und die Ressourcenmanifest-Signatur (mit dem Zertifikat aus der APK) erfolgreich geprüft.

## Produktionsrelease

- [C0reStream v1.7.1](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.7.1), [APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 9, versionName 1.7.1, minSdk 30, targetSdk 37, Compile-SDK 37.1.
- 6.600.726 Bytes (nur Information, keine Höchstgrenze); SHA-256 `82444a5e7367e4475035701c42a91264fa0c33ab6a0a2bbd9920e66e9f867f49`.
- Signatur v2, RSA 4096, Fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`. Die APK wurde ohne Authentifizierung geladen; `sha256sum --check`, `apksigner` und die Prüfung von `resources.json` gegen `resources.sig` mit dem APK-Zertifikat waren erfolgreich.
- Update von v1.7.0 kompatibel: versionCode monoton (9 > 8), identisches Zertifikat, HTTPS-URL, geprüfte Prüfsumme. Alle öffentlichen URLs (Repository, Release, APK, SHA256SUMS, Ressourcenmanifest und -signatur) liefern HTTP 200. Bestehende Tags und Artefakte blieben unverändert; keine Pre-Releases. Auch die erneuten CI-Läufe auf unveränderter Version haben das veröffentlichte Release nicht verändert (Artefakt-Zeitstempel unverändert 2026-11-27T21:31:13Z).

## Versions- und Release-Management (verifiziert)

- Die Regeln zu Semantic Versioning `MAJOR.MINOR.PATCH`, Klassifizierung, monotonem `versionCode`, zentraler Versionsverwaltung, Conventional Commits, Release Notes, Unveränderlichkeit, Rückwärtskompatibilität, Hotfix-Strategie und Betreiberfreigabe für MAJOR sind kompakt in `AGENTS.md` verankert. C0reStream bleibt ohne Freigabe in 1.x.x.
- `version.properties` ist die einzige Quelle für `versionName`/`versionCode`; `app/build.gradle.kts` und CI brechen bei fehlenden oder ungültigen Werten kontrolliert ab (negativ getestet). Keine redundanten fest codierten Versionen.
- CI-Validierung vor Veröffentlichung: gültiges SemVer-Format, `update.json`-`latestVersion` und lokalisierte Notes, monoton steigender `versionCode` gegen das letzte veröffentlichte Release, APK-Badging per `aapt` gegen `version.properties`, Tag-Existenz/Unveränderlichkeit und Fingerprint des Produktionszertifikats. Ein erneuter Lauf auf unveränderter Version überspringt die Monotonieprüfung und veröffentlicht kein neues Release; reine Docs-/CI-/Test-Änderungen lösen keine APK-Releases aus.
- `UpdateChecker` vergleicht Versionen numerisch statt lexikografisch; Regressionstests decken `1.9→1.10`, `1.7.9→1.7.10`, `1.10.9→1.11`, `1.99.9→2.0.0`, identische, ältere und ungültige Werte ab. Für Android-Installationsentscheidungen bleibt `versionCode` verbindlich; APK-, SHA-256-, Paketnamen- und Zertifikatsprüfungen bleiben erhalten.
- Release Notes liegen in `update.json` rückwärtskompatibel in DE/EN/FR vor; die App zeigt die Notes in der Gerätesprache mit Fallback auf `notes`. Der Publishing-Schritt erzeugt die GitHub-Release-Notes aus derselben Struktur. Tests für Sprachauswahl und Fallback sind grün.
- Der gehärtete lokale Playlist-Import (SAF `OpenDocument`, explizite MIME-Liste, `ContentResolver`-Streaming, persistierbare Berechtigung, 32-MB-Lesegrenze, Abbruch und Fehlerbehandlung) ist mit einem instrumentierten Regressionstest abgesichert (CI `46075708200`, 12/0 je API). Die Änderung ist committet, aber noch nicht veröffentlicht; eine Veröffentlichung erfordert `1.7.2` / `versionCode 10`.

## Modernisierung (verifiziert)

- Toolchain an offiziellen stabilen Quellen geprüft: Gradle 9.9.3 inklusive `distributionSha256Sum`, AGP 9.5.3, Kotlin 2.4.21, KSP 2.3.14, JDK 17, Build-Tools 37.0.0. Alle anderen Katalogversionen sind bereits aktuell und wurden unverändert übernommen; JDK-/SDK-Major-Upgrades fehlen die offizielle AGP-Validierung.
- Veraltete `versionCode=4` / `versionName=1.3.0`-Fallbacks entfernt; `version.properties` ist die einzige Quelle, fehlende oder ungültige Werte brechen den Build (negativ getestet).
- Bestätigte Sicherheitslücke `GHSA-xxph-c9ww-hj94` in transitivem Guava behoben (Constraint auf 33.8.0-android); anschließend 0 OSV-Meldungen. GitHub Actions auf vollständige Commit-SHAs gepinnt, Berechtigungen minimiert, Artefakt-Aufbewahrung auf 7 Tage begrenzt, doppelter Release-Build und Schema-Upload entfernt, alle Signatur secrets plus Zertifikatsvariable geprüft.
- Ressourcenmanifest wird über die exakt heruntergeladenen Bytes verifiziert (vorher: kanonische Neuserialisierung → sichere Ablehnung jeder Aktualisierung); redundanter Versionsmarker entfernt. Update-Installation läuft außerhalb des Main-Threads mit `finally`-Statusbereinigung; Aufnahme-Stop/Start-Race, EPG-Aktualisierung, QR-Decode auf dem Main-Thread und veraltete Passwort-/Berechtigungs-APIs behoben.
- ProGuard-Beibehaltung der gesamten Datenschicht und Lizenzausschlüsse entfernt; Lizenzdateien liegen in der APK. Nicht mehr benötigte Tochter-Codes, Kommentare und `.kotlin/`-Restmarkierung bereinigt; alle 34 Branding-Grafiken und `docs/`-Vorschauen unverändert erhalten.

## Nicht verifiziert / offen

- Gradle-Dependency-Verification ist aktiviert (`gradle/verification-metadata.xml`, SHA-256, inklusive Buildscript-/Plugin-Klassenpfad; 505 Komponenten); in CI-Lauf `46079956592` erfolgreich. Dependabot-Abhängigkeitsaktualisierungen erfordern einen Regenerationsschritt der Metadaten. Repository-Quellen (Google Maven, Maven Central, Plugin Portal) und Wrapper-Prüfsumme sind geprüft.
- Rohes UDP-/Multicast-Playback, RTSP-Zugangsdaten in URLs, Casting, Multiview, Timeshift, Stalker/MAG und FFmpeg-AAR bleiben offen (Plattform-, Zertifizierungs- oder Vertrauensgrenzen); keine DRM-Umgehung.
- Keine Gerätetests jenseits API 30/36; Android 17/18 nicht zertifiziert. Netzwerkwechsel-, Keystore-Gerätefehler- und längere Langzeitläufe (5/30/60 Minuten) nicht durchgeführt.
- Keine belastbaren Messwerte zu Startzeit, Senderwechsel, RAM, CPU, Akku oder Datenbank; Macrobenchmark/Baseline Profiles wurden mangels konkretem Nutzen nicht eingeführt.
