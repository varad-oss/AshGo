# AshGo v2 — Product Decisions

**Status key**
- **Recommended**: awaiting owner approval
- **Owner action**: only the owner can do it
- **Decided**: approved

Nothing here is final until the owner reviews it. Decisions marked Recommended are what the spec and plan will assume unless overridden.

---

## D-1 Identity and pairing model — **Decided 2026-10-13: (C) Google Sign-In**

**Owner chose C.** Consequences:
- **Identity:** a Firebase Auth Google provider via AndroidX Credential Manager + `googleid` (Apache-2.0, free; Google sign-in is a "non-phone" provider and free on Spark).
- **Recovery:** signing in with the same Google account on a new phone restores the same UID and access, with no partner approval needed.
- **Pairing proposal:** invite **by email** instead of by code.
  1. A enters B's Google email.
  2. The invite is stored under a hash of that email.
  3. Rules allow joining only when `auth.token.email` matches **and** `auth.token.email_verified == true`, and the pair has fewer than 2 members.

  This removes guessable codes entirely. A short "Is this you, B?" confirmation (avatar + email shown on both sides) replaces orbit words. A QR code remains an optional convenience.
- **Migration:** on the first v2 launch, call `linkWithCredential` on the existing anonymous user, so the UID is preserved where possible. If the Google account is already linked elsewhere, sign in instead and re-import.
- **Owner setup: ✅ done 2026-10-13.** Verified locally: `google-services.json` now has an Android OAuth client whose certificate hash equals the debug-key SHA-1, plus a web client (type 3). The file is still git-ignored. Steps for reference:
  1. Firebase console → Authentication → enable the **Google** provider.
  2. Project settings → Android app `com.varad.unstoppableash` → add the signing certificate fingerprints:
     - SHA-1 `43:16:66:AE:F0:61:DD:BF:09:CB:39:8F:8A:4F:51:B6:CC:80:ED:FC`
     - SHA-256 `89:93:8A:55:…:53:9A:56`

     These belong to this Mac's debug key, which also signed the shipped APK. Verified with `apksigner`. They are public certificate fingerprints, not secrets.
  3. Re-download `google-services.json`; it must then contain an `oauth_client` of type 3, the web client ID.
- **Privacy note:** each member's Google email and display name become visible to their partner, and are stored in the pair's profile only with consent at pairing.

### Original comparison (kept for the record)

| Option | How | Pros | Cons | ₹0 |
|---|---|---|---|---|
| **A. Anonymous auth + invite-code pairing + orbit words** | Each install gets a Firebase anonymous UID. Pairing binds exactly two UIDs, and the rules enforce membership. Device change works by partner approval. | No accounts or passwords. Smallest data footprint. Works for any relationship. Pairing *is* the authorization. | Clearing app data or reinstalling loses the identity, so the person must re-join with partner approval. If **both** phones are lost at once, the orbit must be recreated. | ✓ |
| B. Email + password (Firebase Auth) | Standard sign-up per person, plus the same pairing. | Recovery on a new device without the partner. | Passwords to remember; email verification flow; a weaker "no accounts" story. | ✓ |
| C. Google Sign-In (Credential Manager) | Same pairing; Google account as identity. | Best recovery; no passwords. | Needs the app's SHA-1 registered in the Firebase console and an OAuth client (`google-services.json` has none today). Ties identity to Google accounts. | ✓ |

**Why A:** for a two-person circle, the partner is the natural recovery authority. Option A also matches the "no unnecessary accounts" principle. Linking Google later as an optional recovery method (Option C as an add-on) remains possible without a schema change, because Firebase can link providers to an existing UID.

**Not acceptable, any option:** the current shared global paths with `auth != null`.

## D-16 Pairing safeguards and leaving — **Decided 2026-10-14 (Stage 3 security review)**
- **Who can pair:** only Google-verified accounts can create pairs, invite, or join. Anonymous accounts (v1) and other providers can't.
- **Invites can't be squatted:** invites are stored per inviting pair, so no invite can block another.
- **Invites show who really sent them:** the invite carries the inviter's real email, which the rules check against their sign-in token. The invitee is shown that email, not just a typed name.
- **Nothing private before confirmation:** until memberA confirms ("Yes, that's them"), the joiner can see only the pair's metadata and profiles, never location, presence, trips, chat, media, events or SOS. This protects against a mistyped email.
- **Leaving is always possible, even during the partner's active SOS.** It deletes the shared data. Blocking it would let an abusive partner hold someone in the orbit indefinitely. The SOS texts to emergency contacts have already been sent by the sender's phone, independently of the database.
- **Stage 10 note:** `LegacyImporter` can't write partner-authored messages with old timestamps under these rules, by design. The import must be redesigned so that each phone imports only its *own* legacy messages, keeping the original time in a separate field. It must not relax `from`/`at` validation, because the legacy paths are writable by any signed-in client until phase 2.

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

## D-6 SOS activation friction — **Decided 2026-10-13: (B) hold 3 s, send immediately, no countdown**

**Owner chose B.** Design consequences:
- **The hold is the only safeguard,** so it must be unmistakable. It shows a ring that fills over 3 s with haptic ticks at 1 s and 2 s and a strong haptic on send. Releasing early sends nothing and shows "Hold for 3 seconds to send SOS".
- **Undo after sending:** the first screen after sending leads with a large **"I'm safe: cancel SOS"** button, itself hold-to-confirm for 1.5 s. Cancelling notifies the partner ("Cancelled by A") and sends an SMS "Sorry, false alarm, I'm safe" **only to contacts whose SOS SMS was already sent**. That follow-up SMS is shown and confirmed on screen before it goes, since it costs money.
- **Widgets, the QS tile and notification actions cannot detect a long-press.** Android `RemoteViews` only deliver taps. These entry points therefore open a full-screen **SOS hold screen** (shown over the lock screen) where the 3 s hold happens. A tap alone never sends.
- **Duplicate protection:** holding again while an SOS is active doesn't create a second event. It offers "Resend SMS to failed contacts" instead.

**Earlier recommendation (superseded):** hold 3 s + 5 s cancellable countdown.
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
It collides with normal volume use, is blocked by restricted settings on sideloaded installs, and requests over-broad accessibility events. It is replaced by the Quick Settings tile, the safe widget, and the notification action, all routed through the SOS hold screen.

## D-9 Signing continuity — **Owner action + Recommended**
Existing installs are signed with this Mac's debug key.
- **Recommendation:** copy `~/.android/debug.keystore` to a backed-up location, and configure a `release` signing config that uses it. Then ship **non-debuggable**, R8-optimised release builds that install over v1 with no data loss.
- **Alternative:** generate a new release key. This is cleaner, but every install must be uninstalled once, which loses local data such as emergency contacts.

## D-10 Updates — **Decided 2026-10-14: (A) public releases repo with APKs only, no token**

**Owner's condition:** "given that my data doesn't get leaked".

**Ordering guarantee:** the releases repo becomes public **only after** rules phase 2 is deployed (legacy paths denied) **and** the legacy data is deleted. Until then, any APK holder could still read v1 data through the legacy `auth != null` paths.

**Interim:** keep the private repo, using a fine-grained, single-repo, Contents-read-only token in `app_config/github_token`.

**v2 flow:** the owner bumps `app_config/latest_version_code` and sets `apk_url` + `sha256` in the console. The app shows the size, downloads, verifies the SHA-256, and hands the file to the system installer. Rules make `app_config` read-only for clients.

Earlier recommendation, kept for the record:
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

## D-14 Retention defaults — **Decided 2026-10-13**

**Owner chose (B) for pause:** the last precise point is **kept**. It is shown faded, labelled "Paused · last seen 6:40 pm", with no "Live" styling and no freshness alarms. Leaving the orbit still deletes it. The other defaults were accepted without objection.

| Data | Retention |
|---|---|
| Chat text | 7 days |
| Photos | 3 days |
| Check-ins and nudges | 7 days |
| SOS events | 30 days, or until resolved and then 30 days |
| Location | latest point only |
| Location when paused | kept as the last point, shown as paused with its age (owner decision) |
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
- ~~**Q-1:** Identity~~: **Answered: C, Google Sign-In** (see D-1).
- **Q-2:** Make the main repo private, or keep it public and scrub personal media?
- ~~**Q-3:** SOS activation~~: **Answered: B, hold 3 s and send immediately** (see D-6).
- ~~**Q-4:** Retention~~: **Answered: defaults accepted; keep the last point on pause, shown as paused** (see D-14).
- **Q-5:** Should the partner be told automatically when a trip starts (the Google Safety Check makes this optional; we default to *on* for Partner and Family, *off* for Friends)?
- **Q-6:** Is the owner willing to verify Cloudflare's no-card free plan, so a later push relay can be considered? (Not needed for v2.0.)

## Assumptions needing real-user validation
- The orbit-words step feels cute rather than tedious.
- Partners prefer gentle presence notifications over alarm-volume tones.
- Two hours a day of trip sharing is a realistic upper bound for the quota estimates.
- Battery-optimisation exemption is acceptable to both users, given its trade-off.
