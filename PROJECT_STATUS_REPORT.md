# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-28.

## Verifikation

CI-Lauf `46096463279` vollständig grün (Unit-Tests, Lint, instrumentierte Tests API 30/36, signierter Release und Veröffentlichung). Vorgelaufen grün: `46094094395` (Integration nach Korrektur der instrumentierten Tests), `46095331639` (manueller Build ohne Veröffentlichung). CI-Lauf `46093112939` war rot (zwei instrumentierte Tests) und wurde mit `46094094395` behoben.

- 246 Unit-Tests: 0 Fehler, 0 Exceptions. Ein Test (`LiveStreamEnduranceTest`) ist im CI bewusst übersprungen und wird nicht als bestanden gezählt; der 99-Sekunden-Lauf vom 2026-11-27 mit 0 Fehlern bleibt der letzte tatsächliche Nachweis und wurde ohne konkreten Anlass nicht wiederholt.
- 29 instrumentierte Tests je API 30 und API 36: 0 Fehler (Artefakte `instrumented-reports-api30/36` von `46096463279`). Enthalten sind End-to-end-Tests für lokalen Playlist-Import, PIN-Operationen mit echter Room-Datenbank, Backup-Teilwiederherstellung und APK-Zwischenspeicher.
- `:app:lintDebug`: 0 Fehler, 0 Warnungen; Kotlin-Kompilierung ohne Warnungen. Signierter R8-Release-Build mit Resource Shrinking erfolgreich.
- `apksigner`, `sha256sum --check`, APK-Badging und die Kombination `:app:signResourceManifest :app:verifyResourceManifest` mit dem Produktionszertifikat erfolgreich. OSV/Gitleaks aus dem vorherigen Stand unverändert (0 Meldungen bzw. 0 Funde); keine erneute Toolchain-Modernisierung ohne Anlass.

## Produktionsrelease

- [C0reStream v1.7.2](https://github.com/klatschanstalt/C0reStream/releases/tag/v1.7.2), [APK](https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk), [SHA256SUMS](https://github.com/klatschanstalt/C0reStream/releases/latest/download/SHA256SUMS).
- `pw.c0re.stream`, versionCode 10, versionName 1.7.2, minSdk 30, targetSdk 37, Compile-SDK 37.1. Badging und `version.properties` stimmen überein; `update.json` nennt dieselbe Version und die Prüfsumme der veröffentlichten APK.
- 6.661.994 Bytes (nur Information, keine Höchstgrenze); SHA-256 `0a46693033151ccb0dbe8d55da18f375d0065ff2c484fb45faf6bb1f689c512e`, geprüft gegen die heruntergeladene Release-APK und `SHA256SUMS`.
- Signatur v2, RSA 4096, Fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543` (unverändert gegenüber v1.7.1). Release-Notes werden aus `update.json` in DE/EN/FR erzeugt.
- `resources.json` und `resources.sig` stammen aus dem signierten, geprüften Manifest des Release-Jobs; `resources.json` ist bytegleich mit `resources/manifest.json`.
- Update-Kompatibilität: versionCode monoton (10 > 9), identisches Zertifikat, HTTPS-URL, geprüfte Prüfsumme. Bestehende Releases und Artefakte blieben unverändert: v1.7.1 weiterhin 6.600.726 Bytes, Zeitstempel `2026-11-27T21:31:13Z`; v1.7.2 unverändert seit `2026-11-28T02:20:39Z`. Keine Pre-Releases, kein Force-Push, kein neues Signaturmaterial.
- Besonderheit beim Checksum-Bootstrap: AGP bettet die Git-Revision in `META-INF/version-control-info.textproto` ein; dadurch ändert bereits ein Metadaten-Commit `pg-map-id` und APK-SHA. Die finale `update.json`-Prüfsumme wurde deshalb nach der Veröffentlichung mit `[skip ci]` an die tatsächlich veröffentlichte APK angepasst und gegen diese geprüft. Bei künftigen Releases ist die Prüfsumme aus einem Build ohne Veröffentlichung zu ermitteln und nach dem Publishing abzugleichen.

## Bearbeitete Review-Befunde (verifiziert)

- **PIN-Verwaltung:** Ändern und Entfernen einer PIN verifizieren die bestehende PIN in der Geschäftslogik, nicht nur im Dialog; neue PIN muss bestätigt werden; getrennte Fehlerursachen; Zuordnung zum ausgewählten Profil; Serialisierung gegen parallele Profilwechsel; begrenzte Fehlversuche über die vorhandene Sperre; keine Datenänderung bei Abbruch oder Fehler. PBKDF2-Speicherung unverändert. Regressionstests inklusive Profilwechsel während des Vorgangs grün.
- **Startzugriff:** `ProfileGate` mit Loading/Locked/Unlocked/Error; Profil-ID, PIN-Konfiguration und Auth müssen geklärt sein, bevor geschützte Inhalte, Navigation, Mini-Player oder Mediensteuerung erscheinen. Fail-closed bei Fehler/unbekanntem Zustand; Re-Lock bei Profilwechsel. Instrumentierte Tests grün.
- **Zugangsdaten/Redirects:** Zentrale Erkennung um `username`, `user`, `user_name`, `login` und Varianten (case-insensitive, URL-kodiert, mehrfach) sowie Xtream-Pfadsegmente erweitert; sensible Header erkannt; `XtreamApi` redigiert Ausnahmen/Logs. HTTPS-Downgrade und TLS-Prüfung bleiben. Ein opt-in-Profil erlaubt ausschließlich `github.com` → `release-assets.githubusercontent.com/github-production-release-asset/` mit SAS-Parameter-Whitelist, maximal drei Hops und ohne mitgeführte Zugangsdaten; alle anderen Profile bleiben strikt. MockWebServer-/Hermes-Tests grün.
- **GitHub-Weiterleitungen bestätigt:** `curl` zeigte für APK, `resources.json` und `SHA256SUMS` zwei 302-Hops zum Asset-Host mit `sig`/`jwt`. Die bisherige generelle Regel blockierte den zweiten Hop (`Credential-bearing cross-origin redirect blocked`) — das war die tatsächliche Ursache des gemeldeten Updatefehlers, nicht die Verifikation. Behoben durch das Engpass-Profil.
- **Lokaler Import:** Vollständiger SAF-/ContentResolver-Pfad mit Inhaltsprüfung statt MIME-Vertrauen, BOM-/Sonderzeichen-/Leer-/Übergröße-/Abbruchbehandlung, bereinigten Namen und lokalisierten Fehlern. End-to-end-instrumentierte Tests (URI → Resolver → Parser → Room → Anzeige, auch App-Neustart) grün. Kein physisches Gerät verfügbar; der Betreiberbefund ist code- und emulatorseitig behoben, nicht auf echtem Gerät nachgestellt.
- **In-App-Update:** Download-Fehler, Integritäts-, Signatur- und Installationsfehler sind getrennt; geprüfte APK wird wiederverwendet; Installationsberechtigung und Rückkehr aus den Einstellungen sind berücksichtigt; `STATUS_SUCCESS` meldet tatsächlich installiert. Der vollständige Installationsabschluss benötigt ein reales Gerät und bleibt ungeprüft; die manuelle Installation der signierten APK ohne Deinstallation und ohne Datenverlust bleibt möglich.
- **Backup:** `RestoreResult` mit SUCCESS/PARTIAL/FAILED/CANCELLED und getrenntem Bibliotheks-/Einstellungsstatus; UI zeigt beide Teile und bietet eine sichere Einstellungen-Wiederholung ohne Datenbankänderung. Tests mit absichtlich fehlschlagender Einstellungswiederherstellung grün.
- **Player / EPG / Ressourcen / UI:** Retry-Aufträge sind an Medien-/Fehlergeneration und Player-Identität gebunden (Regression A→B grün); gleiche Absicherung für VOD-Fortschritt und Sleep-Timer. Ungültige XMLTV-Offsets werden nicht mehr als UTC ausgegeben, sondern begrenzt verworfen; gültige Offsets und `Z` behalten ihre Semantik. Signierte leere Kataloge aktivierbar; nicht leere Manifeste verlangen genau die erwarteten, intakten Ressourcen; atomare Aktivierung, Offline-Fallback und Rollback erhalten. Sekundärbutton-Kontrast zentral korrigiert (automatisierte WCAG-Prüfungen grün). Wirkungslose responsive Typografie-Abstraktion entfernt, `sp`-Skala und Systemschriftgröße erhalten. `ACTION_SEND` mit separatem Intentfilter und validierter Eingabe; History-Leerzustand in DE/EN/FR korrigiert.
- **Dokumentation/CI:** README in DE/EN/FR unterscheidet Actions-Secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) von der Repository-Variable `ANDROID_CERT_SHA256` (kein Geheimnis). `THIRD_PARTY_NOTICES.md` weist Guava, FailureAccess, ListenableFuture und ZXing mit belegten Upstream-Lizenzen aus. PR-Trigger führt nur Verifikation aus; Signierung und Publishing bleiben auf vertrauenswürdige Pushes zu `main` beschränkt; manueller Dispatch baut signiert, veröffentlicht aber nicht.

## Weiterhin unvollständig / nicht verifiziert (bewusst offen)

Diese Punkte sind **nicht** durch dieses Release abgeschlossen und werden in einem späteren Auftrag bearbeitet. Die vollständige Liste steht in `MASTERPLAN.md`.

| Punkt | Status | Grund |
| --- | --- | --- |
| Lokaler Playlist-Import auf echtem Gerät | **offen / unvollständig** | Code und instrumentierte End-to-end-Tests grün; kein physisches Gerät, Betreiberbefund nicht dort nachgestellt. |
| In-App-Update bis Installationsabschluss | **offen / unvollständig** | Root Cause (GitHub-Asset-Redirect) bestätigt und behoben; Download/Integrität/Signatur/Übergabe instrumentiert, Installation auf Gerät ungeprüft. |
| PIN-Gate und Picture-in-Picture | **offen / unvollständig** | App-seitiges Pausieren und Sperren geprüft; PiP-Fenster-Verhalten auf Gerät ungeprüft. |
| Externe Playlist-Übergabe aus Browser/Share-Sheet | **offen / unvollständig** | Intentfilter und Validierung instrumentiert geprüft; reale externe Apps nicht getestet. |
| Backup-Teilwiederherstellung auf Gerät | **offen / unvollständig** | Struktur und UI-Wiederholung instrumentiert geprüft; Geräteanzeige ungeprüft. |
| Visuelle Schrift-/Kontrast-/TV-Fokusprüfung | **offen / unvollständig** | Nur automatisierte `sp`-, Token- und WCAG-Prüfungen; 1,5× Systemschrift, AMOLED/Cyan/Ember und TV-Fokus nicht visuell geprüft. |
| Reproduzierbare APK-Prüfsumme | **offen / unvollständig** | Git-Revision im APK ändert R8-Map-ID und SHA bei jedem Metadaten-Commit; `update.json` manuelle nach dem Publishing angeglichen. |
| API-37-Emulator | **offen / unvollständig** | `android-37.0;aosp_atd` vorhanden, Runner erwartet `android-37;aosp_atd`; stabile Matrix bleibt API 30/36. |
| Android 17/18, Langzeit- und Messwertprüfung | **offen / unvollständig** | Nicht zertifiziert; keine Netzwerkwechsel-, Keystore-, 5/30/60-Minuten- oder Performance-Baselines. |
| Abhängigkeitsverifikation nach Updates | **offen / bei Bedarf** | `gradle/verification-metadata.xml` muss bei künftigen Dependency-Änderungen regeneriert werden. |
| FFmpeg, Casting, Stalker/MAG, UDP/Multicast, Timeshift, Multiview, RTMP/SRT, DRM | **offen / außerhalb dieses Releases** | Keine vertrauenswürdige Quelle, Autorisierung oder Gerätematrix; keine DRM-Umgehung. |
