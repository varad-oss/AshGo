# AshGo 2.0 — device verification checklist

Run on **two real phones** (yours and your partner's), both currently on v1, before the releases repo goes public.
Tick each line; anything that fails stops the rollout. Nothing here needs a paid service.

## 0. Before installing (owner)
- [ ] Export a JSON backup of the Realtime Database (Firebase console → Realtime Database → ⋮ → Export JSON). Keep it offline.
- [ ] Copy the current rules from the console into a dated file outside the repo.
- [ ] The phase-1 rules (`firebase/database.rules.json`) are deployed (done 2026-10-14; see SECURITY_PRIVACY_AUDIT §8).
- [ ] Back up `~/.android/debug.keystore`: it signs every AshGo release, and losing it means no more in-place updates.

## 1. Upgrade in place (each phone)
- [ ] Install `app-release.apk` (2.0.0 / 46) over v1. Android shows **Update**, not "App not installed" (same signer).
- [ ] The home-screen icon still opens AshGo (the `.MainActivity` alias).
- [ ] A placed v1 SOS widget still exists; tapping it opens the **hold** screen and sends nothing by itself.
- [ ] Settings → Emergency contacts shows the v1 contacts (imported once).
- [ ] Safety → Fake call lists v1 recordings that are still readable (others are skipped quietly).

## 2. Sign-in and pairing
- [ ] Continue with Google on both phones; one creates an invite, the other joins with the code; both confirm.
- [ ] A third Google account cannot join the same orbit.

## 3. Presence, chat, map
- [ ] Each phone shows the other as Live within ~2 min; airplane mode on one → the other shows Recent, then Stale with "last update …".
- [ ] Messages arrive both ways; ✓ Sent, ✓✓ Delivered, Seen appear only when true. A photo sends and shows on the other phone.
- [ ] Share live → the other phone's map shows the pin; Pause 1 h keeps the last point faded; Stop removes it.
- [ ] Start a trip with a destination 500 m away; walk there → "Arrived" check-in reaches the partner.
- [ ] A trip with a deadline 2 min out, not arrived → overdue card on both phones after the 10-min grace.

## 4. Notifications with the app closed
- [ ] Swipe AshGo away on phone B. A message from A → notification on B within a minute.
- [ ] Reboot phone B without opening AshGo → the "Connected" notification returns and alerts still arrive.
- [ ] Settings → Reliability: every row green (or fixed with its button), including battery and full-screen alerts.

## 5. SOS (the real-SMS test; tell your emergency contacts first)
- [ ] Add **one** willing contact only. Hold SOS 3 s on phone A.
- [ ] Phone B (locked) wakes with the full-screen partner SOS; Acknowledge → A sees "Seen by …".
- [ ] The contact receives one SMS with a map link; A shows **Delivered** only after the network report.
- [ ] Cancel the SOS → B's alert clears; "False alarm" text reaches the contact.
- [ ] Quick Settings tile → opens the hold screen (no send on tap).

## 6. Updates (after the releases repo is public)
- [ ] Publish a test build: `latest_version_code`, `apk_url` (github.com/varad-oss/AshGo-releases/…), `sha256` in `app_config`.
- [ ] Settings → Updates → Check → Download → the system installer opens.
- [ ] Change one character of `sha256` → "didn't match its checksum, so it was refused"; nothing installs.

## 7. Privacy
- [ ] Settings → Privacy & data → Delete my messages → your messages and photos are gone on both phones; theirs stay.
- [ ] Leave orbit (test accounts only) → everything shared is deleted for both.

## 8. After both phones run 2.0 (owner)
- [ ] Deploy `firebase/database.rules.phase2.json` (legacy paths closed; 59 emulator tests pass).
- [ ] Delete the legacy nodes `chat`, `tracking`, `alerts`, `buzz`, `users`, `notifications` in the console.
- [ ] Only then make `AshGo-releases` public, and revoke the old GitHub token that v1 shipped.
