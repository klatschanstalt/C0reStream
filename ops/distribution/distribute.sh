#!/usr/bin/env bash
# C0reStream distribution synchroniser.
#
# Runs unprivileged as ham3l. Detects a newer stable GitHub release, verifies the
# exact signed bytes, installs them in a versioned directory, switches the public
# symlink and only then activates update.json in the repository. All state changes
# are serialized with flock; a failure leaves the previous distribution active.
# No GitHub token, URL, signature or JWT is ever written to the journal.
set -euo pipefail

BASE="${C0RE_DIST_BASE:-/var/lib/c0re-distribution}"
REPO_DIR="${C0RE_REPO_DIR:-/home/ham3l/Projects/C0reStream}"
SDK="${C0RE_SDK:-/tmp/opencode/toolchain/sdk/build-tools/37.0.0}"
API="https://api.github.com/repos/klatschanstalt/C0reStream"
RELEASE_DOWNLOAD="https://github.com/klatschanstalt/C0reStream/releases/download"
PUBLIC_BASE="https://stream.c0re.pw/distribution"
EXPECTED_CERT="65fb26b0f617583228ea8813080fa7e2f8f6421022d8d279f928c726ea6e8543"
EXPECTED_PACKAGE="pw.c0re.stream"
LOCK="$BASE/state/distribution.lock"
ACTIVE_FILE="$BASE/state/active.json"
STATE_FILE="$BASE/state/last-result.json"
APKSIGNER="$SDK/apksigner"
AAPT="$SDK/aapt"

log() { printf '%s c0re-distribution: %s\n' "$(date -u +%FT%TZ)" "$*"; }

# Compact, secret-free result file for monitoring.
write_state() {
    local status="$1" action="$2" detail="$3"
    python3 - "$STATE_FILE" "$status" "$action" "$detail" \
        "$ACTIVE_VERSION" "$ACTIVE_CODE" "$ACTIVE_SHA" <<'PY'
import datetime, json, os, sys
p, status, action, detail, version, code, sha = sys.argv[1:8]
obj = {
    "ts": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "status": status,
    "action": action,
    "detail": detail,
    "activeVersion": version,
    "activeVersionCode": code,
    "activeSha256": sha,
}
with open(p + ".new", "w") as f:
    f.write(json.dumps(obj, separators=(",", ":")) + "\n")
os.replace(p + ".new", p)
PY
}

fail() {
    log "ERROR: $1"
    write_state error failed "$1"
    exit 1
}

# Ensure repository update.json matches the active, publicly verified metadata.
# Use an isolated worktree at origin/main: never touch a dirty checkout, never
# force push, and never publish a tag or artifact from this service.
sync_repo_metadata() {
    local desired="$1" wt="$BASE/staging/git-meta"
    # Clean a worktree left behind by an interrupted previous run.
    git -C "$REPO_DIR" worktree remove --force "$wt" >/dev/null 2>&1 || true
    rm -rf "$wt"
    if ! git -C "$REPO_DIR" fetch --quiet origin main 2>"$tmp/curl.err"; then
        return 1
    fi
    git -C "$REPO_DIR" worktree add --quiet --detach "$wt" origin/main >/dev/null 2>&1 || return 1
    if python3 - "$wt/update.json" "$desired" 2>"$tmp/curl.err" <<'PY'
import json, sys
a, b = (json.load(open(p)) for p in sys.argv[1:3])
keys = ("latestVersion", "apkUrl", "sha256", "notes", "notes_de", "notes_en", "notes_fr")
raise SystemExit(0 if all(a.get(k) == b.get(k) for k in keys) else 1)
PY
    then
        git -C "$REPO_DIR" worktree remove --force "$wt" >/dev/null 2>&1 || true
        log "repository metadata already current"
        return 0
    fi
    cp "$desired" "$wt/update.json"
    git -C "$wt" add update.json
    git -C "$wt" -c user.name="C0reStream Release" \
        -c user.email="release@c0re.stream" \
        commit -q -m "release: publish $ACTIVE_VERSION update metadata [skip ci]" || {
            git -C "$REPO_DIR" worktree remove --force "$wt" >/dev/null 2>&1 || true
            return 0
        }
    git -C "$wt" push --quiet origin HEAD:main 2>"$tmp/curl.err" || {
        git -C "$REPO_DIR" worktree remove --force "$wt" >/dev/null 2>&1 || true
        return 1
    }
    git -C "$REPO_DIR" worktree remove --force "$wt" >/dev/null 2>&1 || true
    log "repository metadata activated for version $ACTIVE_VERSION"
    return 0
}

# Serialize runs. A second trigger simply exits; the next timer tick retries.
exec 9>"$LOCK"
flock -n 9 || { log "another run holds the lock; exiting"; exit 0; }

tmp=$(mktemp -d "${TMPDIR:-/tmp}/c0re-distribution.XXXXXX")
trap 'rm -rf "$tmp"' EXIT

# Current active distribution (seeded once during provisioning).
ACTIVE_VERSION=$(python3 -c "import json;print(json.load(open('$ACTIVE_FILE'))['version'])")
ACTIVE_CODE=$(python3 -c "import json;print(json.load(open('$ACTIVE_FILE'))['versionCode'])")
ACTIVE_SHA=$(python3 -c "import json;print(json.load(open('$ACTIVE_FILE'))['sha256'])")
[[ "$ACTIVE_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$ACTIVE_CODE" =~ ^[0-9]+$ \
   && "$ACTIVE_SHA" =~ ^[0-9a-f]{64}$ ]] || fail "invalid active state file"
log "start active=$ACTIVE_VERSION code=$ACTIVE_CODE"

# Public GitHub API only: no token is read, stored or logged.
if ! curl -fsS --max-time 30 "$API/releases/latest" -o "$tmp/release.json" 2>"$tmp/curl.err"; then
    log "release API unavailable; no changes made"
    write_state skip none "release-api-unavailable"
    exit 0
fi
if ! python3 - "$tmp/release.json" "$tmp/meta.env" <<'PY'
import json, re, sys
d = json.load(open(sys.argv[1]))
if d.get("draft") or d.get("prerelease"):
    sys.exit(1)
tag = d.get("tag_name", "")
m = re.fullmatch(r"v?(\d+)\.(\d+)\.(\d+)", tag)
if not m:
    sys.exit(2)
assets = {a["name"]: a for a in d.get("assets", [])}
required = ("C0reStream.apk", "SHA256SUMS", "resources.json", "resources.sig")
if any(name not in assets for name in required):
    sys.exit(3)
with open(sys.argv[2], "w") as f:
    f.write("TAG=%s\nVERSION=%s\n" % (tag, ".".join(m.groups())))
PY
then
    log "no usable stable release or required assets missing; no changes made"
    write_state skip none "no-stable-release"
    exit 0
fi
# shellcheck source=/dev/null
source "$tmp/meta.env"

newer=$(python3 - "$ACTIVE_VERSION" "$VERSION" <<'PY'
import sys
def p(s): return tuple(int(x) for x in s.split("."))
print("1" if p(sys.argv[2]) > p(sys.argv[1]) else "0")
PY
)
if [ "$newer" != "1" ]; then
    log "no newer stable release (active=$ACTIVE_VERSION latest=$VERSION)"
    sync_repo_metadata "$(readlink -f "$BASE/current")/update.json" || \
        log "WARNING: repository metadata sync pending"
    write_state ok none "no-new-release"
    exit 0
fi

target="$BASE/versions/$VERSION"
if [ -e "$target" ]; then
    fail "version directory already exists; refusing overwrite"
fi

stage="$BASE/staging/$VERSION"
rm -rf "$stage"
mkdir -p "$stage"
log "staging new version $VERSION"

# Download only the four published release assets. curl diagnostics are discarded
# so no URL can reach the journal.
fetch_asset() {
    local name="$1" dest="$2" code
    code=$(curl -sSL --max-time 180 -o "$dest" -w '%{http_code}' \
        "$RELEASE_DOWNLOAD/$TAG/$name" 2>"$tmp/curl.err") || return 1
    [ "$code" = 200 ] && [ -s "$dest" ]
}
for asset in C0reStream.apk SHA256SUMS resources.json resources.sig; do
    fetch_asset "$asset" "$stage/$asset" || fail "asset download failed ($asset)"
done

# SHA-256 from the published SHA256SUMS, verified against the downloaded APK.
( cd "$stage" && sha256sum -c SHA256SUMS >/dev/null ) || fail "SHA256SUMS verification failed"
expected_sha=$(awk '$2=="C0reStream.apk" {print $1; exit}' "$stage/SHA256SUMS")
[[ "$expected_sha" =~ ^[0-9a-f]{64}$ ]] || fail "invalid APK checksum in SHA256SUMS"

# Signing certificate, package and version metadata.
actual_cert=$("$APKSIGNER" verify --print-certs "$stage/C0reStream.apk" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $NF; exit}' | tr -d '[:space:]' | tr 'A-Z' 'a-z')
[ "$actual_cert" = "$EXPECTED_CERT" ] || fail "APK certificate fingerprint mismatch"
"$AAPT" dump badging "$stage/C0reStream.apk" > "$tmp/badging.txt" || fail "aapt badging failed"
grep -q "name='$EXPECTED_PACKAGE'" "$tmp/badging.txt" || fail "package name mismatch"
apk_code=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" "$tmp/badging.txt" | head -n1)
apk_name=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" "$tmp/badging.txt" | head -n1)
[ "$apk_name" = "$VERSION" ] || fail "APK versionName does not match tag"
[ -n "$apk_code" ] || fail "APK versionCode missing"
if [ "$(python3 -c "print(1 if $apk_code > $ACTIVE_CODE else 0)")" != 1 ]; then
    fail "APK versionCode is not greater than active versionCode (downgrade/equal refused)"
fi

# Detached SHA256withRSA signature over the exact manifest bytes. The public
# certificate comes from the APK itself; the hex signature is decoded locally.
"$APKSIGNER" verify --print-certs-pem "$stage/C0reStream.apk" \
    | sed -n '/-----BEGIN CERTIFICATE-----/,$p' > "$tmp/cert.pem"
[ -s "$tmp/cert.pem" ] || fail "certificate PEM unavailable"
openssl x509 -in "$tmp/cert.pem" -pubkey -noout > "$tmp/pubkey.pem"
python3 - "$stage/resources.sig" "$tmp/sig.bin" <<'PY'
import sys
hexsig = open(sys.argv[1], "rb").read().strip()
assert len(hexsig) % 2 == 0 and all(c in b"0123456789abcdefABCDEF" for c in hexsig)
open(sys.argv[2], "wb").write(bytes.fromhex(hexsig.decode()))
PY
openssl dgst -sha256 -verify "$tmp/pubkey.pem" -signature "$tmp/sig.bin" \
    "$stage/resources.json" >/dev/null || fail "resource manifest signature invalid"

# Notes come from release-notes.json at the tag, the canonical source for notes.
if ! curl -fsS --max-time 30 "$API/contents/release-notes.json?ref=$TAG" \
        -o "$tmp/notes.b64" 2>"$tmp/curl.err"; then
    fail "release notes unavailable"
fi
python3 - "$tmp/notes.b64" "$stage/notes.json" <<'PY'
import base64, json, sys
payload = json.load(open(sys.argv[1]))
assert payload.get("encoding") == "base64"
open(sys.argv[2], "wb").write(base64.b64decode(payload["content"]))
PY
python3 - "$stage/notes.json" "$VERSION" "$PUBLIC_BASE/releases/$VERSION/C0reStream.apk" \
        "$expected_sha" "$stage/update.json.new" <<'PY'
import json, sys
notes = json.load(open(sys.argv[1]))
for key in ("notes_de", "notes_en", "notes_fr"):
    assert isinstance(notes.get(key), str) and notes[key].strip(), key
assert notes.get("version") == sys.argv[2]
data = {
    "latestVersion": sys.argv[2],
    "notes": notes["notes_de"],
    "notes_de": notes["notes_de"],
    "notes_en": notes["notes_en"],
    "notes_fr": notes["notes_fr"],
    "apkUrl": sys.argv[3],
    "sha256": sys.argv[4],
}
with open(sys.argv[5], "w") as f:
    print(json.dumps(data, ensure_ascii=False, indent=4), file=f)
PY

# Build the public layout in staging. Keep the previous update.json until the
# new APK is verified over HTTPS, so /distribution/update.json is never missing.
install_dir="$stage/install"
mkdir -p "$install_dir/releases/$VERSION" "$install_dir/resources"
cp "$stage/C0reStream.apk" "$install_dir/releases/$VERSION/C0reStream.apk"
cp "$stage/SHA256SUMS" "$install_dir/releases/$VERSION/SHA256SUMS"
cp "$stage/resources.json" "$install_dir/resources/resources.json"
cp "$stage/resources.sig" "$install_dir/resources/resources.sig"
if [ -f "$BASE/versions/$ACTIVE_VERSION/update.json" ]; then
    cp "$BASE/versions/$ACTIVE_VERSION/update.json" "$install_dir/update.json"
else
    cp "$stage/update.json.new" "$install_dir/update.json"
fi
chmod -R a+rX "$install_dir"

# HTTP 200 with zero redirects. Never follow redirects on the public endpoint.
http_ok() {
    local url="$1" out="$2" result code redirects attempt
    for attempt in 1 2 3 4 5; do
        result=$(curl -sS --max-time 60 -o "$out" -w '%{http_code} %{num_redirects}' \
            "$url" 2>"$tmp/curl.err") || { sleep 3; continue; }
        read -r code redirects <<< "$result"
        if [ "$code" = 200 ] && [ "$redirects" = 0 ]; then return 0; fi
        sleep 3
    done
    return 1
}

# Publish: rename the version dir, then switch the symlink atomically. The old
# version directory is kept for rollback and is never overwritten.
prev_target=$(readlink "$BASE/current" || true)
restore_previous() {
    if [ -n "$prev_target" ]; then
        ln -sfn "$prev_target" "$BASE/state/current.new"
        mv -T "$BASE/state/current.new" "$BASE/current"
    fi
}
mv -T "$install_dir" "$target" || fail "version directory could not be installed"
ln -sfn "$target" "$BASE/state/current.new"
mv -T "$BASE/state/current.new" "$BASE/current" || fail "distribution symlink switch failed"
log "installed version $VERSION; verifying public endpoints"

verify_public() {
    http_ok "$PUBLIC_BASE/releases/$VERSION/C0reStream.apk" "$tmp/public.apk" || return 1
    [ "$(sha256sum "$tmp/public.apk" | cut -d' ' -f1)" = "$expected_sha" ] || return 1
    cmp -s "$tmp/public.apk" "$stage/C0reStream.apk" || return 1
    http_ok "$PUBLIC_BASE/releases/$VERSION/SHA256SUMS" "$tmp/public.sums" || return 1
    cmp -s "$tmp/public.sums" "$stage/SHA256SUMS" || return 1
    http_ok "$PUBLIC_BASE/resources/resources.json" "$tmp/public.json" || return 1
    cmp -s "$tmp/public.json" "$stage/resources.json" || return 1
    http_ok "$PUBLIC_BASE/resources/resources.sig" "$tmp/public.sig" || return 1
    cmp -s "$tmp/public.sig" "$stage/resources.sig" || return 1
    return 0
}
if ! verify_public; then
    restore_previous
    fail "public endpoint verification failed; previous distribution restored"
fi

# Only now activate update.json in the public layout, atomically.
cp "$stage/update.json.new" "$target/update.json.new"
mv -T "$target/update.json.new" "$target/update.json"
if ! http_ok "$PUBLIC_BASE/update.json" "$tmp/public-update.json"; then
    restore_previous
    fail "public update metadata verification failed; previous distribution restored"
fi
python3 - "$tmp/public-update.json" "$VERSION" "$expected_sha" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
assert d["latestVersion"] == sys.argv[2] and d["sha256"] == sys.argv[3]
PY

# Promote the active marker only after every public check passed.
python3 - "$ACTIVE_FILE" "$VERSION" "$apk_code" "$expected_sha" <<'PY'
import json, os, sys
p, version, code, sha = sys.argv[1:5]
data = {"version": version, "versionCode": code, "sha256": sha}
with open(p + ".new", "w") as f:
    f.write(json.dumps(data, separators=(",", ":")) + "\n")
os.replace(p + ".new", p)
PY
ACTIVE_VERSION="$VERSION"
ACTIVE_CODE="$apk_code"
ACTIVE_SHA="$expected_sha"
log "public verification passed for version $VERSION"

if ! sync_repo_metadata "$target/update.json"; then
    log "WARNING: repository metadata sync pending; distribution remains active"
    write_state ok installed "repository-metadata-sync-pending"
else
    write_state ok installed "public-verified"
fi
log "done active=$ACTIVE_VERSION"
