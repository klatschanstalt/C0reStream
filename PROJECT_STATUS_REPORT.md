# PROJECT_STATUS_REPORT

Letzter vollständig verifizierter Gesamtstand, 2026-11-27.

## Verifikation

CI-Lauf `46022423220` (Commit `9057db7`) vollständig grün; der reaktivierte Push-Lauf `46025325554` (Commit `43218e4`) ebenfalls vollständig grün.

- 91 Unit-Tests ohne Fehler; `:app:lintDebug` mit 0 Fehlern und 0 Warnungen.
- 10 instrumentierte Tests je API 30 und API 36 ohne Fehler.
- Release-Build mit R8, `signResourceManifest` und Signaturprüfung erfolgreich; Checksumme geprüft.

## Design- und Branding-Stand

- `assets/branding/` bleibt Designquelle; APK enthält nur die benötigten Launcher-/Splash-/Systemressourcen. GitHub-, Social- und Promo-Grafiken sind nicht im APK.
- Adaptive, runde und monochrome Icons liegen unter `res/mipmap/` und `res/drawable/`; Splash- und Fenster-Theme nutzen die native Android-Splashscreen-API (API 31+) und eine kompatible Variante für ältere Versionen. Kein zweiter, künstlich verzögerter Splash.
- Zentrale Design-Tokens (Palette aus den Assets, Typografie, Shapes, Motion, Gradienten, Abstände) und wiederverwendbare `Brand*`-Komponenten sind in Compose verankert. Dark ist Referenz; Light, System und AMOLED bleiben markenkonform.
- Alle vorhandenen Ansichten (Navigation, Playlists, Senderdetails, Favoriten, Verlauf, Suche, VOD, Einstellungen, Profile, Backup, Player) nutzen dieselben Komponenten. Responsive Auswahl erfolgt über Window Size Classes mit Höhen-Fallback; WindowInsets und IME werden berücksichtigt.
- Barrierefreiheit: lokalisierte Content-Descriptions, 48-dp-Zielgrößen, Fokus-/Semantikrollen, skalierbare `sp`-Typografie ohne zusätzliche Breiten-Skalierung, reduzierte Bewegung beachtet.
- QA-Agent prüfte den Diff; festgestellte Defekte (englische Fehlertexte, nicht zugängliche Player-Overlay, PIN-Gate-Insets, Button-Zentrierung, Gradienten-Parameter, TV-Bannerfarben, ungenutzte Ressourcen) wurden behoben und erneut verifiziert.

## Artefakte

- Lokaler Release-Build: `app/build/outputs/apk/release/app-release-unsigned.apk`, 6.430.253 Bytes.
- Signierte Verifikations-APK aus CI-Artefakt `C0reStream-signed-verification`: `C0reStream.apk`, 6.434.349 Bytes, SHA-256 `1d3da403fc32262633f8209df08d9f35592d6d381730cee514ab0d71e8984a9e` (`sha256sum --check` OK).
- `pw.c0re.stream`, versionCode 6, versionName 1.5.0, minSdk 30, targetSdk 37; Signatur v2 mit Fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543`.
- Keine neue öffentliche Version veröffentlicht: `v1.5.0` bleibt aktuell, da sich die Versionsnummer nicht änderte und der Release-Job vorhandene Releases schützt.

## Nicht verifiziert

- Echte Geräte-Screenshots und physische Gerätetests; Emulatorläufe nur auf API 30/36.
- Visuelle Ausrichtung des themed monochrome Icons auf allen Launcher-Masken; Player-Steuerzeilen bei Display-Cutouts/Split-Screen; Foldable- und TV-Geräteverhalten.
- APK-Größe weiter über dem <5-MB-Ziel (gemessene Begründung unverändert).
