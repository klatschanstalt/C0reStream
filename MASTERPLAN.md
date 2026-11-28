# C0reStream – offene Entwicklungsziele

Verifizierter Ist-Zustand: `PROJECT_STATUS_REPORT.md`. Dauerhaft verbindliche Regeln: `AGENTS.md`. Ein abgeschlossener Auftrag wird hier entfernt; die Historie liegt in der Git-Historie.

## Offene Entwicklungsziele

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
- **Geräte- und Zertifizierungslücken:** Keine Gerätetests jenseits der Emulator-Matrix API 30/36; Android 17 und 18 sind nicht zertifiziert. Langzeitläufe (5/30/60 Minuten), Netzwerkwechsel und Keystore-Gerätefehler sowie belastbare Messwerte zu Startzeit, Senderwechsel, RAM, CPU, Akku, Datenbank und Import fehlen; Macrobenchmark/Baseline Profiles nur bei konkretem Nutzen.
