# AshGo — Current State Audit

> **Historical snapshot (2026-10-13).** This describes v1, before the v2 redesign, and is kept for the record. For the current status, see the dated re-checks at the top of [SECURITY_PRIVACY_AUDIT.md](SECURITY_PRIVACY_AUDIT.md).

Audit date: 2026-10-13 · Branch audited: `main` @ `fc1070e` (versionCode 45, versionName 1.3.32)
Method: every Kotlin file, manifest, Gradle file and XML resource was read in full; git history was searched; a baseline build, unit-test run and lint run were executed locally. No production code was modified.

Legend for evidence: **[code]** confirmed by reading source · **[build]** confirmed by running Gradle · **[history]** confirmed from git log · **[runtime?]** needs a device to confirm.

---

## 1. Working tree and repository

| Item | Finding |
|---|---|
| Branch | `main`, up to date with `origin/main`. v2 work happens on new branch `v2`. |
| Uncommitted changes | `.idea/deploymentTargetSelector.xml` (IDE noise, left untouched). Untracked: `AshGo-releases-clone/`, `ashgo-releases/` (clones of the private releases repo containing a 33 MB `app-debug.apk`), `ashgov2prompt.md`. None were modified. |
| Remotes | `origin` → `varad-oss/AshGo` (**public** on GitHub, verified via the GitHub API); `releases` → `varad-oss/AshGo-releases` (returns 404 unauthenticated, i.e. private or deleted). |
| Stray files tracked in git | `test.js` (Node admin script that names the production database URL), `patch_receiver.py`, `patch_tracking.py` (one-off AI patch scripts), `.kotlin/errors/*.log` (compiler crash log), `AndroidManifest.xml.bak` (untracked but present). |
| Secrets scan | No GitHub tokens, private keys or service-account JSON in the working tree or in any commit (pattern scan of `git log -p --all`). `app/google-services.json` exists locally and is correctly git-ignored. Release-clone remotes contain no embedded credentials. |

## 2. Build configuration

| Setting | Value | Note |
|---|---|---|
| applicationId / namespace | `com.varad.unstoppableash` | Must be preserved for in-place upgrades. |
| minSdk / targetSdk / compileSdk | 26 / 34 / 37 | targetSdk 34 avoids Android 15's `dataSync` 6-hour limit; raising it requires reworking `ReceiverService`. |
| AGP / Gradle / Kotlin | 9.3.2 / 9.5.0 / 2.2.10 | Current toolchain. |
| JDK | No system JDK. Builds work with Android Studio's bundled JBR (OpenJDK 25). |
| Release build | `optimization { enable = false }`; **no signing config**. What is distributed is `app-debug.apk` (debuggable, signed with this Mac's `~/.android/debug.keystore`). |
| Version catalog | Mixed: some deps in `libs.versions.toml`, three hardcoded in `app/build.gradle.kts` (osmdroid, navigation, icons-extended). |

### Dependencies

| Dependency | Used? | Note |
|---|---|---|
| Compose BOM 2026.02.01, Material3, activity-compose 1.8.0, core-ktx 1.10.1, lifecycle-runtime-ktx 2.6.1 | Yes | AndroidX libs are several minor versions behind (lint `GradleDependency`). No `lifecycle-viewmodel-compose`: **there are no ViewModels**. |
| navigation-compose 2.7.7 | Yes | String routes. |
| material-icons-extended | Yes | Large artifact; R8 is disabled, so the APK keeps all icons (debug APK is 33 MB). |
| firebase-bom 33.1.2 → database, auth | Yes | Auth is only used for `signInAnonymously()` and its result is ignored. |
| firebase-analytics | **No** (0 references) | Adds tracking with no product value. Remove. |
| firebase-storage | **No** (0 references) | Cloud Storage now requires the Blaze plan. Remove. |
| coil-compose 2.6.0 | Marginal | Only renders a legacy `imageUrl` field that nothing writes. |
| play-services-location 21.3.0 | Yes | Fused location. |
| kotlinx-coroutines-play-services | Barely | No `await()` usage found. |
| osmdroid-android 6.1.18 | Yes | **Upstream archived 2024-11-20** (GitHub API: `archived: true`). Works today; no further fixes. |

## 3. Baseline build and test status **[build]**

Command: `./gradlew assembleDebug testDebugUnitTest lintDebug` with `JAVA_HOME` set to the Android Studio JBR.

| Task | Result |
|---|---|
| `compileDebugKotlin`, `assembleDebug` | ✅ Pass (UP-TO-DATE against current sources) |
| `testDebugUnitTest` | ✅ Pass. The only test is the template `ExampleUnitTest` (2 + 2 = 4). **Effective test coverage: zero.** |
| `lintDebug` | ❌ Fail. 1 error, 89 warnings, 6 hints. |

Lint error: `SEND_SMS` declared without `<uses-feature android:name="android.hardware.telephony" android:required="false"/>` (`PermissionImpliesUnsupportedChromeOsHardware`).
Top warning categories: UseKtx 12 · IconLocation 11 · UnusedResources 10 · IconLauncherShape 10 · GradleDependency 10 · ObsoleteSdkInt 8 · DefaultLocale 6 · OldTargetApi 1 · HardcodedText 1.
Unused resources include `brick_wall.jpg` (848 KB), `retro_tv.jpg` (632 KB), `raw/fake_call.wav`, and template colours.

Device resources available on this machine: two AVDs (`Pixel_8_Pro`, `Pixel_8_Pro_2`); no physical device was attached.

## 4. Source inventory

| File | Lines | Responsibility (actual) |
|---|---|---|
| `MainActivity.kt` | 1458 | Activity, startup cleanup that deletes data, the updater dialog, nav graph, role selection, **the entire traveler dashboard** (settings, fake-call player, emergency contacts, contact picker, SOS button, unread badge, Thunder listeners), the **receiver dashboard** (geocoding, listeners, toggles). |
| `ui/ReceiverScreen.kt` | 587 | Receiver map + chat (3 modes via string), map long-press destination writes, arrival toasts, signal-lost timer, `ChatMessage` model, `ChatBubble`. |
| `ui/TravelerChatScreen.kt` | 316 | Traveler chat, camera capture, Base64 encoding inside the UI callback. |
| `ui/AboutScreen.kt` | 254 | Personal "about us" page with 9 bundled personal photos on drawn ropes. |
| `ui/AppDrawer.kt` | 204 | Profile photo (Base64 upload), display name editing, Help/About links. |
| `ui/HelpScreen.kt` | 57 | Two lines of personal text. No help content. |
| `TrackingService.kt` | 372 | Location FGS + destination geofence + Thunder/chat/custom-notification listeners + **SOS SMS dispatch**. |
| `ReceiverService.kt` | 240 | `dataSync` FGS that listens for Thunder, chat, location, SOS; 15 s dead-signal polling. |
| `FirebaseRepository.kt` | 130 | Path constants and helpers. **Never referenced. Dead code.** |
| `AutoUpdater.kt` | 94 | Downloads an APK from a URL in RTDB, with an optional GitHub token, then launches the installer. |
| `SosAccessibilityService.kt` | 65 | Hold Volume-Down for 3 s → SOS. |
| `SosWidgetProvider.kt` | 51 | One-tap home-screen SOS widget. |
| `SoundUtil.kt` | 49 | Forces alarm stream to max volume and plays a tone. |
| `AshGoApplication.kt` | 19 | Enables RTDB disk persistence and `keepSynced` on `chat`, `buzz`, `tracking`. |
| `ChatState.kt` | 5 | Global mutable flag, no longer read for any decision. |
| `ui/theme/*` | 114 | Dark-only scheme (indigo accent); the "light" scheme is also dark. Typography only overrides `bodyLarge`. |

## 5. Current architecture (as-built)

```
Composable screens ──(direct FirebaseDatabase.getInstance() calls, 40+ sites)──► RTDB
      │                                                                          ▲
      ├─ SharedPreferences "AshGoPrefs" (role, toggles, contacts, voices)        │
      └─ start/stop services by Intent                                          │
TrackingService (location FGS) ──writes current_trip every 2–5 s; reads alerts──┤
ReceiverService (dataSync FGS) ──reads everything, raises notifications─────────┘
Widget / Accessibility / notification action ──write alerts/push──► RTDB
```

There is no ViewModel, repository in use, domain model, dependency injection or test seam. Every screen opens its own Firebase listeners. The same data (`alerts`, `buzz/*`, `chat`, `tracking/current_trip`) is listened to from 2–4 places at once, each with its own interpretation.

### Database schema (inferred from code; the deployed rules are not in the repo)

```
/tracking/current_trip   {latitude, longitude, timestamp}           overwritten every fix
/tracking/vehicle_info   "string"                                   written on every keystroke
/tracking/destination    {latitude, longitude}
/tracking/arrived        timestamp
/chat/{pushId}           {text, isFromTraveler, timestamp, imageBase64?, imageUrl?}
/alerts/{pushId}         {isSosActive, timestamp}                   append-only event log
/buzz/traveler|receiver  timestamp
/notifications/traveler|receiver {title, message, timestamp}        read only; nothing in the app writes it
/users/traveler|receiver {profilePicBase64, displayName}
/app_config              {latest_version_code, apk_url, github_token}
```

All timestamps are **client clock** values (`System.currentTimeMillis()`). No `ServerValue.TIMESTAMP` is used anywhere.

## 6. Screen inventory

| Route | Who | Content |
|---|---|---|
| `role_selection` | first run | "I am the Traveler" / "I am the Receiver". Choice is stored only in SharedPreferences. |
| `traveler_dashboard` | traveler | Vehicle registration field, location-sharing switch, fake-call sheet and player, emergency-contacts sheet, chat button with unread badge, SOS button (long-press). |
| `traveler_chat` | traveler | Chat, selfie prompt, Thunder button. |
| `receiver_home` | receiver | Listening switch, info card (vehicle, address, last-updated time), "View Live Location", "Chat With Her". |
| `receiver_map_only` / `receiver_chat_only` / `receiver_map` (legacy) | receiver | One composable in three modes. |
| `help`, `about` | both | Personal pages. |

## 7. Code-quality findings

Severity: 🔴 high · 🟠 medium · 🟡 low. All are **[code]** unless marked otherwise.

### Correctness and reliability
1. 🔴 **Notification channel `tracking_channel` is never created**, and git history confirms no version ever created it. Eight call sites post to it: chat notifications (both services), the traveler's Thunder notification, all "custom" notifications, and both Thunder notifications in `DashboardScreen`. From Android 8 (every supported device, since minSdk is 26), notifications posted to a missing channel are not shown. The sound in `SoundUtil` still plays, which is likely why this went unnoticed. **[runtime?]** Confirm with logcat ("No Channel found").
2. 🔴 **SOS SMS is sent only if `TrackingService` is running on the traveler's phone.** If location sharing is off, triggering SOS from the button, widget or volume key writes to Firebase and shows "SOS TRIGGERED", but no SMS is ever sent. The user gets no indication.
3. 🔴 **SOS SMS can contain coordinates 0,0.** `lastKnownLat/Lng` default to `0.0` and are only set after the first fix of the current service instance.
4. 🔴 **SOS SMS probably fails on many devices.** The message contains emoji, so it is encoded as UCS-2, where a single SMS holds 70 characters. The message is about 105 characters and is sent with `sendTextMessage` (single-part), with no `sentIntent`. Failures are invisible. **[runtime?]**
5. 🔴 **Receiver sees a fake location.** `ReceiverScreen` initialises the traveler's position to a hardcoded city coordinate and draws the "Live Location" marker there before any data arrives.
6. 🔴 **"Live Tracking Active" with a green dot is shown whenever data is less than 5 minutes stale, including when no data has ever been received** (`lastLocationTime == 0` → `isSignalLost = false`).
7. 🔴 **Deliberately stopping sharing causes a false "Connection lost" alarm.** `ACTION_STOP` leaves `current_trip` in place, and five minutes later `ReceiverService` plays an alarm-volume tone. There is no "paused" state in the data model.
8. 🟠 **SOS state disagrees between screens.** The traveler dashboard treats the latest alert as active forever. The receiver screens and service expire it after 15 minutes. `TrackingService` only acts within 60 seconds.
9. 🟠 **Clock-skew dependence.** Thunder fires only if `now − ts < 10 s` using the *other* phone's clock. A commit message ("Fix thunder button clock skew bug") shows this has already bitten. Cleanup and freshness use client clocks too.
10. 🟠 **Location restart after swipe-away is likely blocked on Android 14.** `onTaskRemoved` schedules an inexact `AlarmManager` restart of a `location` FGS from the background. Inexact alarms are not an exemption from FGS background-start rules, and while-in-use location cannot be accessed by an FGS created in the background without `ACCESS_BACKGROUND_LOCATION` (not declared). **[runtime?]**
11. 🟠 **Arrival is detected only while the receiver's map screen is open.** `ReceiverService` doesn't listen to `tracking/arrived`.
12. 🟠 **Receiver map recentres on every recomposition** (`animateTo` in `update`), so the user cannot pan or zoom away from the marker.
13. 🟠 **Listener leaks.** The alert listener in `DashboardScreen` (`LaunchedEffect`) and the users listener in `AppDrawer` are never removed, so each navigation adds another.
14. 🟠 **Duplicate handling.** Thunder is handled by both a composable listener and the service, so the sound and notification fire twice when the app is open.
15. 🟠 **Unbounded listeners.** Both services attach `ChildEventListener` to the whole `chat` node with no limit, and `AshGoApplication` calls `keepSynced("chat")`, which mirrors every message and every Base64 photo onto both phones.
16. 🟠 **Profile photos are uploaded at full resolution.** `AppDrawer` compresses the original gallery bitmap at JPEG quality 40 with no downscale, which can produce multi-megabyte Base64 strings. It then decodes the bitmap on every recomposition without `remember`, causing jank and an OOM risk.
17. 🟠 **Startup cleanup runs on both phones on every launch.** It orders by `timestamp` with no `.indexOn` evidence, deletes children one by one, and uses client clocks.
18. 🟡 **Accidental SOS paths.** The widget fires with one tap, with no confirmation or feedback. Holding Volume-Down for 3 seconds is also how people normally lower or mute volume. The lock-screen notification has a one-tap "TRIGGER SOS" action.
19. 🟡 **The widget writes to Firebase from `onReceive` without `goAsync()`**, so the process may be killed before the write is sent.
20. 🟡 **`SoundUtil` permanently sets the alarm stream to maximum volume** and never restores it.
21. 🟡 **Vehicle registration is written to Firebase on every keystroke.**
22. 🟡 **The fake-call feature is audio playback, not a simulated incoming call.** The README overstates it.
23. 🟡 **Empty `catch {}` blocks and empty `onCancelled` handlers are used throughout.** Permission-denied or quota errors are invisible.

### Structure
- Business logic sits in composables: Firebase writes, SMS-adjacent state, JSON (de)serialisation of contacts, media encoding, contact queries.
- A 1458-line `MainActivity.kt` holds three unrelated screens.
- There are three sources of truth for "is SOS active", two for unread counts, and SharedPreferences values are read directly in composition.
- Hardcoded personal names, nicknames and romantic copy appear in shared logic, notifications, SMS text and dialogs.
- `FirebaseRepository` and `ChatState` are dead code. There is a Google Maps API-key placeholder in the manifest, but the app uses no Google Maps.
- No accessibility work: many `contentDescription`s are generic or decorative, hardcoded `sp` sizes ignore the type scale, and colour alone carries state.

## 8. What works (credited)

- Text chat round-trip, Thunder sound, location upload while the app is foregrounded, emergency-contact storage, and the map display are plausibly functional **[code]**. They need device confirmation.
- `PendingIntent`s use `FLAG_IMMUTABLE`; `FileProvider` is not exported; release clones contain no credentials.
- The `alerts` design is append-only, which preserves an event history that v2 can build an SOS state machine on.
