# AshGo 2.0 — release readiness (2026-10-14)

**Build:** 2.0.0 (versionCode 46), signed with the same key as v1, R8 on. **Budget:** ₹0, on the Firebase Spark plan only (RTDB + Auth). No Functions, Storage, FCM server, Blaze or paid APIs.

## Status

| Area | State | Evidence |
|---|---|---|
| Unit tests | ✅ all pass | `./gradlew :app:testDebugUnitTest` |
| UI (instrumented) tests | ✅ all pass on the API 34 emulator | `./gradlew :app:connectedDebugAndroidTest` |
| Rules tests | ✅ 59/59 (phase 1 + phase 2) | `cd firebase && npm test` with the emulator |
| Lint | ✅ 0 errors (release and debug) | `./gradlew :app:lintDebug` |
| Release build | ✅ R8 build launches to onboarding on the emulator | `./gradlew :app:assembleRelease` |
| Secrets | ✅ no tokens, keys or keystores in the repo or the APK; the updater needs no token | grep + `UpdatePolicy` |
| Personal media | ✅ none bundled: v1 photos and recordings removed, and Our page / fake-call voices stay on the device | cutover commit |
| Real devices | ⏳ owner, see `DEVICE_VERIFICATION.md` | — |

## What ships
Pairing via Google, honest presence, chat with receipts and photos, map sharing with pauses, trips with arrival and overdue alerts, SOS (partner alert + SMS with delivery reports), lock-screen partner SOS, notifications without a push server (an "AshGo connected" foreground service), Reliability checks, Settings (profile, notifications, privacy & retention, updates, help, licences), fake call, Our page, home-screen widget and Quick Settings tile.

## Known limits (honest)
- **Notifications** rely on the connected service. On phones that kill background apps aggressively, Reliability shows the fix, but delivery is not guaranteed the way FCM would be.
- **Fake call** rings only while AshGo is open (Android blocks background activity starts).
- **SMS** costs are the user's normal carrier rates. An SMS shows **Delivered** only when the network reports it, and some carriers never do.
- **Git history** still contains the old bundled personal photos and recording. Removing them needs a history rewrite and force-push, which is the owner's decision.

## Owner steps, in order
1. Back up the DB JSON, the current rules and `~/.android/debug.keystore`.
2. Install 2.0 on both phones and run `DEVICE_VERIFICATION.md` §1–7.
3. Deploy `firebase/database.rules.phase2.json`, then delete the legacy nodes.
4. Make the releases repo public, publish the APK and set `app_config`.
5. Revoke the old GitHub token.
