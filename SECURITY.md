# Security policy

Report vulnerabilities privately to the repository owner; never include real IPTV credentials or stream URLs.

## Implemented controls

- Playlist, EPG and stream URLs are encrypted at rest with an AES key held by the Android Keystore; the transactional Room migration `2 → 3` converts legacy plaintext rows (`EncryptUrlsMigration`). Later migrations `3 → 4`, `4 → 5` and `5 → 6` add stable keys/EPG/progress, profiles and import ordering. Keystore failures are classified, never silently recreated.
- Backups use PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM with a versioned, authenticated header.
- NetworkPolicy accepts HTTP(S) and structurally validated RTSP/RTP URLs. Embedded credentials, control characters and overlong URLs are rejected; HTTPS-to-HTTP downgrades are blocked on every redirect hop, and sensitive headers/query parameters never cross origins. HTTPS is preferred.
- M3U, XMLTV and Xtream parsers enforce record, depth, line and text limits; DTDs and external entities are rejected.
- Playlist replacement runs in one Room transaction and refuses suspicious lineup shrinkage; remove-channel cleanup includes dependent reminders.
- Update metadata must be public HTTPS; an APK is installed only after SHA-256, package-name, monotonic-version and signing-certificate checks, followed by user confirmation. No GitHub token is embedded. The owner's server is the primary direct-HTTPS distribution source (no redirects, no temporary tokens); GitHub metadata stays compatible for older clients by pointing `apkUrl` at that server.
- The non-executable resource manifest is signed and verified with the existing production certificate over the exact downloaded bytes; a mismatching signature keeps the previous offline version. Mirrored resources are byte-identical to the published signed assets; their signature is re-verified before activation.
- Logs redact URLs, user-info pairs and credential parameters; Android backup is disabled; CI permissions are minimal, Actions are SHA-pinned and credentials are never logged.
- Production APK signature is verified against certificate fingerprint `65FB26B0F617583228EA8813080FA7E2F8F6421022D8D279F928C726EA6E8543` before publication.

## Accepted risk

HTTP sources are supported by explicit owner decision; HTTPS is preferred and transport risk is disclosed in settings. Raw UDP/multicast, RTSP credentials in URLs and third-party decoding extensions are not implemented or are explicitly rejected. No ads, tracking, analytics or bundled content.

## Update diagnostics

The following codes distinguish the stages of the update flow for support and release acceptance.
They are stable identifiers, not raw exception text. No URL, user-info, `sig`, `jwt` or other signed
download parameter is written to the local log. Installed releases report what their own code can
distinguish: v1.7.2 logs stage-specific detail (redirect, HTTP/incomplete download, checksum,
archive, package, version, signer, installer) and shows localized messages for the download,
integrity, signature and permission stages; v1.7.1 collapses every failure into one generic
"verification failed" message. The finer codes below are the diagnostic contract for support and
for a future client release; they are not a claim about strings shown by v1.7.1.

| Code | Stage | DE | EN | FR |
| --- | --- | --- | --- | --- |
| UPD-001 | Update-Metadaten nicht erreichbar | Update-Informationen sind nicht erreichbar. | Update information is unreachable. | Informations de mise à jour injoignables. |
| UPD-002 | Ungültige Update-Metadaten | Die Update-Informationen sind ungültig. | Update information is invalid. | Informations de mise à jour invalides. |
| UPD-003 | Weiterleitung blockiert | Eine Weiterleitung wurde aus Sicherheitsgründen blockiert. | A redirect was blocked for security reasons. | Une redirection a été bloquée pour des raisons de sécurité. |
| UPD-004 | Download fehlgeschlagen | Der Download ist fehlgeschlagen. | The download failed. | Le téléchargement a échoué. |
| UPD-005 | Download unvollständig | Der Download ist unvollständig. | The download is incomplete. | Le téléchargement est incomplet. |
| UPD-006 | SHA-256-Abweichung | Die Prüfsumme stimmt nicht überein. | The checksum does not match. | La somme de contrôle ne correspond pas. |
| UPD-007 | Ungültige APK | Die APK ist ungültig. | The APK is invalid. | L'APK est invalide. |
| UPD-008 | Falscher Paketname | Der Paketname stimmt nicht überein. | The package name does not match. | Le nom du paquet ne correspond pas. |
| UPD-009 | Falsche Versionsnummer | Die APK ist nicht neuer als die installierte Version. | The APK is not newer than the installed version. | L'APK n'est pas plus récente que la version installée. |
| UPD-010 | Abweichendes Signaturzertifikat | Das Signaturzertifikat stimmt nicht überein. | The signing certificate does not match. | Le certificat de signature ne correspond pas. |
| UPD-011 | Installationsberechtigung fehlt | Die Installationsberechtigung ist aus. | Install permission is off. | L'autorisation d'installation est désactivée. |
| UPD-012 | Installation abgelehnt | Das System hat die Installation abgelehnt. | The system rejected the installation. | Le système a rejeté l'installation. |
| UPD-013 | Installation abgebrochen | Die Installation wurde abgebrochen. | The installation was cancelled. | L'installation a été annulée. |
| UPD-014 | Unbekannter Installationsfehler | Die Installation konnte nicht abgeschlossen werden. | The installation could not be completed. | L'installation n'a pas pu être terminée. |

## Own-server distribution

The primary update endpoints are served directly from this server with no external redirect, no
authentication and no temporary tokens:

- `https://stream.c0re.pw/distribution/update.json`
- `https://stream.c0re.pw/distribution/releases/v1.7.2/C0reStream.apk`
- `https://stream.c0re.pw/distribution/releases/v1.7.2/SHA256SUMS`
- `https://stream.c0re.pw/distribution/resources/resources.json`
- `https://stream.c0re.pw/distribution/resources/resources.sig`

One isolated Caddy route serves these paths from a private canonical tree outside the webroot.
Directory listing is disabled; version-bound files are immutable and cacheable, while update
metadata and resources revalidate. A missing file returns a real 404, never the website's
fallback page. The signing certificate, package name, version and checksum are re-verified before
a release becomes active, and the publicly served APK is verified again afterwards.

## Safe manual update path

If the in-app updater is unavailable, download `C0reStream.apk` and `SHA256SUMS` from the
[latest release](https://github.com/klatschanstalt/C0reStream/releases/latest) or from the
own-server distribution above, verify with `sha256sum -c SHA256SUMS`, confirm the signature with
`apksigner verify --print-certs C0reStream.apk` against the fingerprint in `PROJECT_STATUS_REPORT.md`,
and open the APK on the device. Android will ask for confirmation; the installed app is **not**
uninstalled and its data is preserved. Never install an APK whose checksum or certificate differs.

Installed v1.7.0/v1.7.1 clients can show a premature failure notification while the system is
still waiting for the install confirmation (`STATUS_PENDING_USER_ACTION`): their receiver treats
every non-success status as an error. This cannot be repaired by server or metadata changes.
If the system confirmation dialog appears, confirm it and check the version in settings instead
of relying on that notification. v1.7.2 no longer misreports this status.

## Release evidence

The latest verified release and its CI run, checksum and signature are recorded in `PROJECT_STATUS_REPORT.md`.
