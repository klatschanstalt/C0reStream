# Security policy
Report vulnerabilities privately to the repository owner. Do not include real IPTV credentials or stream URLs.

## Open findings (2026-11-26 source review)
- High: Xtream credentials and credential-bearing stream URLs persisted without application-level encryption; plan Keystore-backed migration without data loss.
- Accepted transport risk: HTTP remains supported without mandatory opt-in by explicit owner decision. Prevent unintended HTTPS downgrades; disclose plaintext transport risks.
- High: private repository update endpoint is not publicly accessible; design signed, authenticated distribution without embedding tokens.
- Medium: review external intents, parser limits, XML processing, export URI scope, playlist refresh transactionality, media lifecycle and dependency advisories.
- Release gate: resolve high-risk findings, verify migration, test malformed inputs, test Android 11–17 runtime and signed APK. Android 18 readiness is not a compatibility certification.

Binding owner decision: HTTP and HTTPS IPTV sources remain supported without mandatory opt-in. Prefer HTTPS where available; prevent unintended HTTPS-to-HTTP redirects and retain URL validation, credential encryption and transport-risk disclosure. HTTP support alone is not a release blocker.
