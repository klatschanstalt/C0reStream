# Security policy
Report vulnerabilities privately to the repository owner. Do not include real IPTV credentials or stream URLs.

## Open findings (2026-11-26 source review)
- High: Xtream credentials and credential-bearing stream URLs persisted without application-level encryption; plan Keystore-backed migration without data loss.
- High: HTTP permitted globally for legacy IPTV; require explicit user opt-in and prevent HTTPS downgrade across metadata and playback clients. Migrate to network security configuration before API 38 targeting.
- High: private repository update endpoint is not publicly accessible; design signed, authenticated distribution without embedding tokens.
- Medium: review external intents, parser limits, XML processing, export URI scope, playlist refresh transactionality, media lifecycle and dependency advisories.
- Release gate: resolve high-risk findings, verify migration, test malformed inputs, test Android 11–17 runtime and signed APK. Android 18 readiness is not a compatibility certification.
