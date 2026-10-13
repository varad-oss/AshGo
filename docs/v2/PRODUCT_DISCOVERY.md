# AshGo v2 — Product Discovery ("Little Orbit")

Status: research complete; recommendations await owner review.

**Evidence tags**
- **[E]** externally supported (cited)
- **[C]** confirmed in this codebase
- **[H]** our hypothesis; needs real-user validation

---

## 1. Product thesis

> **AshGo is a private space for two people who look out for each other. It tells each of you, honestly and at a glance, whether the other is okay, where they are if they've chosen to share, and how sure the app is about that. It makes reaching out or asking for help a single deliberate action. It costs nothing, and it never turns care into surveillance.**

Three words carry the thesis:
- **Honest**: freshness and delivery are always shown truthfully. The current app's biggest failure is that it says "Live" and "SOS TRIGGERED" without evidence [C].
- **Deliberate**: sharing and alerts are choices, not ambient tracking.
- **Two**: the app is built for one pair, not a family-wide surveillance grid.

## 2. Target users

| Persona | Situation | Core need |
|---|---|---|
| **Partners** (primary; the owner's use case) | Commutes, late cabs, travel, different cities | Reassurance without nagging; a fast way to get help |
| **Parent and young adult** | First jobs, hostels, night travel | Arrival confirmation without constant check-up calls |
| **Two close friends** | Nights out, walking home, dates with strangers | A "check on me" timer and a discreet exit (fake call) |

All three share one shape: **one person moves while the other worries**, and roles swap from day to day. The current traveler/receiver split [C] wires in one direction only. v2 makes the pair symmetric.

## 3. Research: what mature products do

| Product | What it does well | Assumptions and limits | Lesson for AshGo |
|---|---|---|---|
| **Apple Check In** [E: MacStories, TidBITS, Tom's Guide] | Timer- or arrival-based. When the user is late, it asks **the user first** ("add time?"), then waits 15 minutes, re-alerting every 5. Shares data **only if** the user doesn't respond. Two data levels: *Limited* (location, battery, signal) and *Full* (adds route and last unlock). | Requires iOS 17 on both phones; relies on Apple's servers. | The **ask-me-first escalation** and **share-only-on-failure** model is the best pattern found. Battery and signal level explain silence. |
| **Google Personal Safety: Safety Check** [E: Google blog, Android Central] | Timer from 1 minute to 8 hours; 60 s warning; starts Emergency Sharing only if the user doesn't respond. Optionally notifies contacts on start and cancel. Can be started by voice. | Pixel-heavy; uses Google Maps links over SMS. | Confirms the timer pattern. Notify-on-start is an **option**, not a default. |
| **Google Emergency Sharing** [E] | Real-time location to contacts by SMS link. | Needs a backend for live links. | Our SMS can only carry a **snapshot** link. Say so honestly. |
| **Life360** [E: The Markup 2021/2023; reviews] | Continuous family location, place alerts. | Complaints centre on battery drain, stale or wrong positions, family friction, and the **sale of precise location data** (reported by The Markup; class action filed in 2023). | Our differentiators are **no data business, truthful staleness, and low battery cost**. Avoid always-on high-accuracy tracking. |
| **WhatsApp live location** [general knowledge, H] | Time-boxed sharing (15 min / 1 h / 8 h) inside a chat. | No SOS, no freshness guarantees, no arrival logic. | **Time-boxed sharing** is a well-understood mental model. Reuse it for "share for 1 h". |
| **OwnTracks / Traccar** (open source) [H, by reputation] | Self-hosted location history. | Need a server and setup. | Validates the privacy-first audience, but the setup is too heavy for a couple. |
| **Android platform** [E: developer.android.com] | Background FGS start is restricted from Android 12. Location FGS can't be created from the background on Android 14+ without background location. `dataSync` FGS is limited to 6 h per 24 h when targeting Android 15. "Restricted settings" apply to sideloaded accessibility and (Android 15) SMS. | — | **Reliability must be designed around the platform**, not hoped for. Build a "reliability check" screen; never promise background behaviour the OS won't give. |

## 4. Key real-world problems (ranked by severity × frequency)

| # | Problem | Severity | Frequency | Evidence |
|---|---|---|---|---|
| P1 | "Is that location *now*, or from an hour ago?" A stale or fake location read as live gives false reassurance. | Very high | Daily | [C] fake initial marker, "Live" with no data; [E] Life360 complaints |
| P2 | "Did my SOS actually reach anyone?" | Critical | Rare | [C] success toast without evidence; SMS gated on an unrelated toggle |
| P3 | "Why isn't she replying? Busy, offline, or in trouble?" | High | Daily | [H] owner context; [E] Check In shares battery and signal for exactly this reason |
| P4 | "Tell me when you get home" relies on remembering to text | Medium | Daily | [E] Check In, Safety Check |
| P5 | Notifications for chat and Thunder silently never appear | High | Constant | [C] missing channel |
| P6 | Anyone with the APK could read everything | Critical | Latent | [C]/[LIKELY] S1 |
| P7 | Pausing location is punished with a "signal lost" alarm | Medium | Daily | [C] |
| P8 | Accidental SOS from the widget or volume key | High (trust-destroying) | Occasional | [C] |
| P9 | Phone dying mid-journey looks the same as an emergency | High | Weekly | [H]; [E] Check In battery sharing |
| P10 | Background services killed by the OEM, so silent failure | High | Device-dependent | [E] Android FGS rules; [H] OEM behaviour (dontkillmyapp-style issues) |

## 5. Brainstormed opportunities (26)

Format: **Problem → Feature → Use (steps) → Why better → MVP → Dependencies → ₹0? → Failure modes → Privacy → Complexity / tests → Evidence**

### Category 1 — Low-friction everyday connection

**O-1. Quick check-ins ("Home safe", "On my way", "Thinking of you", custom)**
- **Problem:** reassurance currently needs a full chat or a call (P3, P4).
- **Feature:** a one-tap check-in from Home, with a result shown as "Sent ✓ · Seen ✓" or "Not sent: offline, will retry".
- **Use:** open Home → tap "Home safe" → a toast-free inline status shows *Sending… → Delivered to AshGo ✓ → Seen ✓ (when the partner app acknowledges)*.
- **Why better:** clearer than chat, with honest delivery states.
- **MVP:** 3 presets plus one custom, stored as pair events with server time.
- **Dependencies:** RTDB.
- **₹0:** ✓
- **Failure modes:** offline (queued by RTDB, so show "waiting for connection"); the partner's service is dead ("Seen" never arrives, which is truthful).
- **Privacy:** minimal.
- **Complexity:** S. Tests: state reducer, idempotency.
- **Evidence:** [H] + [E] Check In uses the same pattern.

**O-2. Status line ("In a meeting till 4", "Driving", "Asleep 🌙")**
- **Problem:** P3, the ambiguity of silence.
- **Feature:** an optional short status with expiry, shown on the partner's Home.
- **MVP:** text plus an emoji preset, expiring after 1 h, 4 h or "until I change it".
- **₹0:** ✓
- **Risk:** low.
- **Complexity:** S.
- **Evidence:** [H]

**O-3. Nudge (Thunder, reborn)**
- **Problem:** the current Thunder forces max alarm volume, fires twice, depends on clocks, and its notification never shows [C].
- **Feature:** typed nudges: "Thinking of you 💗", "Call me when free", "Are you okay?". Normal notification channel; respects Do Not Disturb; rate limit of 1 per 30 s.
- **MVP:** event node, server timestamp, the partner's service shows exactly one notification. Keep the ⚡ Thunder visual as the playful brand element.
- **₹0:** ✓
- **Failure modes:** partner service not running, so the nudge is shown at next open with its time.
- **Complexity:** S.

### Category 2 — Travel awareness and arrival

**O-4. Trips ("Walk me home" / "Watch my trip")**
- **Problem:** P1, P4, P9. The current destination is receiver-only and arrival shows only on an open screen [C].
- **Feature:** either person starts a trip with an optional destination (map pick or saved place), an optional "expect me by" time, and an optional note (cab number). Location cadence goes up for the trip only. Arriving within a radius ends the trip and sends a "Home safe" check-in automatically.
- **Use:**
  1. Tap "Start trip".
  2. Choose a destination or "no destination".
  3. Choose "expect me by 11:30" (or skip).
  4. Tap Start.
  5. Partner Home shows "On a trip · 12 min ago · 2.3 km to go".
- **Why better:** a bounded, consented window of higher-detail sharing. This is the Check In / Safety Check pattern, without needing their ecosystems.
- **MVP:** trip node + adaptive cadence + arrival check-in + trip note.
- **Dependencies:** location FGS (foreground start from a user action, which satisfies Android 14 rules [E]).
- **₹0:** ✓
- **Failure modes:** GPS drift near the destination (use accuracy-aware arrival: distance < max(100 m, accuracy) for 2 consecutive fixes); OS kills the service (partner sees "updates stopped at …").
- **Privacy:** the trip ends automatically; the trip's breadcrumb isn't stored by default.
- **Complexity:** M. Tests: arrival detector, cadence policy, trip state machine.
- **Evidence:** [E] Apple, Google.

**O-5. Overdue escalation ("ask me first")**
- **Problem:** silent failure during a trip.
- **Feature:** at "expect me by", the traveller's phone asks "Are you okay? +15 min / I'm safe / Get help". With no answer in 10 min, the partner gets an *Overdue* alert with the last location and its age, battery, and the last connectivity time. The partner's phone independently computes overdue from the trip's server-time deadline, so it still fires if the traveller's phone is dead.
- **MVP:** traveller-side alarm (exact alarm; sideloaded apps can hold `SCHEDULE_EXACT_ALARM`, which must be verified per Android version) plus a partner-side check in the stay-connected service.
- **₹0:** ✓
- **Failure modes:** both phones' services are dead, which the app cannot fix and documents.
- **Privacy:** escalation data is shared only with the partner.
- **Complexity:** M. Tests: time-based state machine with a fake clock.
- **Evidence:** [E] Check In 15-minute flow; Safety Check 60 s warning.

**O-6. Battery and connectivity context**
- **Problem:** P9.
- **Feature:** status includes battery %, charging flag, and "last connected" (server time via `onDisconnect`). Below 15 % battery, the partner sees "Phone at 12 % 🔋, may go quiet soon". A one-time optional "low battery" auto check-in.
- **₹0:** ✓
- **Complexity:** S.
- **Evidence:** [E] Check In Limited data includes battery and signal.

**O-7. Time-boxed sharing ("Share for 1 h / until I arrive / until I stop")**
- **Problem:** pressure to share continuously; P7.
- **Feature:** the share toggle becomes a choice of duration; it auto-stops and shows "Paused" (not "lost") to the partner.
- **₹0:** ✓
- **Complexity:** S.
- **Evidence:** [H] + WhatsApp live-location mental model.

### Category 3 — Emergency preparedness and response

**O-8. SOS state machine with honest delivery receipts** (P0 core)
- **Problem:** P2, P8.
- **Feature:**
  - Hold for 3 s with a visible ring countdown, then a 5 s "Sending in 5… Cancel" window. (Skippable; see open question Q-3.)
  - Raised: write the pair SOS event, send SMS from **this** phone (multipart, GSM-7 text, the location's age), and show per-channel status rows: *AshGo partner: delivered ✓ / not yet* · *SMS Mum: sent ✓ delivered ✓* · *SMS Priya: failed ✗ (no signal), retry*.
  - Cancel requires a deliberate hold and is recorded with who and when.
- **MVP:** as above, with no automatic retries beyond one; manual retry buttons.
- **Dependencies:** `SEND_SMS` (on sideloaded Android 15, a restricted setting the user must allow [E]).
- **₹0:** ✓ (carrier cost disclosed).
- **Failure modes:** no SMS permission (show it explicitly, plus "Call 112" fallback buttons); no network (SMS still works); no location (send "location unavailable").
- **Privacy:** only the configured contacts.
- **Complexity:** M–L. Tests: reducer, fake `SmsSender`, idempotency.

**O-9. SOS from either person** (symmetry): the partner can also raise SOS. Included in O-8.

**O-10. Fast but safe entry points**
- **Feature:** a Quick Settings tile and a home-screen widget, both opening the countdown activity (which also shows over the lock screen); a long-press app-shortcut "SOS". Remove the volume-key accessibility hack [C].
- **₹0:** ✓
- **Complexity:** S–M.
- **Risk:** opening an activity over the lock screen needs `showWhenLocked`, to be verified on a device.

**O-11. Real fake call**
- **Feature:** pick a caller name and delay (now, 10 s, 1 min, 5 min) → a full-screen ringing UI with ringtone and vibration; answering plays the user's own recording through the earpiece (or silence).
- **Dependencies:** a full-screen-intent notification (Android 14 restricts `USE_FULL_SCREEN_INTENT` for Play apps, but sideloaded is fine; still verify) or launching the activity directly if foreground.
- **₹0:** ✓
- **Complexity:** M.
- **Evidence:** [H] widely used pattern in safety apps.

**O-12. "Call now" shortcuts in the SOS screen** for the partner and local emergency services (112 in India): one tap opens the dialer, never auto-dials. **₹0:** ✓. **Complexity:** XS.

**O-13. Partner-side SOS takeover screen**
- **Feature:** when an SOS arrives, a full-screen red takeover appears with last location and age, battery, "Acknowledge" (writes an ack so the sender sees "Partner has seen this"), Call, and Open map.
- **₹0:** ✓
- **Complexity:** M.

### Category 4 — Connectivity, offline, failure recovery

**O-14. Truthful presence model** (P0 core)
- **Problem:** P1, P3, P7.
- **Feature:** one derived state per person, computed from server timestamps:

  | State | Meaning |
  |---|---|
  | **Live** | location < 2 min old |
  | **Recent** | < 15 min |
  | **Stale** | older, shown with an exact age |
  | **Paused** | sharing paused by the user |
  | **Off** | location never shared |
  | **Offline since …** | connection dropped |
  | **Unknown** | no data |

  Each state has a distinct colour and icon plus text, never colour alone.
- **₹0:** ✓
- **Complexity:** S–M. Tests: pure function, table-driven.

**O-15. Safe manual updater** (see the S2 and S5 fixes): public releases, SHA-256 check, no token. **₹0:** ✓. **Complexity:** S–M.

**O-16. Reliability check screen**
- **Feature:** a checklist with live status and one-tap fixes:
  - notification permission and channel state
  - battery-optimisation exemption
  - precise location
  - SMS permission and restricted-settings guidance
  - emergency contacts present
  - "stay connected" service running
  - last successful sync time
  - a test-notification button (local only)
- **Why:** turns silent platform failure (P10) into visible, fixable state.
- **₹0:** ✓
- **Complexity:** M.

### Category 5 — Personalisation

**O-17. Pair personalisation:** display names, avatars (≤ 30 KB), relationship label (Partner / Family / Best friends / Trusted person) that switches microcopy tone, an optional pet-name greeting ("Good morning, sunshine"), and an accent choice within the palette. No hardcoded names [C]. **₹0:** ✓. **Complexity:** S–M.

**O-18. "Our page" (optional):** the existing About-us photo ropes, rebuilt to use photos picked from the device, stored locally only and never uploaded. Keeps the sentiment, fixes S3. **₹0:** ✓. **Complexity:** S.

**O-19. Little moments:** anniversaries or "days together" counter on Home (local); a gentle "you both checked in today" sparkle. **[H]**; risk of becoming engagement bait, so keep it passive with no streaks. **Complexity:** XS.

### Category 6 — Privacy, consent, control

**O-20. Secure pairing with safety words** (P0 core)
- **Feature:**
  1. Person A taps "Create our orbit" and gets a one-time invite (a 10-character code plus QR, expires in 10 min).
  2. Person B scans or types the code.
  3. Both screens show the same **4-word/emoji "orbit words"** derived from both IDs, and each confirms they match.
  4. The rules then lock the pair to those two UIDs.
- **Why:** proves the right person joined (it defends against a shoulder-surfed code), and it's cute.
- **₹0:** ✓
- **Complexity:** M–L. Tests: Emulator rules tests.
- **Evidence:** [E-ish] Signal-style safety numbers; [H] for the emoji form.

**O-21. Leave orbit / unlink:** either member can leave. This deletes the pair's shared data after confirmation and revokes the partner's access. Device change works through a re-invite. **₹0:** ✓. **Complexity:** M.

**O-22. Retention controls:** Settings shows exactly what is kept and for how long (chat 7 days, photos 3 days, check-ins 7 days, SOS 30 days, location: latest point only, cleared on pause if chosen), with "Delete our chat now". **₹0:** ✓. **Complexity:** S–M.

**O-23. End-to-end encryption of chat and location** (Tink, keys exchanged during QR pairing)
- **Why:** Firebase and the project owner can't read content.
- **Cost:** recovery and device change get hard; quota impact is small.
- **₹0:** ✓ (library)
- **Complexity:** L.
- **Verdict:** P2. Don't claim it until it is built.

### Category 7 — Battery, performance

**O-24. Adaptive location cadence:** about 15 s on a trip, about 5 min idle with balanced power, paused when paused, fused "significant movement" only. Removes the always-on 2–5 s high-accuracy polling [C]. **₹0:** ✓. **Complexity:** S. Tests: the policy is a pure function.

### Category 8 — Onboarding, accessibility

**O-25. Just-in-time permissions with explanations:** no batch request at launch [C]. Each permission is requested when its feature is first used, with a one-screen "why" and a "not now" path that leaves the rest of the app working. **₹0:** ✓. **Complexity:** S–M.

### Category 9 — Reliability, transparency

**O-26. Activity log ("What happened"):** a short, local-plus-pair timeline: "10:02 Trip started · 10:31 Arrived · 10:31 Home safe sent ✓ seen ✓ · 22:14 Updates stopped (phone offline)". Helps users trust the app and debug. **₹0:** ✓. **Complexity:** M. Risk: becomes a surveillance log, so keep it to 7 days and events only, no locations.

### Considered and rejected / deferred

| Idea | Reason |
|---|---|
| Automatic SOS on dead signal | False alarms; dead signal is common. Use overdue escalation (O-5) instead. |
| Fall or crash detection | Unreliable without OEM sensors and models; risk of false confidence. |
| Live tracking web link for emergency contacts | Needs hosting plus auth for a public link; Firebase Hosting is free, but a secure public live view adds attack surface. Defer. |
| Automatic place alerts (Life360-style geofences) | Needs `ACCESS_BACKGROUND_LOCATION`, encourages monitoring. Defer; trips cover the need consensually. |
| Location history / breadcrumbs | Privacy cost. At most a P2 opt-in per trip, deleted at trip end. |
| Read receipts on every chat message | Engagement pressure. Show "seen" only for check-ins and SOS, where it matters for safety. |
| Streaks and daily-reply mechanics | Explicitly prohibited (emotional pressure). |
| Wear OS / tablets | Out of scope for ₹0 and testing resources. |

## 6. Shortlist and priority (summary; scored in FEATURE_OPPORTUNITY_MATRIX.md)

- **P0 (trustworthy v2.0):**
  - O-14 truthful presence, including O-6 battery context and O-7 pause / time-boxed sharing
  - O-20 secure pairing (+ O-21 leave)
  - O-8/9/12/13 SOS state machine
  - notification-channel fix (bug, not opportunity)
  - O-1 check-ins
  - O-25 just-in-time permissions
  - O-24 adaptive cadence
  - O-15 safe updater
  - O-16 reliability check
  - O-22 retention (basic)
  - O-17 personalisation basics (names, avatars, no hardcoding)
  - safe widget (part of O-10)
- **P1:** O-4 trips, O-5 overdue, O-3 nudges, O-10 QS tile, O-11 real fake call, O-2 status line, O-18 Our page.
- **P2:** O-23 E2EE, O-26 activity log, O-19 little moments, the Cloudflare FCM relay, MapLibre migration.
- **Remove:** volume-key accessibility SOS, custom admin notifications, token updater, vehicle field as a standalone setting, analytics, storage SDK, dead code.

## 7. Product principles

1. **Never claim more than we know.** Every location shows its age; every send shows its real delivery state.
2. **Sharing is a choice with an end.** Time-boxed or trip-bound by default; pausing is never punished.
3. **Help is one deliberate action away.** SOS is fast and unmistakable, but can't be fired by a pocket.
4. **Ask the person first, then the partner.** Escalate automatically only after the person had a chance to respond.
5. **Two people, both equal.** Either member can share, check in, nudge and raise SOS.
6. **Warm, not needy.** No streaks, no guilt, no forced replies; affectionate copy only when the pair chose it.
7. **Red means danger.** Emergency surfaces drop decoration; the playful brand never softens them.
8. **₹0, forever.** No billing account, ever. If a feature needs one, we don't ship it.
9. **The platform decides background behaviour.** We measure it, show it, and help the user fix it, rather than hiding it.

## 8. What AshGo deliberately won't do
- Continuous high-accuracy tracking by default
- Location history
- Monitoring without the person's knowledge
- Automatic emergency-services contact
- Claiming push delivery or encryption it doesn't have
- Selling or analysing data

## 9. Why AshGo beats "just share location in WhatsApp"
- **Truthful freshness and presence.** WhatsApp shows a dot; AshGo shows Live / Stale / Paused / Offline-since, plus battery.
- **Arrival and overdue escalation** without either person remembering to text.
- **An SOS that reports what actually happened**, including SMS that works with no internet.
- **A private two-person space** with no ads, no contacts graph, and no data business.

## 10. Sources
- Firebase pricing: https://firebase.google.com/pricing
- Cloud Storage Spark change FAQ: https://firebase.google.cn/docs/storage/faqs-storage-changes-announced-sept-2024
- FCM HTTP v1 send: https://firebase.google.com/docs/cloud-messaging/send/v1-api
- OSM tile usage policy: https://operations.osmfoundation.org/policies/tiles/
- Android FGS timeouts: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android FGS background-start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android 15 restricted settings (secondary): https://9to5google.com/2024/09/12/android-15-sideloaded-apps-restrictions/ · https://androidauthority.com/android-15-restricted-settings-sideloading-3481098
- Apple Check In (secondary): https://www.macstories.net/stories/ios-17s-check-in-feature-simplifies-making-sure-friends-and-family-get-home-safely/ · https://tidbits.com/2023/10/09/ios-17s-check-in-feature-provides-peace-of-mind-and-could-even-save-lives/
- Google Safety Check: https://blog.google/products/pixel/google-pixel-phone-watch-safety-features/ · https://androidcentral.com/how-to-use-personal-safety-app-stay-safe-alert-emergency-contacts
- Life360 data sale: https://themarkup.org/privacy/2023/06/01/life360-sued-for-selling-location-data
- SMS segment encoding: https://www.twilio.com/docs/glossary/what-sms-character-limit.md · https://docs.surge.app/concepts/sms-segments
- osmdroid archived: GitHub API `osmdroid/osmdroid` → `archived: true`, last push 2024-11-20
- Cloudflare Workers limits: https://developers.cloudflare.com/workers/platform/limits/
