# Ham3L TV – approved roadmap
1. Modernize SDK, AGP, Gradle wrapper, Kotlin, KSP, Compose, Media3, Room, WorkManager, DataStore and dependencies; version catalog; resolve lint.
2. Material 3 responsive UI, themes, animations, branding, accessibility, DE/EN/FR.
3. Player: HLS/DASH, track selection, PiP, mini-player, retry, buffering, gestures, sleep timer, diagnostics, radio.
4. Playlists: M3U/M3U8, Xtream, import/export, stable IDs, refresh, groups, favorites, deduplication.
5. EPG: XMLTV, gzip, mapping, timeline, reminders, catch-up, timezone, cache.
6. VOD: movies, series, episodes, resume, watchlist, search.
7. TV: D-pad, focus, remote, numeric selection, TV layouts and testing.
8. Optional casting, multiview, authorized recording, profiles, PIN, encrypted backups, QR import.
9. Security: Keystore credentials, component and URL validation, parser limits, no secrets/logged credentials, no ads/tracking.
10. Performance: startup, memory, large lists, caching, R8.
11. Settings and diagnostics center.
12. CI, tests, Dependabot, signing, release notes, checksums and artifact retention.
13. Secure update distribution without embedded private GitHub tokens; user-confirmed installation.
14. Compact documentation and verified status.
15. Autonomous incremental development, safe commits and pushes, no paid services.
16. Acceptance: zero compiler/lint errors, resolve actionable warnings, compatible stable dependencies, device tests, signed release, verified updates.

Binding decision: minimum Android 11 (API 30); target latest stable SDK. Older Android versions are unsupported.
