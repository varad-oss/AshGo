# Releasing an AshGo update

After the one-time setup, every installed AshGo shows **"Update available"** the next time it is opened. Tapping
**Update** downloads the APK, checks its SHA-256, and opens Android's installer.

## One-time setup
1. Phase-2 rules are live and the legacy data is deleted (`DEVICE_VERIFICATION.md` §8).
2. Make `varad-oss/AshGo-releases` public. It holds APKs only: no source, no keys.
3. Sign the Firebase CLI in once, with the Google account that owns the project:
   `cd firebase && npx firebase login`
4. The GitHub CLI is already signed in (`gh auth status`).
5. Install a build that includes the open-time check by hand, once per phone. v1 can't update itself to it.

## Every release
```
scripts/publish-release.sh "One line on what changed"
```
The script:
1. Bumps the version (patch + 1).
2. Runs the unit tests and builds the release APK.
3. Creates a GitHub release with the APK.
4. Writes `app_config` (version, URL, SHA-256, size, notes).
5. Commits the version bump.

If any step fails, it puts the version back.

The APK is signed with `~/.android/debug.keystore`. Keep that file backed up: a different key can never update an installed AshGo.
