# AshGo v2 — Architecture Proposal

Status: **design approved in conversation 2026-10-13; this written spec awaits owner review.**
Inputs: CURRENT_STATE_AUDIT, SECURITY_PRIVACY_AUDIT, PRODUCT_DECISIONS (D-1 Google Sign-In, D-6 hold-to-send SOS, D-14 retention), build approach **A** (a new v2 tree in the same module, staged, with one cutover release).

## 1. Goals and non-goals

**Goals**
- Authorization enforced by Realtime Database rules, not by the client.
- One source of truth per piece of state. Composables only render state and emit actions.
- Honest, server-time-based freshness and delivery states.
- Domain logic that is unit-testable on the JVM.
- ₹0: Spark plan only, no server code.

**Non-goals**
- Multi-module, Hilt, Room, or end-to-end encryption (P2).
- Groups larger than two people.
- iOS.

## 2. Constraints carried into the design

| Constraint | Consequence |
|---|---|
| Spark plan: no Functions, no FCM sender, no Storage | Alerts come from on-device listeners (`StayConnectedService`). Photos are bounded Base64 in RTDB. |
| Android 14+: a location FGS can't be created from the background without background-location permission | `LocationShareService` starts only from user actions. No `AlarmManager` restart tricks. |
| `dataSync` FGS is limited to 6 h / 24 h when targeting API 35+ | The listener uses the `specialUse` type with a subtype property. |
| Sideloaded distribution | Restricted settings for SMS (Android 15). Reliability screen guidance. |
| Existing installs are debug-key signed | Release builds are signed with the same key (D-9). `applicationId` stays `com.varad.unstoppableash`. |

## 3. Package layout (single `:app` module)

```
com.varad.unstoppableash
├── AshGoApplication.kt          creates AppContainer, registers notification channels
├── AppContainer.kt              manual DI: repositories, clocks, stores, dispatchers
├── MainActivity.kt              v1 until cutover; then hosts AshGoNavHost only
│                                v1 sources stay in place (not moved) until the cutover deletes them
├── SosWidgetProvider.kt         keeps this exact FQCN in v2, so widgets already placed on home screens survive the update
├── core/
│   ├── time/ServerClock.kt      .info/serverTimeOffset → now(); interface Clock for tests
│   ├── result/Outcome.kt        sealed Loading | Success | Failure(reason)
│   └── coroutines/Dispatchers.kt
├── domain/                      pure Kotlin, no android.* or firebase imports
│   ├── model/                   Member, Pair, Profile, Presence, LocationFix, Trip, ChatMessage,
│   │                            MediaRef, OrbitEvent, SosEvent, SmsReceipt, Relationship
│   ├── presence/PresenceCalculator.kt
│   ├── sos/SosStateMachine.kt   reducer: (SosState, SosAction) -> SosState
│   ├── location/CadencePolicy.kt, ArrivalDetector.kt
│   ├── trips/OverdueEvaluator.kt
│   ├── retention/RetentionSelector.kt
│   ├── media/ImageBudget.kt
│   └── copy/Microcopy.kt        relationship-aware strings (keys → resources)
├── data/
│   ├── firebase/DbPaths.kt      THE only place paths are spelled
│   ├── firebase/Mappers.kt      DataSnapshot ↔ domain
│   ├── auth/AuthRepository.kt   Credential Manager + Firebase Auth; anonymous→Google linking
│   ├── pair/PairRepository.kt   create, invite, join, confirm, leave
│   ├── presence/PresenceRepository.kt   onDisconnect, heartbeat, battery
│   ├── location/LocationRepository.kt   FusedLocationProviderClient wrapper + writes
│   ├── chat/ChatRepository.kt   messages, media, read markers, idempotent send
│   ├── events/EventRepository.kt        check-ins, nudges, seen receipts
│   ├── sos/SosRepository.kt     backend half of SOS
│   ├── trips/TripRepository.kt
│   ├── local/SettingsStore.kt   DataStore (theme, greeting, notification prefs, last-notified ids)
│   ├── local/EmergencyContactsStore.kt  DataStore; never uploaded
│   ├── local/ActiveSosStore.kt  persists an in-flight SOS across process death
│   └── legacy/LegacyImporter.kt one-time v1 → v2 import
├── service/
│   ├── StayConnectedService.kt  FGS specialUse; bounded listeners → Notifier
│   ├── LocationShareService.kt  FGS location; CadencePolicy-driven
│   ├── sms/SmsDispatcher.kt     interface; AndroidSmsDispatcher (divideMessage + multipart + PendingIntents)
│   ├── sms/SmsResultReceiver.kt
│   ├── notify/Channels.kt, Notifier.kt
│   ├── sos/SosHoldActivity.kt, PartnerSosActivity.kt   showWhenLocked/turnScreenOn
│   ├── sos/SosTileService.kt    (widget provider stays at the root package, see above)
│   ├── trips/OverdueAlarmReceiver.kt
│   ├── fakecall/FakeCallActivity.kt, FakeCallScheduler.kt
│   ├── update/UpdateChecker.kt  public GitHub Releases + SHA-256
│   ├── work/CleanupWorker.kt, HeartbeatWorker.kt (WorkManager)
│   └── boot/BootReceiver.kt     restarts StayConnectedService only
├── ui/
│   ├── theme/                   Color.kt, Type.kt (Nunito), Shape.kt, Spacing.kt, Theme.kt
│   ├── components/              OrbitButton, StatusPill, PresenceRing, OrbitCard, OrbitTextField,
│   │                            OrbitDialog, OrbitSheet, EmptyState, LoadingOrbit, ErrorBanner,
│   │                            HoldToConfirmButton, OfflineBar
│   └── nav/AshGoNavHost.kt, Destinations.kt
├── feature/
│   ├── onboarding/   home/   map/   chat/   safety/   trips/   settings/
│   │   each: XScreen.kt (stateless), XViewModel.kt, XUiState.kt
```

**Layering rules**
- `feature → data, domain, ui`; `service → data, domain`; `data → domain, core`; `domain → nothing`.
- Composables never import `com.google.firebase.*`, so a grep test enforces it.
- ViewModels expose `StateFlow<XUiState>` and receive actions via functions. Screens use `collectAsStateWithLifecycle`.
- Repositories expose `Flow`s built with `callbackFlow` and `awaitClose { removeEventListener }`, so a listener cannot leak past its collector.

## 4. State ownership

| State | Owner (single source of truth) | Consumers |
|---|---|---|
| Signed-in user, pairId | `AuthRepository` + `PairRepository` (`userPairs/{uid}`) | all |
| Partner presence | RTDB `presence/{uid}` + `location/{uid}`, interpreted only by `PresenceCalculator` | Home, Map, Chat header, service |
| My sharing mode | `LocationRepository` (written to presence) | Home, Map, service |
| Active SOS (mine) | `ActiveSosStore` (local, survives death) + RTDB `sos/{id}`; reduced by `SosStateMachine` | Safety, SosActive, Home |
| Chat | RTDB `chat`, `limitToLast(100)` + paging | Chat, unread badge |
| Unread / last-read | RTDB `profiles/{uid}/lastReadAt` (server time) | badge, service |
| Emergency contacts, fake-call voices | Local DataStore only | Safety |
| Notification dedupe | `SettingsStore.lastNotified[kind]` | service |

## 5. Data model

```
userPairs/{uid}             pairId (string)   — not under legacy `users/`, which phase 1 keeps open to any signed-in client
invites/{emailKey}/{pairId} { fromUid, fromName, fromEmail (= inviter's token email), createdAt, expiresAt }   one invite per inviting pair, so none can block another
                            emailKey = lowercase(email).replace(".", ",")
pairs/{pairId}/
  meta                      { memberA, memberB?, relationship, createdAt, confirmedAt? }
  profiles/{uid}            { name ≤40, avatar ≤30 KB b64, email, lastReadAt, greeting:boolean }
  presence/{uid}            { online, lastSeen, heartbeatAt, battery, charging,
                              sharing: live|paused|off, sharingUntil?, status? {text ≤60, until} }
  location/{uid}            { lat, lng, acc, at }
  trips/{uid}               { state: active|arrived|ended|overdue, dest? {lat,lng,label},
                              expectBy?, note? ≤80, notifyPartner, startedAt, endedAt?, extendedTo? }
  chat/{msgId}              { from, text ≤2000, at, mediaId? }            msgId = client UUID (idempotent)
  media/{mediaId}           { from, data ≤205000 chars (≈150 KB), w, h, at }
  events/{eventId}          { from, type: checkin|nudge, kind, text?, at,
                              deliveredTo/{uid}: at, seenBy/{uid}: at }
  sos/{sosId}               { from, at, state: active|cancelled, lat?, lng?, locAt?, acc?,
                              battery?, smsSent, smsFailed, cancelledAt?,
                              deliveredTo/{uid}: at, ackBy/{uid}: at }
```

All `at`, `lastSeen` and similar timestamps are written as `ServerValue.TIMESTAMP` and validated as `now`.

**Queries are bounded:**
- `chat.limitToLast(100)`
- `events.orderByChild('at').startAt(now-7d)`
- `sos.limitToLast(5)`

Indexes: `.indexOn: ["at"]` on chat, events, sos and media.

## 6. Security rules (behavioural spec; JSON written and emulator-tested in implementation)

Helper: `isMember = root.child('pairs/'+$pairId+'/meta/memberA').val() === auth.uid || ... memberB ...`

| Path | Read | Create | Update | Delete |
|---|---|---|---|---|
| `userPairs/$uid` | self | self, value must be a pair the user is a member of | same | self |
| `invites/$k/$pairId` | inviter; the verified Google invitee lists `invites/$k` | Google-verified memberA of `$pairId` while memberB is empty; `fromEmail == auth.token.email`; `expiresAt ≤ now+24h` | inviter re-invites | inviter or invitee |
| `pairs/$p/meta` | members | Google-verified creator becomes `memberA`, `memberB` absent | `memberB`: set once by a Google-verified account, `== auth.uid`, an unexpired invite exists at `invites/<own email>/$p` **and** its `fromUid` is the current memberA. `confirmedAt`: memberA only | — |
| `pairs/$p` (whole) | members | — | — | members (Leave orbit = delete the pair) |
| `profiles/$uid` | members | own; `email == auth.token.email` | same | same |
| `presence/$uid`, `location/$uid`, `trips/$uid` | members, **after `meta/confirmedAt` exists** | `$uid == auth.uid` | same | same |
| `chat/$id`, `media/$id` | members after confirmation | `from == auth.uid`, `at == now`, size limits | ✗ (immutable) | sender, or anyone when `at < now − retention` (cleanup) |
| `events/$id` | members after confirmation | `from == auth.uid`, `at == now`; no receipts at creation | only `deliveredTo/$uid`, `seenBy/$uid` where `$uid == auth.uid ≠ from`, on an event that already exists, value `== now`, set once | as chat |
| `sos/$id` | members after confirmation | `from == auth.uid`, `state == 'active'`, `at == now` | `state → cancelled` + `cancelledAt == now` only by `from`; `smsSent/smsFailed` only by `from`; `deliveredTo/ackBy/$uid` only by that `$uid` | `from` only, and only when `state == 'cancelled'` and `cancelledAt < now − 30d` |
| legacy `/chat`, `/tracking`, `/alerts`, `/buzz`, `/users`, `/app_config`, `/notifications` | phase 1: unchanged (owner's current rules); phase 2: ✗ | | | |

**Implemented** in `firebase/database.rules.json` (phase 1), with 54 emulator tests (after the Stage 3 security review) in `firebase/test/*.test.mjs`. Run them with `cd firebase && npm test`, with `JAVA_HOME` set to a JDK. Receipts (`deliveredTo`/`seenBy`/`ackBy`) can only be added to an already-existing event or SOS, so a sender can't pre-fill "seen" with `serverTimestamp()`.

**Rules tests** (originally planned as `firebase/rules.test.ts`, `@firebase/rules-unit-testing` against the Database emulator) must cover:
- an outsider is denied every read and write
- a third joiner is denied
- an expired invite is rejected
- a join from an unverified email is rejected
- a forged `from` is rejected
- a client-chosen `at` is rejected
- the partner cannot cancel SOS
- the partner can ack SOS
- oversized media is rejected
- chat updates are rejected

## 7. Time
- `ServerClock` subscribes to `.info/serverTimeOffset`; `now() = System.currentTimeMillis() + offset`.
- `PresenceCalculator`, `OverdueEvaluator` and `RetentionSelector` take `now` as a parameter, so they are deterministic and testable.

## 8. Services and platform boundaries

| Component | FGS type / mechanism | Started by | Stops when |
|---|---|---|---|
| `StayConnectedService` | `specialUse` (property: "Partner safety alerts for a private two-person circle") | app open; `BootReceiver`; Reliability "Reconnect" | sign-out / leave orbit / user disables "Stay connected" |
| `LocationShareService` | `location` | user toggles sharing / starts a trip (visible activity) | pause/off; `sharingUntil` reached; trip end + idle policy |
| Overdue prompt | exact alarm (`SCHEDULE_EXACT_ALARM`, fallback inexact + partner-side evaluation) | trip with `expectBy` | trip ends |
| Cleanup, heartbeat backstop | WorkManager periodic (24 h; 15 min) | app start | — |
| Arrival | `ArrivalDetector`: 2 consecutive fixes within max(100 m, accuracy); fixes with accuracy worse than 200 m are ignored | `LocationShareService` during a trip | trip ends |
| SMS | `SmsManager.divideMessage` + `sendMultipartTextMessage` with sent/delivered `PendingIntent`s → `SmsResultReceiver` → `ActiveSosStore` | SOS raise / retry / false-alarm (confirmed) | — |

**Notification channels** (created in `AshGoApplication.onCreate`, idempotent):

| Channel | Importance | Notes |
|---|---|---|
| `sos` | HIGH | alarm sound, full-screen intent, public on the lock screen |
| `overdue` | HIGH | |
| `chat` | HIGH | MessagingStyle |
| `nudges` | DEFAULT | |
| `checkins` | DEFAULT | |
| `presence` | LOW | |
| `service` | MIN | |

The stream volume is never modified.

## 9. Error handling
- Every repository write returns `Outcome`. Failures carry a typed reason: `Offline`, `PermissionDenied`, `QuotaOrServer`, `Invalid`.
- `onCancelled` is always mapped to a `Failure` surfaced via `ErrorBanner`; there are no empty catches.
- RTDB offline queueing is exposed as `Pending` (local write not yet acknowledged, using `setValue` completion), so the UI shows "Waiting for connection".
- Logs never contain message text, coordinates or phone numbers. `Log` calls go through a `SafeLog` that redacts arguments in release builds.

## 10. Migration (approach A, cutover release)

| Phase | Owner action | App behaviour |
|---|---|---|
| 0 | Export the DB JSON and current rules (SECURITY_PRIVACY_AUDIT §7). Back up `~/.android/debug.keystore`. Revoke the GitHub token. | — |
| 1 | Deploy rules v2-phase-1: the new `userPairs/invites/pairs` rules plus the legacy paths enumerated one by one (no root-level grant, `notifications` and `app_config` made client read-only) | v1 keeps working |
| 2 | Both install v2 | Google sign-in with `signInWithCredential`, replacing the v1 anonymous session. Nothing in v1 is keyed by user id, so there is nothing to link, and a fresh session guarantees `sign_in_provider == google.com` for the rules. Local migration of contacts and voices from `AshGoPrefs`. |
| 3 | Pairing completes | memberA's device runs `LegacyImporter` once: last 7 days of `/chat` (sender mapped via memberA's legacy role), `/users/*` names and avatars (downscaled), with a recorded `importedAt` |
| 4 | Deploy rules v2-phase-2 (legacy denied), then delete legacy nodes | — |
| Rollback | Before phase 4: reinstall v1. After: redeploy the phase-1 rules from git. | — |

## 11. Testing strategy

| Layer | Tooling | Examples |
|---|---|---|
| Domain | JUnit4 + kotlinx-coroutines-test, fake `Clock` | presence table (≥ 15 cases), SOS reducer transitions and idempotency, cadence policy, arrival (accuracy + 2 fixes), overdue, retention selector, image budget |
| Data | JVM tests with fake data sources; mapper tests | snapshot ↔ model, legacy role mapping |
| Rules | Firebase Database emulator + `@firebase/rules-unit-testing` (Node, local, free) | §6 list |
| UI | Compose UI tests on the `Pixel_8_Pro` AVD | Home states (live / stale / paused / offline / no data), SOS hold < 3 s sends nothing, chat double-tap sends once, light/dark, font scale 1.3/2.0 |
| Static | lint (0 errors), a grep test for `com.google.firebase` in `feature/**` and `ui/**`, a grep test for tokens and personal names | |
| Device | Manual checklist (`docs/v2/DEVICE_VERIFICATION.md`, created in the plan) | SMS to an owner-controlled number, background survival 24 h, lock-screen activities, exact alarm, boot restart, DND |

## 12. Items requiring device verification (not claimable from the emulator)
- The `specialUse` FGS not being time-limited on API 34–36.
- `BootReceiver` starting a `specialUse` FGS on Android 15+.
- `showWhenLocked` behaviour of the SOS and fake-call activities.
- `SCHEDULE_EXACT_ALARM` availability on a sideloaded install.
- SMS sent and delivered callbacks per carrier.
- OEM battery management.
