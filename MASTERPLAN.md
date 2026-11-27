# C0reStream – offene Entwicklungsziele

Verifizierter Ist-Zustand: `PROJECT_STATUS_REPORT.md`. Dauerhaft verbindliche Regeln: `AGENTS.md`. Ein abgeschlossener Auftrag wird hier entfernt; die Historie liegt in der Git-Historie.

## Offen, ohne bestätigten Umsetzungsweg

- Rohes UDP- und Multicast-Playback: Media3 RTSP unterstützt nur Unicast und interleaved TCP. Ein `MulticastLock` mit `CHANGE_WIFI_MULTICAST_STATE` ist nicht integriert.
- RTSP-Authentifizierung: Zugangsdaten in Stream-URLs werden aus Sicherheitsgründen abgelehnt; eine sichere Alternative ist nicht spezifiziert.
- Casting: benötigt eine registrierte Receiver-ID und Google-Play-Services-Zertifizierung.
- Multiview: decoder- und geräteabhängig; keine verlässliche Gerätematrix vorhanden.
- Timeshift/Ringpuffer und Stalker-/MAG-Portale: keine autorisierten Testzugänge, keine Umgehung von MAC-/Portal-Sperren geplant.
- FFmpeg-Softwaredecoder: das Modul `media3-decoder-ffmpeg` wird nicht öffentlich publiziert; eine lokal gebaute AAR fehlt. Der Extension-Modus bleibt wirkungslos, bis eine vertrauenswürdige Distribution vorhanden ist.

## Verifikationslücken

- Gerätetests jenseits der Emulator-Matrix API 30/36; Android 17 und 18 sind nicht zertifiziert.
- Längere Langzeitläufe (5/30/60 Minuten), Netzwerkwechsel und Keystore-Gerätefehler.
- Belastbare Messwerte für Startzeit, Senderwechsel, RAM, CPU, Akku, Datenbank und Import; Macrobenchmark/Baseline Profiles nur bei konkretem Nutzen.
