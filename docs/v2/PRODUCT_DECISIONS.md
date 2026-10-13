# AshGo v2 — Product Decisions

**Status key**
- **Recommended**: awaiting owner approval
- **Owner action**: only the owner can do it
- **Decided**: approved

Nothing here is final until the owner reviews it. Decisions marked Recommended are what the spec and plan will assume unless overridden.

---

## D-1 Identity and pairing model — **Recommended: (A), open question Q-1**

| Option | How | Pros | Cons | ₹0 |
|---|---|---|---|---|
| **A. Anonymous auth + invite-code pairing + orbit words** | Each install gets a Firebase anonymous UID. Pairing binds exactly two UIDs, and the rules enforce membership. Device change works by partner approval. | No accounts or passwords. Smallest data footprint. Works for any relationship. Pairing *is* the authorization. | Clearing app data or reinstalling loses the identity, so the person must re-join with partner approval. If **both** phones are lost at once, the orbit must be recreated. | ✓ |
| B. Email + password (Firebase Auth) | Standard sign-up per person, plus the same pairing. | Recovery on a new device without the partner. | Passwords to remember; email verification flow; a weaker "no accounts" story. | ✓ |
| C. Google Sign-In (Credential Manager) | Same pairing; Google account as identity. | Best recovery; no passwords. | Needs the app's SHA-1 registered in the Firebase console and an OAuth client (`google-services.json` has none today). Ties identity to Google accounts. | ✓ |

**Why A:** for a two-person circle, the partner is the natural recovery authority. Option A also matches the "no unnecessary accounts" principle. Linking Google later as an optional recovery method (Option C as an add-on) remains possible without a schema change, because Firebase can link providers to an existing UID.

**Not acceptable, any option:** the current shared global paths with `auth != null`.

## D-2 Data model and legacy migration — **Recommended**
- New tree: `pairs/{pairId}/{members, profiles, presence, location, trips, events, chat, media, sos}`, plus `invites/{codeHash}` and `userPairs/{uid}`.
- Rules use membership checks, sender-ownership checks (`newData.child('senderUid').val() === auth.uid`), and server-time validation (`now`).
- **Migration (coordinated cutover):**
  1. The owner exports a JSON backup of the database and the current rules (SECURITY_PRIVACY_AUDIT §7).
  2. Both phones install v2 the same day.
  3. After pairing, the first member's client imports from the legacy paths: the last 7 days of chat (images included only if ≤ 150 KB), display names, and avatars (downscaled).
  4. Emergency contacts and fake-call voices are local and migrate on-device from SharedPreferences.
  5. Once both members have run v2, the owner deploys rules that **deny** all legacy paths. Then the legacy data is deleted, with the backup kept offline.
- **Rollback:**
  - The v1 APK still works until step 5, since the legacy paths are untouched.
  - After step 5, rollback means re-deploying the old rules from the backup.
- **Rejected:** dual-writing legacy paths from v2, because it would keep the exposed paths alive indefinitely.

## D-3 Symmetric members — **Recommended**
Either person can share location, start trips, check in, nudge and raise SOS. The "traveller/receiver" roles are removed. A relationship label only changes tone and copy. *Why:* in real life the roles swap; the current one-way design leaves the "receiver" with no SOS.

## D-4 Notifications without a server — **Recommended**
- **v2.0** keeps one "Stay connected" foreground service per phone with bounded RTDB listeners. It is honest about its permanent notification and backed by the Reliability check.
- **FCM** isn't usable on Spark without a trusted sender (see ZERO_COST_DEPENDENCIES §3).
- **FGS type:** `specialUse` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation ("partner safety alerts for a private two-person circle"). Since the app is sideloaded, there's no Play review.
  - **Rejected:** `remoteMessaging`, whose documented purpose is "continuity of a user's messaging tasks when they switch devices", which doesn't match. Also rejected: `dataSync`, which has a 6 h per 24 h limit when targeting API 35+ ([timeout docs](https://developer.android.com/develop/background-work/services/fgs/timeout)).
  - The service-types page documents no time limit for `specialUse`.
  - **Needs device verification** on API 34/35/36.
  - Location uses type `location`, started only from user actions, because a location FGS cannot be created from the background without `ACCESS_BACKGROUND_LOCATION` ([service types](https://developer.android.com/develop/background-work/services/fgs/service-types)).

## D-5 Photos within ₹0 — **Recommended**
Keep RTDB Base64, but:
- encode off the main thread
- max 1024 px on the long edge
- adaptive JPEG quality targeting ≤ 120 KB, with a hard reject above 150 KB
- store in `media/{id}`, separate from `chat`, so message lists stay small
- retain for 3 days

Text chat works with photos disabled. Cloud Storage is rejected (Blaze-only).

## D-6 SOS activation friction — **Recommended: hold 3 s + 5 s cancellable countdown; open question Q-3**
**Alternatives considered:**
- Single tap: rejected, because of accidental-trigger evidence in the current app.
- Hold only: faster, but has no undo.
- PIN to cancel: protects against a coerced cancel, but adds stress friction. Deferred.

**Why the recommendation:** the countdown screen is also where the user sees exactly what will be sent and to whom.

## D-7 Maps — **Recommended: keep osmdroid for v2.0**
- **Required now:** add the attribution overlay, set the User-Agent to `AshGo/<version> (+contact)` (owner provides a contact, or a GitHub URL), and keep osmdroid's tile cache with no prefetch, per the OSM tile policy.
- **Why not migrate now:** osmdroid works, and migration is not on the safety-critical path.
- **Revisit** MapLibre + OpenFreeMap in v2.1 (upstream archived, and the vector look would match the brand better).

## D-8 Volume-key accessibility SOS — **Recommended: remove**
It collides with normal volume use, is blocked by restricted settings on sideloaded installs, and requests over-broad accessibility events. It is replaced by the Quick Settings tile, the safe widget, and the notification action, all routed through the countdown.

## D-9 Signing continuity — **Owner action + Recommended**
Existing installs are signed with this Mac's debug key.
- **Recommendation:** copy `~/.android/debug.keystore` to a backed-up location, and configure a `release` signing config that uses it. Then ship **non-debuggable**, R8-optimised release builds that install over v1 with no data loss.
- **Alternative:** generate a new release key. This is cleaner, but every install must be uninstalled once, which loses local data such as emergency contacts.

## D-10 Updates — **Recommended + Owner action**
- **Replace** the token-based auto-download with a manual **Check for update** that queries a **public** releases repo containing only APKs and checksums (no personal content).
- The app verifies the SHA-256 published in the release notes or an asset, then hands the file to the system installer.
- **Owner:**
  1. Revoke the current token.
  2. Delete `app_config/github_token`.
  3. Decide whether `AshGo-releases` becomes public.

## D-11 Public repository with personal media — **Owner decision (Q-2)**
Options:
- **(a)** Make `varad-oss/AshGo` private (free).
- **(b)** Keep it public: v2 removes personal assets and names from the tree going forward, and the owner optionally rewrites history.

**Either way, v2 code ships no personal media**, and "Our page" uses on-device photos.

## D-12 Visual identity — **Recommended**
- "Little Orbit", with a **light theme by default** plus a designed dark theme (plum and charcoal). This changes the current dark-only indigo look.
- Nunito is bundled (OFL).
- Palette from the brief, subject to a contrast check in REDESIGN_SPEC.
- Emergency surfaces use a separate, undecorated red system.

## D-13 Architecture weight — **Recommended**
- Single `:app` module.
- ViewModels + `StateFlow` + repositories with interfaces.
- Manual DI via an `AppContainer`. Hilt is rejected as unnecessary weight for about 20 classes.
- Pure-Kotlin domain logic (presence, SOS reducer, cadence, cleanup selector, arrival detector) so it is unit-testable on the JVM.

## D-14 Retention defaults — **Recommended (Q-4 to confirm)**

| Data | Retention |
|---|---|
| Chat text | 7 days |
| Photos | 3 days |
| Check-ins and nudges | 7 days |
| SOS events | 30 days, or until resolved and then 30 days |
| Location | latest point only |
| Location when paused | optionally cleared |
| Trips | deleted 24 h after they end |
| Profiles | until the member leaves |

Cleanup runs at most once a day from either phone, using server-time cutoffs.

## D-15 Removed or retired features (with reasons)

| Feature | Reason |
|---|---|
| Volume-key SOS | D-8 |
| Admin "custom notifications" | Nothing writes them; replaced by the update check. |
| Token updater | D-10 |
| Standalone vehicle field | Folded into the trip note. |
| Firebase Analytics, Firebase Storage SDKs | Unused; privacy and cost. |
| `FirebaseRepository` (dead), `ChatState`, `patch_*.py`, `test.js` | Dead or stray code. |
| Bundled personal photos and voice recording | Replaced by on-device "Our page" and user-chosen fake-call audio. |

## Open questions for the owner
- **Q-1 (blocking for architecture):** Identity: A (no accounts, partner-approved recovery), B (email/password) or C (Google Sign-In)? *Recommendation: A.*
- **Q-2:** Make the main repo private, or keep it public and scrub personal media?
- **Q-3:** SOS activation: hold 3 s **plus** a 5 s cancellable countdown, or hold only?
- **Q-4:** Are the retention defaults in D-14 acceptable? In particular, should precise location be cleared when sharing pauses?
- **Q-5:** Should the partner be told automatically when a trip starts (the Google Safety Check makes this optional; we default to *on* for Partner and Family, *off* for Friends)?
- **Q-6:** Is the owner willing to verify Cloudflare's no-card free plan, so a later push relay can be considered? (Not needed for v2.0.)

## Assumptions needing real-user validation
- The orbit-words step feels cute rather than tedious.
- Partners prefer gentle presence notifications over alarm-volume tones.
- Two hours a day of trip sharing is a realistic upper bound for the quota estimates.
- Battery-optimisation exemption is acceptable to both users, given its trade-off.
