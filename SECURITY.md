# Security policy

Report vulnerabilities privately to the repository owner. Never include real IPTV credentials or stream URLs.

## Implemented controls (source review, 2026-11-27)

- Playlist, EPG and stream URLs are encrypted at rest with an AES key held by the Android Keystore; Room schema v4 migrates legacy plaintext rows transactionally.
- Backup envelopes use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a versioned header and authenticated metadata.
- Only HTTP(S) URLs are accepted. Embedded credentials, control characters and overlong URLs are rejected; redirects are resolved hop-by-hop and an HTTPS call can never follow an HTTP hop.
- M3U, XMLTV and Xtream parsers enforce record, depth, line and text limits; DTDs and external entities are rejected.
- Room writes that replace a playlist run in a transaction; a failed refresh keeps the previous channel list and refuses suspicious lineup shrinkage so favourites and history are not erased by a degraded provider response.
- A restore deduplicates channels by stable identity, preserves all history timestamps and keeps settings failures visible without reverting successfully restored library data.
- Update metadata must use HTTPS, and an APK is installed only after SHA-256, package-name and signing-certificate verification.
- App logs redact URLs and credential parameters; Android backup is disabled; exported M3U files are shared read-only through FileProvider.
- CI permissions are read-only except for release jobs, and no credentials are logged.

## Accepted risk

HTTP IPTV sources remain supported without mandatory opt-in by explicit owner decision. Prefer HTTPS where available; transport-risk disclosure stays visible in settings. HTTP support alone is not a release blocker.

## Release gate

Production release requires passing unit and instrumented tests, zero lint errors, an independent review of the changed code for security, cancellation, lifecycle and data-loss regressions, durable signing-key custody and a verified update path. Android 18 readiness is not a compatibility certification.
