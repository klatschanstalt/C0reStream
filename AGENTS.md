# C0reStream

- Work only in this repository. Preserve application ID `pw.c0re.stream` and minimum API 30 (Android 11).
- Production signing uses the owner-managed `ANDROID_*` GitHub secrets. Never generate a new release key, commit secrets, force push, add paid services or change global configuration.
- CI verifies unit tests, lint and instrumented tests on every push to `main`. Only verified, durably signed production releases are published; no debug previews.
- Before integration, review security, regressions, cancellation, lifecycle, resource limits and data loss. Add targeted regression tests; never claim an unexecuted test passed.
- Never disable lint to hide defects. Update `PROJECT_STATUS_REPORT.md` with verified facts only.
- No tracking, ads, bundled content or unauthorized streams. HTTP and HTTPS sources are supported by owner decision; prefer HTTPS and block HTTPS-to-HTTP redirects.
- Target API 37, compile SDK 37.1. Support claims for Android 11–17 require evidence; Android 18 preparation uses official APIs and feature detection only.
