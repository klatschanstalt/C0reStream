# Ham3L TV

**Powered by Ham3L** - Eigenstaendiger Android IPTV-Client (Kotlin + Jetpack Compose).

Konzeptionell inspiriert von iptvnator (https://github.com/4gray/iptvnator),
komplett eigene Implementierung, eigenes Design und Branding.

## Features
- M3U / M3U8-Playlists per URL hinzufuegen (tvg-logo, tvg-id, group-title)
- Xtream-Codes-Login (player_api.php, get_live_streams)
- VOD-Filme und Serien (Staffeln/Episoden) ueber Xtream-Codes
- Live-TV-Wiedergabe via Media3/ExoPlayer im MediaSession-Vordergrunddienst
  (Lockscreen-/Kopfhoerer-Steuerung, WakeLock, konfigurierbarer Puffer)
- XMLTV-EPG-Parser mit "laeuft gerade"-Anzeige und Tagesprogramm-Guide
- Catch-up/Timeshift: vergangene Sendungen ueber catchup-source ansehen
- Sender-Zapping (vor/zurueck), Tonspur-Auswahl, Bild-in-Bild, Mini-Player
- Globale Kanalsuche ueber alle Playlists
- Backup: alle Playlists als M3U exportieren und teilen
- Favoriten, Wiederverlauf, Kanalgruppen als Filter-Chips
- Pro Playlist: User-Agent und HTTP-Proxy konfigurierbar
- Update-Check, lokales Diagnose-Protokoll, automatischer Playlist-Refresh (12 h)
- Zwei Brand-Themes (Ham3L Cyan / Ham3L Ember), Splash-Screen, Android-TV-tauglich (Leanback)
- M3U-Links aus dem Browser oder Teilen-Menue direkt oeffnen

## Bauen
    ./gradlew assembleDebug

Mindestens Android 7.0 (API 24), Zielsystem Android 15 (API 35).

## Struktur
    app/src/main/java/tv/ham3l/app/
    - data/       M3U- und EPG-Parser, Repository, Room-DB (Playlists, Kanaele, Favoriten, Verlauf)
    - ui/         Compose-Screens: Playlists, Kanalliste, Player, Favoriten, Verlauf, Einstellungen
    - ui/theme/   Ham3L-Brand-Design

## APK selbst bauen (einfachster Weg)

Das Repo enthaelt einen GitHub-Actions-Workflow (.github/workflows/build-apk.yml):

1. Projekt nach GitHub pushen (main-Branch)
2. Auf github.com im Tab "Actions" den Lauf "Build Ham3L TV APK" abwarten
3. Unter "Artifacts" die Ham3LTV-APK herunterladen und auf dem Geraet installieren
   (Installation aus unbekannten Quellen erlaubt geben)
4. Optional: einen Tag pushen (z.B. v1.0.0), dann haengt die APK automatisch
   an einem GitHub-Release

Alternativ lokal: Android Studio oeffnen, Sync abwarten, Run druecken.

## Lizenz
Alle Rechte bei Ham3L.
