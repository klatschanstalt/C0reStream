# Security policy

Report vulnerabilities privately to the repository owner; never include real IPTV credentials or stream URLs.

## Implemented controls

- Playlist, EPG and stream URLs are encrypted at rest with an AES key held by the Android Keystore; the transactional Room migration `2 → 3` converts legacy plaintext rows (`EncryptUrlsMigration`). Later migrations `3 → 4`, `4 → 5` and `5 → 6` add stable keys/EPG/progress, profiles and import ordering. Keystore failures are classified, never silently recreated.
- Backups use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a versioned, authenticated header.
- NetworkPolicy accepts HTTP(S) and structurally validated RTSP/RTP URLs. Embedded credentials, control characters and overlong URLs are rejected; HTTPS-to-HTTP downgrades are blocked on every redirect hop, and sensitive headers/query parameters never cross origins. HTTPS is preferred.
- M3U, XMLTV and Xtream parsers enforce record, depth, line and text limits; DTDs and external entities are rejected.
- Playlist replacement runs in one Room transaction and refuses suspicious lineup shrinkage; remove-channel cleanup includes dependent reminders.
- Update metadata must be public HTTPS; an APK is installed only after SHA-256, package-name, monotonic-version and signing-certificate checks, followed by user confirmation. No GitHub token is embedded.
- The non-executable resource manifest is signed and verified with the existing production certificate over the exact downloaded bytes; a mismatching signature keeps the previous offline version.
- Logs redact URLs, user-info pairs and credential parameters; Android backup is disabled; CI permissions are minimal, Actions are SHA-pinned and credentials are never logged.
- Production APK signature is verified against certificate fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543` before publication.

## Accepted risk

HTTP sources are supported by explicit owner decision; HTTPS is preferred and transport risk is disclosed in settings. Raw UDP/multicast, RTSP credentials in URLs and third-party decoding extensions are not implemented or are explicitly rejected. No ads, tracking, analytics or bundled content.

## Release evidence

The latest verified release and its CI run, checksum and signature are recorded in `PROJECT_STATUS_REPORT.md`.
