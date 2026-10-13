# AshGo — Security & Privacy Audit

Audit date: 2026-10-13. Scope: source on `main` @ `fc1070e`, git history, the public GitHub repository, and one read-only probe of the production Realtime Database.

**Evidence labels**
- **CONFIRMED**: proven by code, history, or a direct observation.
- **LIKELY**: strongly implied by code, but depends on backend state we could not see.
- **UNVERIFIED**: plausible, and needs access we do not have.

No secret values are reproduced in this document.

---

## 0. What we could and could not see

| Item | Status |
|---|---|
| Deployed RTDB security rules | **Not in the repository; not retrieved.** The Firebase CLI is not installed. A `firebase-tools` login config exists on this Mac, but reading the rules would use the owner's credentials. The owner should run the command in §7. |
| Unauthenticated read of production RTDB | **Probed, read-only, `?shallow=true`** (key names only, no content). Result: `HTTP 401 Permission denied` for `/` and `/app_config`. **The database is not world-readable.** |
| Authenticated (anonymous) read | **Not tested.** Testing it would mean creating an anonymous account in the owner's project. |
| Cloud Storage rules | Not applicable. Storage is unused, and it now requires the Blaze plan. |

## 1. Findings ranked

### S1 🔴 The authorization model cannot separate the couple from anyone else (CONFIRMED 2026-10-14 from the owner's deployed rules: root `.read`/`.write` = `auth != null`)
- The app's only authentication is `FirebaseAuth.signInAnonymously()`, and its result is ignored. **[CONFIRMED]**
- All data lives at fixed global paths (`/chat`, `/tracking`, `/alerts`, `/users/traveler`, …), with no user or pair ID in any path. **[CONFIRMED]**
- "Traveler" and "Receiver" are a client-side SharedPreferences choice, and `isFromTraveler` is a client-asserted boolean. **[CONFIRMED]**
- The unauthenticated probe was rejected, so the rules almost certainly say `auth != null`. Under that rule, **any device that can perform anonymous sign-in can read and write everything.** Anonymous sign-in only needs the Firebase API key and app ID, and both are embedded in every APK. **[LIKELY]**
- Consequences, if LIKELY holds:
  - Anyone with an APK copy, or the API key extracted from one, can read live location, chat, photos, emergency state, and the updater token. They can also forge or cancel SOS, impersonate either person in chat, set a fake destination, or wipe data.
  - Reinstalling the app or clearing its data creates a new anonymous UID, and nothing stops it from working. That shows UID is not used for authorization at all.
- **Fix direction:** add real per-person identity, bind exactly two UIDs into a `pair`, and write rules that check membership. See ARCHITECTURE_PROPOSAL (pending).

### S10 🔴 `notifications` is world-readable and world-writable (CONFIRMED 2026-10-14 from deployed rules)
- The deployed rules give `notifications` `".read": true, ".write": true`, so anyone on the internet can write there **without the APK or any sign-in**.
- v1's `TrackingService` and `ReceiverService` show whatever title and message are written to `notifications/traveler|receiver`, and force the alarm stream to maximum volume.
- **Impact:**
  - A stranger can push arbitrary text to both phones, at full alarm volume. That is a phishing vector (e.g. "Update here: <link>").
  - A stranger can fill the 1 GB Spark storage quota, taking the app down.
- **Nothing in the app writes this node**, and the Firebase console and Admin SDK bypass rules anyway, so closing it breaks nothing.
- **Immediate fix (owner):** delete the `notifications` block from the rules, or deploy the tested phase-1 rules in `firebase/database.rules.json`.

### S2 🔴 A GitHub token is distributed through the database (CONFIRMED design, scope UNVERIFIED)
- `MainActivity` reads `app_config/github_token` and `AutoUpdater` sends it as `Authorization: token …` to download from the private releases repo. **[CONFIRMED]**
- Under S1, every authenticated client can read that token. Its scope is unknown. A classic token with `repo` scope would grant write access to **all** of the owner's repositories. **[UNVERIFIED scope]**
- **Immediate owner action, needing no code change:**
  1. Revoke the token in GitHub → Settings → Developer settings.
  2. Delete `app_config/github_token` from the database.
  3. If downloads are still wanted, publish APKs as **public** GitHub Release assets, or as assets of a dedicated public repo with no sensitive content, so that no token is needed.

### S3 🔴 Personal media and identifying data are in a public repository (CONFIRMED)
- `varad-oss/AshGo` is **public**. Its tracked files include:
  - 9 personal photographs (`res/drawable/photo_*.{jpg,png}`)
  - a personal voice recording (`res/raw/*fake_call.mp3`)
  - real first names and nicknames throughout the code
  - an "about us" narrative
  - `test.js`, which names the production database URL
- Even after deletion, these remain in git history and in any forks or clones.
- **Owner decision required** (cannot be fixed by a commit alone):
  - **Option A:** make the repo private. This is the simplest choice and is free on GitHub.
  - **Option B:** keep it public, but move personal assets into a git-ignored local folder, and rewrite history with `git filter-repo`. Rewriting history is destructive and must be done by the owner.

### S4 🟠 SOS can be forged, cancelled or suppressed by any client (CONFIRMED in code)
- SOS is any push to `/alerts` with `isSosActive: true`, and cancellation is any push with `false`. There is no actor field, no PIN, and no server-side rule on who may cancel.
- The exported `SosWidgetProvider` accepts the custom `ACTION_WIDGET_SOS` broadcast from **any app on the device**, so a local app can trigger a false SOS. The receiver must stay exported for widget updates, but SOS should go through an explicit, confirmation-gated `PendingIntent` to a non-exported component.

### S5 🟠 Updater integrity rests entirely on Android's signature check (CONFIRMED)
- The APK URL comes from the database, and the download has no hash, size cap or content-type validation. A cleartext URL would also be accepted, since `usesCleartextTraffic` is not disabled.
- Android refuses to install an update signed by a different key, so a malicious APK cannot replace AshGo **as an update**. However, a hostile `apk_url` could still deliver a *different* package that the user is prompted to install.
- Releases are **debug-signed** with this Mac's `~/.android/debug.keystore`. If that file is lost, no future update can be installed over existing installs. Debuggable builds also let anyone with USB access run `run-as` and read app data.
- **Fix direction:**
  - Create a release keystore and back it up offline.
  - Ship release builds.
  - Allow only `https://github.com/<owner>/<repo>/releases/download/...` URLs.
  - Verify SHA-256 against a value published alongside the version code.
  - Show size and version before downloading.
  - Keep the user-confirmed system installer.

### S6 🟠 Over-broad or unexplained permissions (CONFIRMED)

| Permission | Problem |
|---|---|
| Location, SMS, contacts, notifications | All requested in one batch at first launch, with no explanation. |
| `READ_CONTACTS` | Needed only because the picker queries `Phone.CONTENT_URI`. Picking a phone row directly (`ACTION_PICK` + `Phone.CONTENT_TYPE`) needs no contacts permission. |
| Accessibility service | Declares `typeAllMask` events and `flagRequestFilterKeyEvents`, far more than a key shortcut needs. On Android 13+, sideloaded apps hit "Restricted settings" for accessibility, and Android 15 extends this to the SMS runtime permission. The user must allow them manually, and this is undocumented in the app. |
| `REQUEST_INSTALL_PACKAGES` | Justified only while an in-app updater exists. |

### S7 🟡 Data-at-rest and backup
- `allowBackup="true"` with empty rules. SharedPreferences (role, emergency contacts with names and numbers, custom voice URIs) can be included in Google cloud backup and device transfer. **[CONFIRMED]**
- The RTDB disk persistence cache keeps chat, including photos, unencrypted in app storage. On a debuggable build this is reachable over ADB. **[CONFIRMED]**
- Logs include phone numbers (`"Emergency SMS sent to $phone"`) and coordinates. **[CONFIRMED]**

### S8 🟡 Retention claims vs. behaviour
- **README claim:** "does not … store historical location data". This is **true for location**, since `current_trip` is overwritten. However, the last location persists indefinitely after sharing stops.
- **README claim:** "chats > 3 days" pruned. This is **best-effort**. Pruning runs only when one of the phones launches the app, and it uses that phone's clock. If neither app is opened, nothing is pruned.
- Profile photos, display names, vehicle registration, destination and `arrived` are never pruned.
- Retention is not access control. See S1.

### S9 🟡 No end-to-end encryption
Nothing is encrypted beyond TLS to Firebase. Google, as the processor, and anyone holding project-owner credentials can read all content. v2 must not claim E2EE unless it actually implements it. (E2EE is evaluated as a P2 opportunity in PRODUCT_DISCOVERY.)

## 2. Location privacy assessment

| Question | Current answer |
|---|---|
| Is it obvious when sharing is on? | Partly. There is an ongoing notification ("Location is being shared securely"), but "securely" is not supported by S1. |
| Can sharing be paused without leaving stale data? | **No.** Stopping leaves the last point visible to the other person forever, and it triggers a false alarm. |
| Is freshness shown truthfully? | **No.** See audit items 5–7: there is a fake initial marker, and "Live" is shown with no data. |
| Precise vs. approximate? | Always requests fine and coarse. If only approximate is granted, the location is not labelled as approximate. |
| Background permission? | Not requested. This is good for privacy, but service restarts from the background will fail on Android 14+. |
| Accuracy shared? | No. Accuracy, speed and battery are not sent (the dead `FirebaseRepository` had fields for them). |

## 3. SOS safety assessment

Current SOS has **no state machine**. Problems:

- The "SOS TRIGGERED" toast appears whether or not anything was sent.
- SMS dispatch depends on an unrelated toggle (location sharing).
- SMS results are never checked.
- SOS cannot be raised from the receiver role.
- Cancellation can be done by anyone.
- Different screens disagree about whether SOS is active.
- The receiver's alert is a tone at forced-max alarm volume plus a notification on an `IMPORTANCE_LOW` channel. On Android 8+ that notification has no heads-up and no sound of its own.

Required v2 states and their separate truths:

| State | Meaning |
|---|---|
| `Arming` | The user is holding or confirming. Nothing has been sent yet. |
| `Raised (local)` | Saved locally first, so a crash does not lose it. |
| `Backend: pending → written ✓ / failed ✗` | Firebase write status. |
| `Partner device: acknowledged ✓ / unknown` | The partner app writes an acknowledgement. |
| `SMS: per contact queued → sent ✓ / failed ✗ / delivered ✓ / unknown` | Driven by `sentIntent` and `deliveryIntent`. |
| `Cancelled by <member> at <server time>` | Who cancelled, and when. |

## 4. Recommended immediate actions (owner, no code)

1. Revoke the GitHub token and delete `app_config/github_token` (S2).
2. Decide public vs. private for `varad-oss/AshGo` (S3).
3. Export the current RTDB rules and a JSON backup of the database before any v2 migration (command in §7).
4. Back up `~/.android/debug.keystore`. Existing installs can only be upgraded with it until the release-key migration is done (S5).

## 5. Verification still required
- Read the deployed rules and confirm or refute S1 using the Emulator Suite with a copy of those rules.
- Measure how long anonymous sign-in takes from a fresh install, then remove anonymous auth once real auth is in place.
- On a real Android 14/15 device, test SMS dispatch with a test contact the owner controls. Real SMS must never be sent from automated tests.
- Run the restricted-settings flow for the accessibility service on a sideloaded install.

## 6. Out of scope / not found
- No hardcoded credentials, service-account keys or keystores in the repo or its history (scan of `git log -p --all`).
- No WebView, no dynamic code loading, no exported content provider.

## 7. Owner commands (read-only)
Run these on your own machine. They use your Firebase login, and nothing is changed.

```bash
npx firebase-tools login:list
```

```bash
npx firebase-tools database:get / --project <your-project-id> --shallow
```

To view the deployed rules: Firebase console → Realtime Database → **Rules** tab. Copy the JSON into `firebase/database.rules.current.json` (git-ignored) so v2 can test against it.
