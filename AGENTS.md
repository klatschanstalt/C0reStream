# C0reStream

- Work only in this repository. Preserve application ID `pw.c0re.stream` and minimum API 30 (Android 11).
- Production signing uses the owner-managed `ANDROID_*` GitHub secrets. Never generate a new release key, commit secrets, force push, add paid services or change global configuration.
- CI verifies unit tests, lint and instrumented tests on every push to `main`. Only verified, durably signed production releases are published; no debug previews.
- Before integration, review security, regressions, cancellation, lifecycle, resource limits and data loss. Add targeted regression tests; never claim an unexecuted test passed.
- Never disable lint to hide defects. Update `PROJECT_STATUS_REPORT.md` with verified facts only.
- No tracking, ads, bundled content or unauthorized streams. HTTP and HTTPS sources are supported by owner decision; prefer HTTPS and block HTTPS-to-HTTP redirects.
- Target API 37, compile SDK 37.1. Support claims for Android 11–17 require evidence; Android 18 preparation uses official APIs and feature detection only.
- Maintain `README.md` as the canonical branded, trilingual (DE/EN/FR) public documentation. Preserve its banner, badges, language navigation, download and verification links, and legal notices. Update all three language sections together whenever verified user-facing features, requirements, build instructions or security behavior change. Document only implemented features; distinguish device-dependent support and unverified capabilities. Use permanent latest-release links and dynamic badges instead of hardcoded release metadata; do not introduce a separate README-only automation or redundant CI loops. Include documentation changes in the corresponding implementation commit before release.
- No fixed APK size limit or target; report size for information only. Prioritize functionality, stability, security, playback quality and compatibility while avoiding unnecessary dependencies.

## Maintenance

- Review this project for currency on relevant mandates; prefer current stable, mutually compatible technologies and replace deprecated implementations.
- Fix confirmed security vulnerabilities first; clean unused files and keep dependencies small and explainable.
- Update documentation synchronously; remove finished tasks from active plans, keep only the last verified state in `PROJECT_STATUS_REPORT.md`, and use Git history as the change log.
- Avoid unnecessary tests, reports and automations; consider resource and token consumption.
