# AshGo v2 — User Journeys

These journeys describe the **proposed** v2 product.

- **Today:** what the current app does in the same moment, from the audit.
- **Friction / failure:** what can go wrong.
- **Mitigation:** how v2 handles it.

People are called **A** (sets up the orbit) and **B** (joins). Either one can be "the one travelling".

---

## J1. Install and understand
1. B receives the APK link from A and installs it. Android shows its "unknown sources" prompt; the app's Help explains this.
2. First launch shows three calm cards, skippable after the first:
   - "AshGo is a tiny private space for two."
   - "You choose what to share and for how long."
   - "Help is one deliberate press away."
3. **No permission is requested yet.**

| Today | Friction / failure | Mitigation |
|---|---|---|
| Five permissions requested at once, then "Traveler or Receiver?" | Users deny everything and features silently fail. | Just-in-time permissions (O-25); a Reliability screen shows what is off. |

## J2. Create or join a private orbit
1. A taps **Create our orbit**, sets their name and an optional avatar, and picks a relationship label (Partner / Family / Best friends / Trusted person).
2. A sees a QR code plus a 10-character code: "Valid for 10 minutes · single use".
3. B taps **Join an orbit** and scans or types the code.
4. **Both** screens show the same four orbit words (e.g. 🌙 maple · ⭐ river · 🍓 cloud · 🪐 pebble). Each taps **They match**.
5. The orbit is sealed. Home shows the partner with "Connected just now".

| Today | Friction / failure | Mitigation |
|---|---|---|
| None. Any install sees all data. | The code expires before use. | One-tap "new code". |
| | Wrong person joins (a shared screenshot). | Orbit words don't match, so tap "They don't match" → the pair is dissolved and the invite revoked. |
| | No internet. | "We need a connection to create your orbit." Nothing is half-created. |
| | A third phone tries to join. | Rules reject it: "This orbit is already complete." |

## J3. Verify the right person joined
Covered by orbit words (J2.4).
- **Later:** Settings → Orbit shows the partner's name, when they joined, and their device's last connection. The orbit words can be re-checked at any time.

## J4. Configure sharing and permissions (just in time)

| Action | Permission asked | Explanation shown | If denied |
|---|---|---|---|
| Turn on sharing / start a trip | Location (precise preferred) → Notifications | "B will see where you are while sharing is on. You can pause anytime." | Sharing stays off. Home shows "Location off. Tap to set up." Chat, check-ins and SOS-without-location still work. |
| Add emergency contacts | None (system picker) | "These people get an SMS if you raise SOS. Normal SMS charges may apply." | — |
| First SOS setup | SMS | "So SOS can text your contacts even without internet." On a sideloaded Android 15 install there are "Allow restricted settings" steps with a deep link. | SOS still alerts B in-app; the SOS screen says "SMS off". |
| Stay connected | Notifications + battery-optimisation exemption (optional) | "Lets AshGo alert you when B needs you, even when the app is closed. It shows a small permanent notification." | Alerts arrive only when the app is open, and Home says so. |

## J5. An ordinary day on Home
- **Top card:** B's avatar, name, presence pill (*Live · 1 min ago*, *Paused*, *Offline since 14:02*), battery, status line ("In class till 4"), and coarse place text.
- **Quick row:** Check in · Chat (badge) · Map · Nudge ⚡.
- **Bottom:** a quiet SOS entry that is always visible but visually separate.
- **Nothing to scroll for the key facts.**

| Today | Friction / failure | Mitigation |
|---|---|---|
| "Live Tracking Active" with no data; a fake city marker. | Data is 40 minutes old. | The pill reads "Stale · last update 40 min ago" in amber with an icon. The map marker is faded with its age. |

## J6. Start and end a journey
1. A taps **Start trip**.
2. Picks a destination from saved places, the map, or none.
3. Chooses "Expect me by 22:30" (optional), adds a trip note ("Cab KA-01-AB-1234", optional), then taps **Start**.
4. Cadence rises. B is notified once: "A started a trip to Home · expected 22:30".
5. A arrives: within max(100 m, accuracy) for two fixes → the trip ends → "Home safe ✓" check-in → B gets one gentle notification.
6. A can end the trip manually at any time: **I'm here** / **Cancel trip**.

| Friction / failure | Mitigation |
|---|---|
| GPS drift near home | Accuracy-aware arrival rule with two consecutive fixes. |
| The OS kills A's service mid-trip | B sees "Updates stopped at 22:05 (A's phone stopped responding)". A gets an in-app notice on next open: "Your trip was interrupted". |
| Battery dies | B sees "Phone was at 4 % 🔋 at 22:03 · offline since 22:05". The escalation timer still runs on B's phone (J9). |

## J7. Messages and check-ins
- **Check-in:** one tap; the status moves *Sending → Saved ✓ → Seen ✓*, each step only when it is true.
- **Chat:** messages show sending, sent and failed states, with a retry on failure. Double taps never duplicate. Photos are compressed off the main thread (≤ 120 KB); larger ones are refused with an explanation.
- **Nudge:** choose *Thinking of you* / *Call me when free* / *Are you okay?*. Limited to one per 30 s ("Give it a moment 💗").

## J8. Connectivity interruption
- **A goes offline:** RTDB queues the writes. A's UI shows "Waiting for connection" on pending items. B sees "Offline since 18:12" (via `onDisconnect`), **not** an alarm.
- **On reconnect:** queued items send, statuses update, and B sees fresh presence.

## J9. Missed update during a trip (overdue)
1. 22:30 passes and A hasn't arrived. A's phone asks: "Are you okay?" with **+15 min**, **I'm safe**, **Get help**.
2. No answer in 10 minutes → B gets an **Overdue** alert (urgent, but not the SOS red) showing the last location and its age, battery, last connection, and Call A / Open map / Raise SOS for A.
3. If A's phone is dead, B's own service computes the overdue from the trip deadline and alerts anyway.
4. **AshGo never auto-calls emergency services.**

## J10. Emergency: raising SOS and understanding what succeeded
1. A presses and holds SOS (on Home, the widget, the QS tile, or the notification) for 3 s; a ring fills.
2. A full red screen appears: "Sending SOS in 5… **Cancel**".
3. **Raised.** The screen shows live receipts:
   - ✓ Saved to AshGo (server time 22:41:07)
   - ✓ B has seen your SOS (22:41:20) — or "B's phone hasn't confirmed yet"
   - ✓ SMS to Mum: sent · delivered
   - ✗ SMS to Priya: failed (no signal) **Retry**
   - Buttons: Call B · Call 112 · Cancel SOS (hold)
4. **B:** a full-screen takeover with A's last location and its age, battery, Acknowledge (sends "seen"), Call A, and Map.
5. **Cancel:** A holds Cancel → "SOS cancelled by A at 22:45" on both phones. B **cannot** cancel A's SOS, but B can mark it "I'm handling it".

| Today | Friction / failure | Mitigation |
|---|---|---|
| A toast says "SOS TRIGGERED" regardless. SMS is sent only if sharing is on, possibly with 0,0 coordinates, and probably fails because of UCS-2 length. | No SMS permission | Explicit "SMS off" row, plus Call buttons. |
| | No location | SMS says "Location unavailable". |
| | No internet | SMS still goes; the AshGo row shows "Waiting for connection". |
| | Accidental press | The countdown with Cancel. |

## J11. Pause, change device, leave
- **Pause:** "Pause sharing for 1 h / until I turn it back on". B sees **Paused**. No alarms fire.
- **Change device:** on the new phone, tap **Join an orbit** → B's app shows "A wants to join from a new device. Approve?" → orbit words → the old device is removed. *(This depends on the D-1 identity decision.)*
- **Leave:** Settings → Orbit → Leave. The confirmation lists exactly what will be deleted. The partner sees "Orbit ended".

## J12. Recovering from errors
- The **Reliability** screen lists each failing item with a fix button:
  - notifications blocked
  - a channel muted
  - battery optimisation on
  - location approximate only
  - SMS restricted
  - service not running
- **Sync status** in Settings shows the "last successful sync" time.
- An update failure shows the reason (network / checksum / install cancelled) and keeps the current version intact.

## Alternative paths summary

| Constraint | What still works |
|---|---|
| Location denied | Chat, check-ins, nudges, SOS (no location) + SMS |
| Notifications denied | Everything in-app. Home banner: "You won't be alerted when the app is closed." |
| No internet | SOS SMS, local UI, queued messages |
| Low battery / battery saver | Presence shows battery. Cadence drops automatically below 15 %. |
| OEM kills the service | Reliability screen guidance. The partner sees "stopped responding", never a fake Live. |
| User wants chat only | Sharing stays off permanently; Home shows "Not sharing location" neutrally. |
