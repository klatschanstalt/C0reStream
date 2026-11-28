# C0reStream – offene Entwicklungsziele

Verifizierter Ist-Zustand: `PROJECT_STATUS_REPORT.md`. Dauerhaft verbindliche Regeln: `AGENTS.md`. Ein abgeschlossener Auftrag wird hier entfernt; die Historie liegt in der Git-Historie.

Die folgenden Punkte sind **weiterhin unvollständig** und werden in einem späteren Auftrag bearbeitet. Sie sind ausdrücklich **nicht** als erledigt oder verifiziert zu behandeln.

## Weiterhin unvollständig – implementiert, aber nicht vollständig verifiziert

- **Lokaler Playlist-Import auf echtem Gerät:** Code und End-to-end-Instrumentierung (URI → ContentResolver → Parser → Room → Anzeige, inkl. Neustart) sind grün; der ursprüngliche Betreiberbefund wurde nicht auf physischer Hardware nachgestellt oder bestätigt. Status: offen.
- **Update-Blockade auf v1.7.0/v1.7.1 – praktische Abnahme:** Ursache bestätigt (strenger Redirect-Guard blockt GitHubs `sig`-Hop). `update.json` verweist jetzt auf eine credential-freie, bytegleiche HTTPS-Kopie der v1.7.2-APK; Artefakte, SHA-256, Paketname, versionCode und Zertifikat sind geprüft. Installation, Versionsanzeige und Erhalt der Nutzerdaten auf einem physischen Gerät fehlen. Der Betreiberbefund gilt nicht als erledigt. Status: offen.
- **PIN-Gate und Picture-in-Picture:** App-seitig wird bei Sperre pausiert und der Gate geschlossen; ein bereits vorhandenes PiP-Fenster kann bis zur Rückkehr das letzte Bild zeigen. Nicht auf Gerät geprüft. Status: offen.
- **Externe Playlist-Übergabe (Browser/Share-Sheet):** `ACTION_SEND`-Filter und Validierung sind registriert und instrumentiert geprüft; die Übergabe aus realen externen Apps ist nicht verifiziert. Status: offen.
- **Backup-Teilwiederherstellung auf Gerät:** Strukturiertes Ergebnis und UI-Wiederholung sind instrumentiert geprüft; Anzeige und Wiederholung auf echtem Gerät nicht verifiziert. Status: offen.
- **Visuelle Theme-Prüfung:** WCAG-Token- und `sp`-Strukturtests sind grün; 1,5-fache Systemschriftgröße, Android-TV-Fokus sowie AMOLED/Cyan/Ember auf Gerät nicht visuell geprüft. Status: offen.
- **Reproduzierbare APK-Prüfsumme (nicht mehr erforderlich, aber weiterhin nicht bitidentisch):** AGP bettet die Git-Revision ein; ein Metadaten-Commit ändert `pg-map-id` und APK-SHA. Der Release-Job erzeugt `update.json` jetzt automatisch aus dem veröffentlichten und erneut geprüften Artefakt; die manuelle Nachkorrektur entfällt. Ein bitidentischer Build bleibt nicht erreicht und ist für das Verfahren nicht nötig. Status: offen (nicht bitidentisch).
- **API-37-Emulator:** `sdkmanager` veröffentlicht `system-images;android-37.0;aosp_atd`, der Emulator-Runner löst aus `api-level: 37` jedoch `android-37;aosp_atd` auf. Keine verlässliche Matrix-Erweiterung. Status: offen.
- **Geräte- und Zertifizierungslücken:** Keine Gerätetests jenseits API 30/36; Android 17 und 18 nicht zertifiziert. Langzeitläufe (5/30/60 Minuten), Netzwerkwechsel, Keystore-Gerätefehler und belastbare Messwerte zu Startzeit, Senderwechsel, RAM, CPU, Akku, Datenbank und Import fehlen; Macrobenchmark/Baseline Profiles nur bei konkretem Nutzen. Status: offen.
- **Abhängigkeitsmetadata nach Updates:** Gradle Dependency Verification ist aktiv; bei künftigen Abhängigkeitsaktualisierungen muss `gradle/verification-metadata.xml` regeneriert werden. Status: offen (bei Bedarf).
- **Einzelne apkUrl für mehrere Client-Generationen:** v1.7.0/v1.7.1 benötigen eine credential-freie URL, v1.7.2 akzeptiert nur `github.com/.../releases/...`. Die aktuelle Spiegel-URL bedient die alten Versionen; eine künftige APK-Version darf sie nicht unverändert übernehmen. Klärung durch clientseitige URL-Auswahl oder gestaffelte Metadaten nötig. Status: offen.
- **Feindiagnostik UPD-001…UPD-014 in der App:** Taxonomie und DE/EN/FR-Meldungen sind in `SECURITY.md` dokumentiert; v1.7.2 zeigt bereits grobe Stufenmeldungen. Die vollständige code- und UI-seitige Unterscheidung würde eine neue APK erfordern und wurde in diesem Auftrag bewusst nicht veröffentlicht. Status: offen.

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
