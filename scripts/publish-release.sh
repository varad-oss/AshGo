#!/usr/bin/env bash
# Publishes a new AshGo build so every installed copy shows "Update available" on its next open.
#   scripts/publish-release.sh "What changed, in one line"
# Steps: checks → bump version → tests + release build → GitHub release (public APK-only repo) → app_config in
# Firebase (version, URL, SHA-256) → commit the version bump. Nothing secret is used or stored: the APK is public,
# and the app verifies the SHA-256 before Android's installer ever sees the file.
set -euo pipefail

NOTES="${1:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="varad-oss/AshGo-releases"
PROJECT="ash-safar-tracker-1787591514"
GRADLE_FILE="$ROOT/app/build.gradle.kts"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

BUMPED=0
die() { echo "✗ $*" >&2; [ "$BUMPED" = 1 ] && git checkout -- "$GRADLE_FILE" && echo "  (version put back)" >&2; exit 1; }
[ -n "$NOTES" ] || die 'Give one line of release notes:  scripts/publish-release.sh "Fixed X"'
cd "$ROOT"

echo "→ Checking prerequisites"
git diff --quiet HEAD -- app || die "app/ has uncommitted changes; commit them first so the release matches git."
gh auth status >/dev/null 2>&1 || die "GitHub CLI isn't signed in: run  gh auth login"
[ "$(gh repo view "$REPO" --json visibility -q .visibility)" = "PUBLIC" ] \
  || die "$REPO is private, so phones can't download from it. Make it public only after the phase-2 rules are live (DEVICE_VERIFICATION §8)."
(cd firebase && npx --no-install firebase projects:list >/dev/null 2>&1) || die "Firebase CLI isn't signed in: run  cd firebase && npx firebase login"

CODE=$(sed -nE 's/^ *versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")
NAME=$(sed -nE 's/^ *versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")
NEW_CODE=$((CODE + 1))
NEW_NAME="$(echo "$NAME" | awk -F. '{ printf "%d.%d.%d", $1, $2, $3 + 1 }')"
echo "→ Version $NAME ($CODE) → $NEW_NAME ($NEW_CODE)"
sed -i '' -E "s/versionCode = $CODE/versionCode = $NEW_CODE/; s/versionName = \"$NAME\"/versionName = \"$NEW_NAME\"/" "$GRADLE_FILE"
BUMPED=1
trap 'echo "✗ Failed: putting the version back"; git checkout -- "$GRADLE_FILE"' ERR

echo "→ Tests and release build"
./gradlew -q :app:testDebugUnitTest :app:assembleRelease

mkdir -p dist
APK="dist/AshGo-$NEW_NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
SIZE=$(stat -f%z "$APK")

echo "→ GitHub release v$NEW_NAME"
gh release create "v$NEW_NAME" "$APK" --repo "$REPO" --title "AshGo $NEW_NAME" --notes "$NOTES"
URL="https://github.com/$REPO/releases/download/v$NEW_NAME/AshGo-$NEW_NAME.apk"
[ "$(curl -sIL -o /dev/null -w '%{http_code}' "$URL")" = "200" ] || die "The APK isn't downloadable at $URL"

echo "→ Telling installed apps (app_config)"
python3 - "$NEW_CODE" "$NEW_NAME" "$URL" "$SHA" "$SIZE" "$NOTES" > dist/app_config.json <<'PY'
import json, sys
code, name, url, sha, size, notes = sys.argv[1:]
print(json.dumps({"latest_version_code": int(code), "latest_version_name": name, "apk_url": url,
                  "sha256": sha, "apk_size": int(size), "notes": notes[:500]}))
PY
(cd firebase && npx --no-install firebase database:update /app_config ../dist/app_config.json --project "$PROJECT" --force)

trap - ERR
BUMPED=0
git add "$GRADLE_FILE"
git commit -qm "release: AshGo $NEW_NAME ($NEW_CODE)"
git push -q
echo "✓ Published AshGo $NEW_NAME. Phones show 'Update available' the next time AshGo opens."
