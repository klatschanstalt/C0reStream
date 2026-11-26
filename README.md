# C0reStream

Android IPTV and media client for user-provided, authorized streams. Part of the C0re product family.

## Download

[GitHub Releases](https://github.com/klatschanstalt/C0reStream/releases) — development previews are published after successful CI builds. Download C0reStream-debug.apk and SHA256SUMS. Preview APKs are debug-signed, not production releases; updates may require uninstalling, which erases local data. Do not enter sensitive credentials while security findings remain open.

Android 11 minimum (API 30), target API 37, compile API 37.1. Actual Android 11–17 device compatibility remains to be verified; Android 18 preparation is planned.

## Development

Kotlin, Jetpack Compose, Media3, Room, Gradle 9.6.0. CI builds debug and release variants, runs unit-test and lint tasks, and publishes checksummed debug previews. See MASTERPLAN.md, SECURITY.md and PROJECT_STATUS_REPORT.md. Production releases require durable signing and security acceptance.

Application ID: pw.c0re.stream. Internal Kotlin namespace: pw.c0re.stream.
