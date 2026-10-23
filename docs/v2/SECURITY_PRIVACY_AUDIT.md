# AshGo — Security & Privacy Audit

Audit date: 2026-10-13. Scope: source on `main` @ `fc1070e`, git history, the public GitHub repository, and one read-only probe of the production Realtime Database.

**Evidence labels**
- **CONFIRMED**: proven by code, history, or a direct observation.
- **LIKELY**: strongly implied by code, but depends on backend state we could not see.
- **UNVERIFIED**: plausible, and needs access we do not have.

No secret values are reproduced in this document.

---

## Status re-check — 2026-10-24 (local repository only)

**Method:** source, rules, tests, build config and git history read on `v2` @ `46329bf`. Rules suite run on the local emulator (project `demo-ashgo`); JVM unit tests and lint run locally. **No production system was accessed:** no live probe, rule read or deploy. The live-database observations in the 2026-10-21 table below are dated evidence from that day, not re-verified today.

**Labels:** **current** = confirmed in today's code · **fixed** = fixed, with evidence · **historical** = past issue that is still relevant · **owner** = needs owner or production verification.

| Item | Label | Finding |
|---|---|---|
| Legacy v1 paths in `database.rules.json` (phase 1) | **current** (file) · **owner** (live) | Phase 1 still grants `auth != null` read and write on `chat`, `tracking`, `alerts`, `buzz` and `users`, so **any signed-in account** could use those paths. Phase 2 removes them and leaves no root grant. The v2 subtrees (`pairs`, `invites`, `userPairs`, `app_config`) are byte-identical in both files, so the pair, role and outsider tests cover both stages. Moving to phase 2 requires both phones on v2, the legacy data deleted, then an owner deploy and check. The owner record says this happened in October 2026; the repo can't prove the live state. |
| `firebase.json` deployed phase 1 | **fixed** today | The default deploy target was `database.rules.json`, so a routine `firebase deploy` would have **reopened** the legacy paths. It now points at `database.rules.phase2.json`, and `firebase/test/stages.test.mjs` fails if the target changes, or if phase 2 regains a legacy path, a root grant or a differing v2 subtree. **Not deployed.** |
| Updater URL allowlist | **fixed** today | `allowedUrl` checked only the path prefix, so `/varad-oss/AshGo-releases/../other/…`, including the `%2e%2e` form, passed. Dot segments are now rejected (`UpdatePolicyTest.dotSegmentsCannotWalkOutOfTheReleasesRepo`). The SHA-256 check already blocked any wrong file, so this is defence in depth. |
| Updater download size | **fixed** today | There was no limit while downloading. The download now stops as soon as it passes the announced `apk_size`, or 100 MB when no size is announced (`downloadsStopAtTheAnnouncedSizeOrTheHardCap`). |
| Updater token, hash, https, redirects | **fixed** | No token anywhere in the source. Every redirect hop is re-checked against the allowlist, and the file goes to the system installer only after its SHA-256 matches. |
| Release signing | **current** (by design) · **owner** | `release` uses `signingConfigs.debug`, the owner's machine key, kept so v1 and v2 installs update in place (D-9). The build is non-debuggable with R8. A dedicated release key would need APK Signature Scheme v3 rotation, and it is an owner decision. Losing that keystore ends in-place updates. |
| Debug emulator switch | **fixed** | `DevBackend` ignores `useEmulators` unless the app is debuggable, so another app can't send a release build to a fake backend. |
| Personal assets | **historical** · **owner** | None in the current tree. Git history still contains 9 photos and a voice recording (paths: `app/src/main/res/drawable/photo_*`, `app/src/main/res/raw/*.mp3`). The owner accepted this on 2026-10-21 (§9). |
| Credentials in the tree | **fixed** | No keystore, `.env`, APK or `google-services.json` is tracked. `.gitignore` now also covers `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `.env*`, `*.apk`, `*.aab`, `*.log` and `firebase/database.rules.current.json`. |
| SOS authorization | **fixed** | Only the member who joined can create an SOS. Only its sender can cancel it, and only the partner can acknowledge it, both with server time. An active SOS can't be deleted. All of this is asserted in `data.test.mjs` and `roles.test.mjs`. |
| Backups, logs, permissions | **fixed** | Backup and device transfer exclude all app data. Logs carry no numbers or coordinates. There is no `READ_CONTACTS` and no accessibility service. |
| Background alerting | **current** (limit) | The watcher's phone is connected only during the traveller's trip, or while following live sharing (`BackgroundPolicy`). Outside those windows an SOS reaches the watcher by SMS only. The README says so. |
| CI | **fixed** today | `.github/workflows/ci.yml` runs unit tests, lint and the rules suite with `contents: read`, no secrets and a placeholder Firebase config. It was verified locally in a clean worktree, and both jobs pass on GitHub for `v2` @ `f763bf1` (after switching the rules script to a file glob so it works on Node 22). |

## Status re-check — 2026-10-21 (v2.0.1, `v2` @ `8b867ea`)

Sections 1–8 below are the **original v1 audit**, kept for the record. Below is where each finding stands now.

| # | Status | Evidence (2026-10-21) |
|---|---|---|
| S1 authorization | ✅ **Closed** | Google sign-in only, with no anonymous auth (`signInAnonymously` is absent from the code). Data lives under `pairs/{pid}`, with membership enforced by the phase-2 rules (64/64 emulator tests). Live DB: unauthenticated reads of `/`, `/pairs`, `/userPairs` and `/app_config` all return **401**. Only `app_config`, `pairs` and `userPairs` remain at the root; the v1 nodes are deleted. |
| S2 GitHub token | ✅ **Closed** | `app_config` holds only `apk_url, apk_size, sha256, latest_version_code, latest_version_name, notes`, with no token. The updater sends no `Authorization` header and downloads from the public `AshGo-releases`. The old v1 token was revoked by the owner on 2026-10-21. |
| S3 personal media in a public repo | 🟡 **Accepted by owner 2026-10-21 (history only)** | Nothing personal is in the current tree. Git history still holds 9 photos (`res/drawable/photo_*`) and a voice recording (`res/raw/*fake_call.mp3`), and `varad-oss/AshGo` is **public** (0 forks). Fixing this needs an owner decision; see §9. |
| S4 SOS forgery | ✅ **Closed** | SOS writes are rules-restricted to the member who joined, and record the sender (`from`) and server time. Only the sender can cancel, and the cancel time is server time. The widget only handles `APPWIDGET_UPDATE`, and a tap opens the non-exported hold screen. The SOS activities and SMS receivers are `exported="false"`. |
| S5 updater integrity | ✅ **Closed** | The updater accepts only an allowlisted `https://github.com/varad-oss/AshGo-releases/releases/download/…` URL and checks the SHA-256 and size before the system installer opens. Release builds have `isDebuggable = false` and R8 on. targetSdk 34 blocks cleartext by default. **Remaining:** the APK is still signed with `~/.android/debug.keystore`, kept deliberately so updates install in place. The owner made an offline backup of the keystore on 2026-10-21. |
| S6 permissions | ✅ **Closed** | No `READ_CONTACTS` (the system phone picker is used instead), and no accessibility service. |
| S7 backup and logs | ✅ **Closed** | `backup_rules.xml` and `data_extraction_rules.xml` exclude `file`, `sharedpref` and `database` from both cloud backup and device transfer. No log line contains phone numbers or coordinates (location logs give accuracy only). The RTDB cache is app-private on a non-debuggable build. |
| S8 retention | ✅ **Closed** · accepted limit | Stopping sharing removes the last point. Retention covers messages 7 d, photos 3 d, check-ins 7 d, trips 24 h after ending and cancelled SOS 30 d; leaving the orbit deletes all of it. **Limit:** pruning runs on the members' phones, because Spark has no Cloud Functions. |
| S9 no E2EE | ✅ **Accepted, not claimed** | No screen or document claims end-to-end encryption. |
| S10 `notifications` open | ✅ **Closed** 2026-10-14 | The node is deleted, and its rule is gone in phase 2. |

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

### S10 ✅ CLOSED 2026-10-14 — `notifications` was world-readable and world-writable

**Closed:** the owner deployed the phase-1 rules. A read-only probe then got HTTP 401 for an unauthenticated read **and** write of `notifications` (both were open before).

**Original finding:**
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


## 8. Deploying the phase-1 rules (owner, 2026-10-14)

> **Historical (done).** Since 2026-10-24 `firebase.json` points at the phase-2 file, so the CLI command below now deploys **phase 2**. Phase 1 should never be redeployed: it would reopen the legacy paths.

`firebase/database.rules.json` is safe to deploy **now**, while v1 is still installed.

**What it keeps working:**
- Every path v1 reads or writes: `chat`, `tracking/*`, `alerts`, `buzz/*`, `users/*` stay `auth != null`.
- `app_config` and `notifications` stay readable.

**What it closes:**
- **S10:** nobody can write `notifications` any more.
- Clients can no longer write `app_config`.
- There is no root-level grant, so the new `pairs/*` data can never leak through a cascade.

**What it adds:**
- `.indexOn: timestamp` for v1's cleanup queries.
- The v2 pairing and pair-data rules, which are unused until v2 ships.

**Steps:**
1. **Back up first:** Firebase console → Realtime Database → ⋮ → *Export JSON*. Also copy the current Rules text somewhere safe.
2. **Deploy**, either way:
   - **Console:** Realtime Database → **Rules** → paste the full contents of `firebase/database.rules.json` → **Publish**.
   - **CLI**, from `firebase/`: run the command below.

     ```bash
     npx firebase deploy --only database --project <your-project-id>
     ```

     This needs `npx firebase login`.
3. **Check:** open the app on both phones and confirm that chat, location, Thunder and SOS still behave as before.
4. **Rollback:** paste the old rules back.

**Still exposed after phase 1 (S1):** any signed-in client can still read the legacy v1 paths. That ends only with phase 2, after both phones run v2. **Keep the releases repo private until then** (D-10).

## 9. Closing S3 (owner decision, 2026-10-21)

The current tree is clean. The personal photos and voice recording are only in **git history**, and they can still be downloaded from the public repo, by commit or from old pull-request refs.

**Option A: make `varad-oss/AshGo` private.** *(Recommended: instant, free and reversible.)*
- Updates keep working, because the app downloads from the separate public `AshGo-releases` repo.
- The README is no longer public.

**Option B: rewrite history and force-push.**
- Rewrite with `git filter-repo --invert-paths --path-glob 'app/src/main/res/drawable/photo_*' --path-glob 'app/src/main/res/raw/*.mp3'`, then force-push every branch.
- This is destructive, and it changes every commit hash.
- GitHub keeps `refs/pull/*` and cached commits, so fully removing them also needs a GitHub Support request to purge cached views.

**Option C: both.** Rewrite the history, keep the repo private until GitHub confirms the purge, then make it public again.

**Decision (2026-10-21):** the owner chose to leave the history as it is and accept the risk. Nothing personal is in the current tree, and new personal media stays on the phones (Our page and fake-call voices are never committed).
