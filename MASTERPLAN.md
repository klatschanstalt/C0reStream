# C0reStream – offene Entwicklungsziele

Verifizierter Ist-Zustand: `PROJECT_STATUS_REPORT.md`. Dauerhaft verbindliche Regeln: `AGENTS.md`. Ein abgeschlossener Auftrag wird hier entfernt; die Historie liegt in der Git-Historie.

Die folgenden Punkte sind **weiterhin unvollständig** und werden in einem späteren Auftrag bearbeitet. Sie sind ausdrücklich **nicht** als erledigt oder verifiziert zu behandeln.

## Weiterhin unvollständig – implementiert, aber nicht vollständig verifiziert

- **Lokaler Playlist-Import auf echtem Gerät:** Code und End-to-end-Instrumentierung (URI → ContentResolver → Parser → Room → Anzeige, inkl. Neustart) sind grün; der ursprüngliche Betreiberbefund wurde nicht auf physischer Hardware nachgestellt oder bestätigt. Status: offen.
- **In-App-Update bis zum Installationsabschluss:** Die blockierte GitHub-Asset-Weiterleitung wurde als Ursache bestätigt und behoben; Download, Integrität, Signatur und Installationsübergabe sind getrennt ausgewiesen. Die tatsächliche Installation auf einem Gerät ist nicht geprüft. Status: offen.
- **PIN-Gate und Picture-in-Picture:** App-seitig wird bei Sperre pausiert und der Gate geschlossen; ein bereits vorhandenes PiP-Fenster kann bis zur Rückkehr das letzte Bild zeigen. Nicht auf Gerät geprüft. Status: offen.
- **Externe Playlist-Übergabe (Browser/Share-Sheet):** `ACTION_SEND`-Filter und Validierung sind registriert und instrumentiert geprüft; die Übergabe aus realen externen Apps ist nicht verifiziert. Status: offen.
- **Backup-Teilwiederherstellung auf Gerät:** Strukturiertes Ergebnis und UI-Wiederholung sind instrumentiert geprüft; Anzeige und Wiederholung auf echtem Gerät nicht verifiziert. Status: offen.
- **Visuelle Theme-Prüfung:** WCAG-Token- und `sp`-Strukturtests sind grün; 1,5-fache Systemschriftgröße, Android-TV-Fokus sowie AMOLED/Cyan/Ember auf Gerät nicht visuell geprüft. Status: offen.
- **Reproduzierbare APK-Prüfsumme:** AGP bettet die Git-Revision ein; bereits ein Metadaten-Commit ändert `pg-map-id` und APK-SHA. `update.json` wurde nach dem Publishing an die veröffentlichte APK angepasst; eine dauerhafte, commit-unabhängige Checksum-Ermittlung fehlt. Status: offen.
- **API-37-Emulator:** `sdkmanager` veröffentlicht `system-images;android-37.0;aosp_atd`, der Emulator-Runner löst aus `api-level: 37` jedoch `android-37;aosp_atd` auf. Keine verlässliche Matrix-Erweiterung. Status: offen.
- **Geräte- und Zertifizierungslücken:** Keine Gerätetests jenseits API 30/36; Android 17 und 18 nicht zertifiziert. Langzeitläufe (5/30/60 Minuten), Netzwerkwechsel, Keystore-Gerätefehler und belastbare Messwerte zu Startzeit, Senderwechsel, RAM, CPU, Akku, Datenbank und Import fehlen; Macrobenchmark/Baseline Profiles nur bei konkretem Nutzen. Status: offen.
- **Abhängigkeitsmetadata nach Updates:** Gradle Dependency Verification ist aktiv; bei künftigen Abhängigkeitsaktualisierungen muss `gradle/verification-metadata.xml` regeneriert werden. Status: offen (bei Bedarf).

## Offene Entwicklungsziele (nicht Teil des letzten PATCH-Releases)

- **Lokale Medienwiedergabe:** Der Player nutzt bisher HTTP(S)- und RTSP-Quellen; das Abspielen lokaler Mediendateien ist nicht implementiert.
- **Update-Hinweis auf dem Home-Screen:** Eine Update-Benachrichtigung in der Schublade ist vorhanden; ein dauerhafter Hinweis auf Launcher/Home-Screen fehlt.
- **FFmpeg-Softwaredecoder:** `media3-decoder-ffmpeg` wird nicht öffentlich publiziert; eine lokal gebaute AAR fehlt. Der Extension-Modus bleibt wirkungslos, bis eine vertrauenswürdige Distribution vorhanden ist.
- **Alternative Player-Engines:** Eingesetzt wird ausschließlich Media3/ExoPlayer; weitere Engines sind nicht integriert.
- **Neue Streaming-Protokolle:** Rohes UDP und Multicast sind nicht abspielbar (Media3 RTSP unterstützt nur Unicast und interleaved TCP; kein `MulticastLock` mit `CHANGE_WIFI_MULTICAST_STATE`). Zugangsdaten in RTSP-URLs werden aus Sicherheitsgründen abgelehnt; eine sichere Alternative ist nicht spezifiziert.
- **Erweiterte Player-Einstellungen:** Über die vorhandenen globalen und senderbezogenen Wiedergabeprofile sowie die Codec-Diagnostik hinausgehende Optionen sind nicht umgesetzt.
- **Timeshift/Ringpuffer:** Nicht implementiert; keine autorisierten Testzugänge.
- **Multiview:** Decoder- und geräteabhängig; keine verlässliche Gerätematrix vorhanden.
- **Casting:** Benötigt eine registrierte Receiver-ID und Google-Play-Services-Zertifizierung.
- **Stalker-/MAG-Portale:** Keine autorisierten Testzugänge; keine Umgehung von MAC-/Portal-Sperren geplant.
- **Vollständiges Redesign:** Das bestehende Material-3-Design ist etabliert; ein grundlegender Neuentwurf steht aus.
