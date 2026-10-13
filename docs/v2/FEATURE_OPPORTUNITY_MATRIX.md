# AshGo v2 — Feature Opportunity Matrix

## Scoring method

Each criterion is scored 1–5, where 5 is always the favourable end (e.g. Effort 5 = easiest, Privacy 5 = most protective). The weighted maximum is **100**.

| Criterion | Weight | Criterion | Weight |
|---|---|---|---|
| Severity of problem (Sev) | 3 | Ease under stress (Str) | 2 |
| Avoidable risk / confusion reduced (Risk) | 3 | Privacy / consent (Priv) | 2 |
| Frequency (Freq) | 2 | Effort, inverse (Eff) | 1 |
| Practical usefulness (Use) | 2 | Battery / data / storage (Bat) | 1 |
| Reliability in real conditions (Rel) | 2 | Few external dependencies (Dep) | 1 |
| | | Testability (Test) | 1 |

**₹0 compliance is a gate, not a score.** A "?" in the gate column means the item cannot ship until the owner verifies it.

Scores rank the work; they do not decide it. Where judgement overrides the number, the reason is written in the Priority column.

## Scores

| ID | Capability | Sev | Freq | Use | Risk | Rel | Str | Priv | Eff | Bat | Dep | Test | **Score** | ₹0 | Priority |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| B-1 | Fix notification channels (bug) | 5 | 5 | 5 | 4 | 5 | 4 | 5 | 5 | 5 | 5 | 4 | **94** | ✓ | **P0** |
| O-14 | Truthful presence model | 5 | 5 | 5 | 5 | 4 | 4 | 5 | 4 | 5 | 5 | 5 | **95** | ✓ | **P0** |
| O-1 | Quick check-ins with honest delivery | 3 | 5 | 5 | 3 | 4 | 5 | 5 | 5 | 5 | 5 | 5 | **86** | ✓ | **P0**: cheap, daily value, reuses the event model |
| O-8/9/12/13 | SOS state machine, symmetric, receipts, partner takeover | 5 | 1 | 5 | 5 | 4 | 5 | 4 | 2 | 5 | 4 | 4 | **83** | ✓ (carrier cost disclosed) | **P0** |
| O-5 | Overdue escalation, asking the person first | 5 | 2 | 5 | 5 | 3 | 5 | 4 | 2 | 4 | 4 | 4 | **82** | ✓ | **P1**: depends on Trips, and exact-alarm behaviour needs device verification |
| O-16 | Reliability check screen | 4 | 3 | 4 | 4 | 5 | 3 | 5 | 3 | 5 | 5 | 3 | **80** | ✓ | **P0** |
| O-20 | Secure pairing + orbit words | 5 | 1 | 4 | 5 | 4 | 3 | 5 | 2 | 5 | 4 | 4 | **79** | ✓ | **P0**: everything else is unsafe without it |
| O-7 | Time-boxed sharing / pause | 3 | 4 | 4 | 3 | 4 | 4 | 5 | 4 | 5 | 5 | 5 | **79** | ✓ | **P0**: folded into the presence work, since pause is part of truthful state |
| O-4 | Trips (destination, expect-by, note) | 4 | 4 | 5 | 4 | 3 | 4 | 4 | 2 | 3 | 4 | 4 | **77** | ✓ | **P1** |
| O-15 | Safe manual updater, no token | 4 | 1 | 3 | 5 | 4 | 3 | 5 | 4 | 5 | 4 | 4 | **76** | ✓ | **P0**: removes the S2 exposure |
| O-6 | Battery + connectivity context | 3 | 4 | 4 | 3 | 4 | 4 | 3 | 5 | 5 | 5 | 5 | **76** | ✓ | **P0**: part of the presence payload; trivial to add |
| O-24 | Adaptive location cadence | 3 | 5 | 4 | 2 | 4 | 3 | 4 | 4 | 5 | 5 | 5 | **74** | ✓ | **P0** |
| O-3 | Nudge (Thunder reborn) | 2 | 5 | 4 | 2 | 4 | 4 | 4 | 4 | 5 | 5 | 5 | **73** | ✓ | **P1**: emotionally central to the couple, so it ships in the same release as P0 if time allows |
| O-10 | QS tile + safe widget + lock-screen hold screen | 4 | 1 | 4 | 4 | 3 | 5 | 4 | 3 | 5 | 4 | 3 | **73** | ✓ | **P1**. The safe-widget part is **P0**, because the current widget is unsafe. |
| O-25 | Just-in-time permissions | 3 | 2 | 4 | 3 | 4 | 3 | 5 | 4 | 5 | 5 | 4 | **72** | ✓ | **P0** |
| O-2 | Status line | 2 | 4 | 4 | 2 | 4 | 4 | 4 | 5 | 5 | 5 | 5 | **72** | ✓ | **P1** |
| O-21 | Leave orbit / unlink | 3 | 1 | 3 | 4 | 4 | 3 | 5 | 3 | 5 | 5 | 4 | **70** | ✓ | **P0**: required for honest consent |
| — | Cloudflare FCM relay | 4 | 5 | 4 | 4 | 3 | 3 | 3 | 2 | 5 | 1 | 2 | 70 | **?** | **Defer**: blocked by the ₹0 gate until the owner verifies no card is needed |
| O-22 | Retention controls | 3 | 2 | 3 | 3 | 3 | 3 | 5 | 4 | 5 | 5 | 5 | **69** | ✓ | **P0** (basic) |
| O-11 | Real fake call | 3 | 1 | 4 | 2 | 4 | 4 | 5 | 3 | 5 | 4 | 3 | **66** | ✓ | **P1** |
| O-23 | End-to-end encryption | 3 | 5 | 2 | 3 | 3 | 3 | 5 | 1 | 4 | 4 | 2 | **65** | ✓ | **P2** |
| O-17 | Personalisation (names, avatars, label, greeting) | 2 | 5 | 4 | 1 | 3 | 3 | 4 | 3 | 5 | 5 | 4 | **64** | ✓ | **P0**: removing the hardcoded names is required. Greetings and accent are P1. |
| O-19 | Little moments (days together) | 1 | 3 | 3 | 1 | 5 | 3 | 5 | 5 | 5 | 5 | 5 | **64** | ✓ | **P2** |
| — | MapLibre + OpenFreeMap | 2 | 5 | 3 | 2 | 4 | 3 | 4 | 2 | 4 | 3 | 3 | 62 | ✓ | **P2** |
| O-26 | Activity log | 2 | 3 | 3 | 2 | 4 | 3 | 3 | 3 | 5 | 5 | 4 | **61** | ✓ | **P2** |
| O-18 | Our page (local photos only) | 1 | 2 | 3 | 1 | 5 | 3 | 5 | 4 | 5 | 5 | 4 | **60** | ✓ | **P1**: low cost, high sentiment, and it fixes S3 |

## Decisions and acceptance criteria

Every row below assumes these **evidence types** apply:
- **[unit]** JVM unit test
- **[rules]** Firebase Emulator rules test
- **[UI]** Compose UI test on an emulator
- **[device]** real-device manual verification, recorded in the release report

| ID | Decision | Acceptance criteria (observable) | Evidence |
|---|---|---|---|
| B-1 | Add | Every notification posts to a channel created at app start. Channels: `sos` (high, alarm-style), `checkins`, `chat`, `nudges`, `presence` (default), `service` (low). Unit-testable channel registry. A logcat check on the emulator shows no "No Channel found". | [unit] [device] |
| O-14 | Add | `presenceOf(snapshot, serverNow)` returns the correct state for a table of ≥ 15 cases, including no data, paused, and clock skew. The UI never renders "Live" unless the state is Live. | [unit] [UI] |
| O-1 | Add | One tap creates exactly one event (idempotent client ID). The sender's UI moves *Sending → Saved → Seen* only on the corresponding backend facts. Offline shows "Waiting for connection". | [unit] [rules] [UI] |
| O-8 | Redesign | Raise is impossible without a ≥ 3 s continuous hold (D-6). A second raise during an active SOS is a no-op. SMS is dispatched once per contact via the fake sender. Each contact row reflects its sent/delivered/failed callback. Cancel records the actor and server time. Status is consistent on both phones. | [unit] [rules] [UI] [device: SMS to an owner-controlled number] |
| O-20 | Add | Only invite holders can join, and only within 10 min. A pair can never exceed 2 members. Non-members are denied read and write on every pair path. Orbit words match on both devices. | [rules] [unit] [device] |
| O-21 | Add | After "Leave", the leaver loses access (rules) and shared nodes are deleted. The partner sees "Orbit ended". | [rules] [UI] |
| O-7 | Add | Pausing writes `sharing=paused` and keeps the last point (D-14), rendered faded with "Paused · last seen <time>". The partner sees Paused, and no signal-lost alert fires. | [unit] [UI] |
| O-6 | Add | Battery % and charging state are included at most once per 5 % change. A low-battery hint appears at < 15 %. | [unit] |
| O-24 | Add | The cadence policy returns the documented interval per state; the location request is updated on state change. | [unit] [device: battery over 24 h] |
| O-15 | Replace | The updater fetches only from an allow-listed public URL. A SHA-256 mismatch aborts and deletes the file. No token exists anywhere (grep test in CI). | [unit] [device] |
| O-16 | Add | Each check reflects real system state and has a fix action. "Send test notification" shows a notification on every channel. | [UI] [device] |
| O-25 | Add | A fresh install requests no permission before the user opens the feature that needs it. Denial leaves the other features working. | [UI] [device] |
| O-22 | Add | The cleanup selector (pure function) excludes unresolved SOS events and items newer than the cutoff. It runs at most once per 24 h per device. | [unit] [rules] |
| O-17 | Add | No real name, nickname or gendered pronoun exists in shared code or strings (grep check). Relationship label switches the microcopy set. | [unit] [UI] |
| O-10 (widget) | Redesign | A widget tap opens the SOS hold screen; nothing is written unless a 3 s hold completes. | [UI] [device] |
| Volume-key SOS | **Remove** | Service and manifest entries removed. Release notes explain why and what replaces it. | build + lint |
| Admin custom notifications | **Remove** | Listeners and paths removed. | build |
| Firebase Analytics / Storage SDKs | **Remove** | Not on the runtime classpath; APK size reduced. | build |
| Standalone vehicle field | **Fold into Trips** | Trip note persists for the trip only. | [unit] |
