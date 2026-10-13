# AshGo v2 — Redesign Spec ("Little Orbit")

Status: **design approved in conversation 2026-10-13; this written spec awaits owner review.**
Companion docs:
- ARCHITECTURE_PROPOSAL (state and data)
- USER_JOURNEYS (flows)
- PRODUCT_DECISIONS (D-6 hold-to-send, D-14 pause keeps the last point)

## 1. Identity
- **Concept:** two small bodies sharing one orbit. Close, but each with its own path.
- **Motif uses:**
  - logo
  - `PresenceRing` on Home (two avatars on an elliptical orbit, a small heart at the closest point)
  - `LoadingOrbit` (two dots circling)
  - empty-state illustrations (orbit + dotted route)
- **Rules:**
  - Hearts appear only as the single orbit "touch point" or in partner-mode microcopy. Never as wallpaper.
  - No glass, no heavy gradients.
  - One soft shadow level, reserved for floating sheets and the SOS button.

## 2. Colour tokens

### Light (default)

| Token | Hex | Role | Contrast checked |
|---|---|---|---|
| `background` | `#FFF7F5` | screens | — |
| `surface` | `#FFFFFF` | cards | — |
| `surfaceWarm` | `#FFFBFA` | sheets, inputs | — |
| `primary` | `#8B405F` | primary actions, active nav | 6.63:1 on background |
| `onPrimary` | `#FFFFFF` | text on primary | 7.01:1 |
| `text` | `#382637` | body / titles | 13.24:1 |
| `textSecondary` | `#6E5A6C` | captions, timestamps | 5.95:1 |
| `blush` | `#F6DDE8` | my chat bubble, decoration | text on it 10.95:1 |
| `lavender` | `#EADFF8` | partner bubble, paused accent | text on it 10.94:1 |
| `goodBg` / `goodInk` | `#DFF1E4` / `#2E6B45` | Live, Arrived, Delivered **only** | 5.40:1 |
| `warnBg` / `warnInk` | `#FCEBCF` / `#8A5300` | Stale, Offline, Waiting, Overdue | 5.40:1 |
| `sos` / `onSos` | `#B3261E` / `#FFFFFF` | SOS, destructive **only** | 6.54:1 |
| `outline` | `#382637` @ 10 % | card borders | — |

### Dark (designed, not inverted)

| Token | Hex | Contrast checked |
|---|---|---|
| `background` | `#1E151D` | — |
| `surface` | `#2A2029` | text on it 13.18:1 |
| `primary` | `#F2B3CB` | 10.24:1 |
| `onPrimary` | `#3A1426` | 9.24:1 |
| `text` | `#F3E8F1` | 14.93:1 |
| `textSecondary` | `#CBB8C8` | 9.51:1 |
| `lavender` | `#D9C8F2` | 11.43:1 |
| `goodInk` | `#9FD3B0` | 10.52:1 |
| `warnInk` | `#F4C27A` | 10.88:1 |
| `sos` button | `#D32F2F` with white text | 4.98:1 |
| `sosText` | `#FFB4AB` | 10.48:1 |

Contrast was computed with the WCAG 2.x relative-luminance formula. All text pairs are ≥ 4.5:1.

A grey `#8C7F8B` was rejected for stale data (3.60:1). Stale data uses `warn` instead.

Theme mode: follows the system by default; Settings offers System / Light / Dark. Dynamic colour is off, to keep the brand.

## 3. Typography: Nunito (SIL OFL 1.1, bundled in `res/font`, OFL.txt in assets)

| Role | Size / line | Weight | M3 slot |
|---|---|---|---|
| Display | 32 / 40 sp | 800 | `displaySmall` |
| Headline | 26 / 32 sp | 700 | `headlineSmall` |
| Title | 22 / 28 sp | 700 | `titleLarge` |
| Title small | 18 / 24 sp | 700 | `titleMedium` |
| Body | 16 / 24 sp | 400 | `bodyLarge` |
| Body small | 14 / 20 sp | 400 | `bodyMedium` |
| Label | 14 / 20 sp | 600 | `labelLarge` |
| Caption | 12 / 16 sp | 400 | `bodySmall` / `labelSmall` |
| Emergency | 28 / 34 sp | 800 | custom `SosType` |

- All sizes are in `sp`, so system font scaling is respected. Layouts are verified at 1.3× and 2.0× font scale.
- No text is ever drawn below 12 sp.

## 4. Shape, spacing, elevation

| Property | Values |
|---|---|
| Corner radii | `small` 12 dp (chips, inputs); `medium` 20 dp (cards); `large` 28 dp (sheets, dialogs); `full` (pills, avatars, FAB) |
| Spacing scale | 4, 8, 12, 16, 24, 32, 48 dp (`Spacing.xs…xxl`); screen gutter 16 dp; card padding 16 dp |
| Elevation | 0 everywhere, with 1 dp `outline` borders on cards; 3 dp + soft shadow for sheets; 6 dp for the SOS button only |
| Touch targets | ≥ 48 dp; SOS hold target 120 dp |

## 5. Components

| Component | Variants / API | States | Accessibility |
|---|---|---|---|
| `OrbitButton` | Primary / Secondary (outlined) / Quiet (text); `loading` flag | enabled, pressed (scale 0.98 + darker), disabled (38 % alpha), loading (LoadingOrbit inline; click ignored) | role Button; label = text |
| `StatusPill` | `PresenceState` → icon + colour + text | Live ●, Recent ◉, Stale ◐, Paused ‖, Offline ○, NotSharing ◌ (dashed), Unknown ? | `contentDescription` = full sentence, e.g. "Sam: stale, last update 40 minutes ago" |
| `PresenceRing` | two avatars, a ring tinted by partner state | animates only on state change (200 ms) | merged semantics: "You and Sam. Sam is live." |
| `OrbitCard` | default / tappable | pressed ripple | — |
| `OrbitTextField` | single / multi-line, counter | focused (primary 2 dp), error (sos ink + message) | label, never placeholder-only |
| `OrbitDialog`, `OrbitSheet` | — | — | focus trapped; dismiss labelled |
| `EmptyState` | illustration + title + body + optional action | — | illustration decorative (null description) |
| `LoadingOrbit` | small / large | — | "Loading" live region |
| `ErrorBanner` | message + Retry | — | polite live region |
| `OfflineBar` | thin warn bar | — | "No connection, showing last known information" |
| `HoldToConfirmButton` | duration (3 s SOS, 1.5 s cancel), colours | idle, holding (ring progress + haptic tick each second), completed (strong haptic), released early (hint text) | TalkBack: custom action "Send SOS" requires a double-tap-and-hold equivalent. Announces "Hold for 3 seconds"; progress announced at 1 s and 2 s. |
| `ReceiptRow` | label + state icon + time + optional Retry | queued / pending / done ✓ / failed ✗ / unknown | full sentence |
| `AvatarImage` | Base64 or initials fallback on blush | — | name |

## 6. Motion
- Allowed:
  - `LoadingOrbit`
  - 200 ms cross-fade on status change
  - hold-ring progress
  - sheet slide
  - bubble appear (100 ms fade)
- Not allowed:
  - pulsing SOS (the v1 breathing animation is removed)
  - confetti
  - looping decoration on Home
- `Settings.Global.ANIMATOR_DURATION_SCALE == 0` disables all non-essential motion.

## 7. Microcopy system
- `Microcopy` maps keys to string resources by `Relationship` (Partner / Family / Friends / Trusted) and the `greeting` toggle.
- **Safety, SOS, overdue and permission screens use one neutral set, regardless of relationship.**
- **No hardcoded names.** `{partner}` and `{me}` come from profiles.
- **No gendered pronouns.** Phrases use the name or "they".

| Key | Partner | Family | Friends / Trusted |
|---|---|---|---|
| greeting.evening | Good evening, love | Good evening | Hey you |
| checkin.thinking | Thinking of you 💗 | Thinking of you | Thinking of you |
| nudge.callme | Call me when you're free? | Call me when you're free? | Call me when free? |
| empty.chat | Say hi 💌 | Say hi | Say hi 👋 |

## 8. Navigation graph

```
Launch ─► (signed out) Onboarding: Welcome → GoogleSignIn → CreateOrJoin
                         ├─ Create: Profile → InvitePartner → Waiting → ConfirmPartner ─┐
                         └─ Join:   InvitationFound → ConfirmPartner ───────────────────┤
       ─► (signed in, no pair) CreateOrJoin                                              │
       ─► (paired) Main ◄────────────────────────────────────────────────────────────────┘
Main (bottom bar): Home │ Map │ Chat │ Safety
  Home → Settings (avatar) → Profile | Orbit | Sharing | Notifications | EmergencyContacts |
                              Reliability | PrivacyData | Appearance | Updates | Help | OurPage | About
  Home/Map → TripSheet
  Safety → EmergencyContacts | FakeCallSetup | Reliability
Standalone activities (showWhenLocked): SosHold, SosActive, PartnerSos, FakeCall
Deep links from notifications: chat → Chat; sos → PartnerSos; overdue → Map(partner); checkin → Home
```

Back behaviour: the bottom-bar tabs keep their own back stacks. Back on Home exits. SosActive cannot be dismissed by Back while the SOS is active; it returns to it.

## 9. Screens and states

Every screen defines **Loading, Content, Empty, Offline, Error.**
- Offline = `OfflineBar` over the last known content.
- Error = `ErrorBanner` with Retry. Permission errors explain what is blocked.

### Onboarding
| Screen | Content |
|---|---|
| Welcome | Three swipeable cards; primary "Continue with Google". |
| Profile | name (prefilled from Google), avatar (Google photo, downscaled to 256 px), relationship chips. |
| InvitePartner | email field with validation; "Invite valid 24 h"; inviter can cancel the invite. |
| Waiting | `LoadingOrbit` + "Waiting for {partner email} to join"; options to edit or cancel. |
| InvitationFound | inviter's avatar, name, email; Join / Not now. |
| ConfirmPartner | the other person's avatar + email; "Yes, that's them" / "That's not them" (revokes). |

States: no network (blocking message, nothing half-created); "no invite for this account" (shows the signed-in email, with Switch account).

### Home
- **Layout:** as the Section 3 wireframe. Greeting; PresenceRing; partner name; StatusPill; battery; coarse place; status line; map preview or trip card; quick actions (Check in, Nudge, Trip); "You: <sharing state> · Pause/Resume"; a separated SOS row (red outline, "Hold 3 s for help": tap opens Safety, a hold sends).
- **States:**
  - partner Live / Recent / Stale / Paused (faded last point, "Paused · last seen …") / Offline since / Not sharing / never shared
  - partner SOS active: Home is replaced by a red banner at the top linking to PartnerSos
  - my SOS active: a banner linking to SosActive
  - partner on a trip / overdue
  - my sharing interrupted ("Sharing was interrupted at … · Resume?")
- **Check-in sheet:** presets plus custom (≤ 60 chars). The result shows inline with ReceiptRows (Saved, Delivered, Seen).

### Map
- osmdroid view.
- Markers: me (berry dot with white ring), partner (avatar marker), destination (flag).
- The accuracy circle is drawn when accuracy exceeds 30 m.
- Stale and paused markers are drawn at 45 % alpha, with the age label.
- Controls: "Show both" and "Centre on {partner}" FABs (48 dp).
- Attribution "© OpenStreetMap contributors" stays visible at the bottom-left at all times.
- Bottom sheet: partner status and trip progress; my sharing controls; Start trip.
- Gesture: long-press sets **my** trip destination and opens the TripSheet prefilled.
- Error states: tiles failing ("Map tiles unavailable", markers and text still shown); location permission off; GPS off ("Turn on location" → system settings).

### Trip sheet
- **Fields:** destination (saved places / map pick / none), expect-by time picker, note (≤ 80), "Tell {partner} I've started" toggle (default on).
- **Buttons:** Start → becomes I'm here / End trip / +15 min.

### Chat
- **Header:** avatar, name, StatusPill.
- **List:**
  - reverse layout anchored to the newest message
  - day separators
  - bubbles: mine on blush (right), partner's on lavender (left), radius 20 dp with a 6 dp tail corner
  - time under each message group
  - my send status: 🕓 / ✓ / ⚠ Retry
- **Inline event chips:** check-ins and nudges, centred, caption style.
- **Photos:** max width 240 dp; tap for fullscreen; an over-budget photo is refused with "Photo too large even after compression".
- **Composer:** 📷, ⚡, text field (multiline up to 5 lines), Send (disabled when blank or in-flight).
- **IME:** `imePadding`; the list stays pinned to the bottom when already at the bottom.
- **Paging:** load older messages on scroll to the top.
- **Empty:** illustration + "Say hi 💌".

### Safety (neutral copy; no hearts, no decoration)
- `HoldToConfirmButton` 120 dp, SOS red, "Hold for 3 seconds to send SOS".
- **"What happens":** a list built from real state: partner alert (paired ✓ / not paired ✗), SMS to N contacts (or "SMS off: fix"), location (on / approximate / off).
- **Buttons:** Call {partner}, Call 112 (both open the dialer).
- **Rows:** emergency contacts, fake call, reliability summary.

### SosActive (SOS red header, white surface below for legibility)
- **Title:** "SOS sent" with server time.
- **Primary action:** `HoldToConfirmButton` 1.5 s "I'm safe: cancel SOS".
- **ReceiptRows:**
  - Saved to AshGo
  - Delivered to {partner}'s phone
  - {partner} has seen it
  - one row per SMS contact (sent / delivered / failed + Retry / unknown)
  - location ("Location from 2 min ago" / "Updating…" / "Unavailable")
- **Call buttons.**
- **After cancel:** "Cancelled at …". The false-alarm SMS confirmation lists the recipients and states that SMS charges may apply.

### PartnerSos
- Full red: "{partner} needs help".
- Last location map snippet + age, battery, time raised.
- Buttons: **Acknowledge** (primary, white), Call {partner}, Open map, "Call 112".

### Overdue (warn colours)
"{partner}'s trip is {n} min overdue". Last location and age, battery, last connection, Call, Raise SOS for them, Dismiss for 15 min.

### FakeCall
Mimics a generic incoming-call screen (no OEM branding): caller name, "Mobile", Accept / Decline. Accepting plays the chosen audio on the earpiece with a call timer; Decline ends it.

### Settings sub-screens
- **Profile:** name, photo, greeting toggle.
- **Orbit:** partner info, relationship label, Leave orbit (destructive; the confirmation lists the deletions).
- **Sharing:** default pause duration, trip-start notification default, battery-saver cadence explanation.
- **Notifications:** per-kind toggles; SOS is not toggleable in-app (link to the system channel).
- **EmergencyContacts:** list, add via picker (no `READ_CONTACTS`), remove, SMS cost note.
- **Reliability:** checklist rows with status + Fix action (see USER_JOURNEYS J12); "Send test notification" (local).
- **PrivacyData:** retention table (D-14), "Delete our chat now", "Export my data" (P2).
- **Appearance:** System / Light / Dark.
- **Updates:** current version, Check for update → found (version, size, notes) → Download (progress) → Verify (SHA-256) → Install (system prompt); each failure is shown.
- **Help:** status meanings, reliability tips, SMS costs, privacy summary.
- **OurPage:** photo ropes using locally picked photos (never uploaded).
- **About:** version, licences (Nunito OFL, osmdroid Apache-2.0, OSM attribution).

## 10. Notifications (visual)
- **Small icon:** monochrome orbit glyph (replaces the `android.R.drawable` placeholders).
- **Accent colour:** `primary`, except `sos` (red) and `overdue` (amber).
- **Copy:**
  - chat: "{partner}: {first 60 chars}"
  - nudge: "{partner}: Thinking of you 💗"
  - check-in: "{partner} is home safe ✓ · 10:31 pm"
  - SOS: "{partner} needs help: tap to see location"
  - overdue: "{partner}'s trip is overdue"
  - service: "Keeping you connected with {partner}"

## 11. Accessibility checklist (acceptance)
- All interactive elements are ≥ 48 dp with labels. Decorative images have null descriptions.
- State is never conveyed by colour alone (icon + text).
- Screens are usable at 2.0× font scale (scroll allowed except on SosHold/SosActive, which reflow).
- TalkBack: logical traversal order on Home (partner status first, then actions, then SOS); the hold-to-send button exposes a custom accessibility action.
- Focus visible in both themes; dialogs trap focus.
- Reduced motion respected.

## 12. Assets
- `res/font/nunito_{regular,semibold,bold,extrabold}.ttf` + `assets/licenses/OFL.txt`.
- Vector drawables: `ic_orbit_logo`, `ic_orbit_glyph` (notifications), `ill_empty_chat`, `ill_no_partner`, `ill_not_sharing`, `ill_offline`. These are original works.
- Removed from the APK: personal photos, the personal voice recording, `brick_wall.jpg`, `retro_tv.jpg`, `raw/fake_call.wav` (unused), `ic_snapchat.xml` (third-party brand mark).
- Launcher icon: an adaptive icon with a monochrome layer (fixes lint `MonochromeLauncherIcon`).
