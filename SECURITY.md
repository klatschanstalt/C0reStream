# Security policy

Report vulnerabilities privately to the repository owner; never include real IPTV credentials or stream URLs.

## Implemented controls

- Playlist, EPG and stream URLs are encrypted at rest with an AES key held by the Android Keystore; Room v4 migrates legacy plaintext rows transactionally.
- Backups use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a versioned, authenticated header.
- Only HTTP(S) URLs are accepted. Embedded credentials, control characters and overlong URLs are rejected; redirects are resolved hop-by-hop so an HTTPS call can never follow an HTTP hop.
- M3U, XMLTV and Xtream parsers enforce record, depth, line and text limits; DTDs and external entities are rejected.
- Playlist replacement runs in one Room transaction and refuses suspicious lineup shrinkage; remove-channel cleanup includes dependent reminders.
- Update metadata must be public HTTPS; an APK is installed only after SHA-256, package-name and signing-certificate checks, followed by user confirmation. No GitHub token is embedded.
- Logs redact URLs and credential parameters; Android backup is disabled; CI permissions are minimal and credentials are never logged.
- Production APK signature is verified against certificate fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543` before publication.

## Accepted risk

HTTP sources are supported by explicit owner decision. HTTPS is preferred, transport risk is disclosed in settings, and HTTP support alone is not a release blocker.

## Release evidence

CI run `45934346757` passed unit tests, lint and instrumented tests on API 30/36; release `v1.3.0` contains a signed APK with SHA-256 `e3a98be25e6abdd32f5cefc08bf7dfb7379f5ace8da1c5e158d073e0be4b5bad`.
