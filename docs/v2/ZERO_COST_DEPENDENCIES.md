# AshGo — Zero-Cost Dependency Register

**Policy:**
- The budget is ₹0.
- A service that needs a billing account or payment method is **rejected**, even if usage would stay inside a free tier.
- Every external service and library must appear here before it is introduced.

Last verified: 2026-10-13. Re-verify pricing before each release.

## 1. External services

| Service | Purpose | Plan | Free limits (official source) | Billing account or card required? | Behaviour at limit | Cost risk | Status |
|---|---|---|---|---|---|---|---|
| **Firebase Realtime Database** | Pair data: presence, location, chat, SOS, trips | Spark | 1 GB stored · 360 MB/day download (≈10 GB/mo) · **100 simultaneous connections** · one database per project ([firebase.google.com/pricing](https://firebase.google.com/pricing)) | **No** | Spark cannot incur charges. Over-quota behaviour isn't stated on the pricing page; assume the service is restricted until the quota resets. **Design for ≤ 10 % of quota.** | None, while on Spark. **Never upgrade the project to Blaze.** | Keep |
| **Firebase Authentication** | Per-person identity | Spark | "Non-phone" providers are free. Phone/SMS auth is **not** available on Spark. 50 K MAU applies with Identity Platform. | **No** | n/a for 2 users | Phone auth would need Blaze, so it is **excluded**. | Keep (provider to be decided, D-1) |
| **Firebase Cloud Messaging** | Push delivery | Free | No numeric quota published | No | — | — | **Blocked**: sending requires a service-account OAuth token from a trusted server ([FCM v1 docs](https://firebase.google.com/docs/cloud-messaging/send/v1-api)). Spark has no Cloud Functions. A service-account key must never ship in the APK. See §3. |
| **Firebase Cloud Functions** | — | — | Not available on Spark | Blaze only | — | — | **Rejected** |
| **Cloud Storage for Firebase** | (was: photos) | — | Not available on Spark. Default buckets require Blaze since the Sept 2024 change ([FAQ](https://firebase.google.cn/docs/storage/faqs-storage-changes-announced-sept-2024)) | Blaze only | Spark calls return 402/403 | — | **Rejected**. Remove the unused `firebase-storage` dependency. |
| **Firebase Analytics** | none | — | free | No | — | Privacy cost, no value | **Remove** (unused) |
| **OpenStreetMap standard tiles** (`tile.openstreetmap.org`) | Map display | Community, no SLA | No numeric limit. Heavy use may be blocked without notice. **Must** show attribution, send a distinct User-Agent, honour caching (≥ 7 days if headers are unavailable). **No bulk prefetch, no offline use** ([tile policy](https://operations.osmfoundation.org/policies/tiles/)) | No | Blocked or slow tiles | None monetary; availability risk | Keep, **add attribution**, set an identifying UA, never prefetch |
| **OpenFreeMap** (candidate) | Vector tiles with MapLibre | Community | Claims no limits, no keys, no registration; provided as-is (openfreemap.org, from secondary search results; not yet read first-hand) | No | May change or stop | Availability | **Evaluate later** (D-7) |
| **Android Geocoder** | Coarse address text | Platform | Backed by Google Play services on most devices; no key | No | Returns empty; app falls back to coordinates | None | Keep (optional, coarse) |
| **GitHub Releases (public)** | APK distribution | Free | Public release assets need no token | No | — | None | **Adopt**. Replaces the token-based private download. |
| **Carrier SMS** | SOS SMS to emergency contacts | User's mobile plan | Depends on the plan | n/a | Carrier refusal or no signal | **May cost money on the user's plan.** Each emoji-bearing segment holds about 70 characters, and every segment is billed. The v2 SMS text will be GSM-7 only (no emoji) to minimise segments. | Keep; disclose in the UI |
| **Cloudflare Workers** (candidate push relay) | Hold the FCM credential server-side to enable real push | Free plan | 100 K requests/day, 10 ms CPU ([limits](https://developers.cloudflare.com/workers/platform/limits/)). Marketing says no card is needed; the technical docs don't state it. | Reportedly no; **unverified** | Requests fail after quota | Moves a project credential to a third party | **Deferred P2.** Only adopt if the owner verifies no card is required. |

## 2. Libraries and assets

| Item | Licence | Purpose | Cost | Status |
|---|---|---|---|---|
| AndroidX (Compose, Material3, Activity, Lifecycle, Navigation, Core) | Apache-2.0 | UI and architecture | Free | Keep, update versions |
| `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | Apache-2.0 | ViewModels, `collectAsStateWithLifecycle` | Free | **Add** |
| AndroidX WorkManager | Apache-2.0 | Daily cleanup, heartbeat | Free | **Add** (proposed) |
| AndroidX DataStore | Apache-2.0 | Typed local settings | Free | **Add** (proposed) |
| Firebase Android SDK (BoM) | Apache-2.0 | Database, Auth | Free SDK | Keep, update |
| Google Play services location | Proprietary SDK, free to use (Android SDK terms) | Fused location | Free | Keep |
| osmdroid 6.1.18 | Apache-2.0 | Map | Free; **archived upstream 2024-11-20** | Keep for v2.0; revisit (D-7) |
| Coil | Apache-2.0 | Image loading | Free | Keep only if needed for Base64 bitmaps; otherwise remove |
| kotlinx-coroutines | Apache-2.0 | Concurrency | Free | Keep |
| JUnit 4, kotlinx-coroutines-test, Turbine (proposed) | EPL-1.0 / Apache-2.0 / Apache-2.0 | Tests | Free | Add test-only |
| **Nunito** typeface | SIL OFL 1.1 (OFL.txt shipped at `assets/licenses/OFL.txt`) | Brand type | Free; bundling permitted; the font can't be sold on its own | **Adopted 2026-10-14.** Variable font `fonts/variable/Nunito[wght].ttf` from github.com/googlefonts/nunito (276 932 B, SHA-256 `bb55a5ca…1e85207`) bundled as `res/font/nunito_variable.ttf`; one file serves weights 400/600/700/800. No runtime fetching. |
| Material Symbols / Icons | Apache-2.0 | Icons | Free | Keep. Prefer specific icons over the 30 MB extended set once R8 is enabled. |
| Firebase Emulator Suite (`firebase-tools`) | MIT (CLI), emulators free | Rules tests, local only | Free, no project billing | **Adopt** for rules tests |
| Custom vector illustrations (orbit, heart, route) | Original work in this repo | Brand accents | Free | Create in-house |

### Pinned versions (Task 0.1, 2026-10-13)

| Item | Version | Licence |
|---|---|---|
| Kotlin + Compose compiler plugin | 2.4.21 (was 2.2.10) | Apache-2.0 |
| Compose BOM | 2026.09.00 | Apache-2.0 |
| Firebase BoM (auth, database) | 35.0.0 | Apache-2.0 |
| AndroidX Core / Activity / Lifecycle / Navigation | 1.19.1 / 1.13.0 / 2.11.0 / 2.10.2 | Apache-2.0 |
| DataStore Preferences / WorkManager | 1.2.1 / 2.12.0 | Apache-2.0 |
| Credentials (+ play-services-auth) / googleid | 1.6.0 / 1.2.1 | Apache-2.0 |
| Play services location | 21.4.0 | Android SDK terms (free) |
| kotlinx-coroutines (play-services, test) | 1.11.0 | Apache-2.0 |
| osmdroid-android | 6.1.18 (upstream archived) | Apache-2.0 |
| coil-compose | 2.6.0 (v1 only; removed at cutover) | Apache-2.0 |
| google-services Gradle plugin | 4.5.0 | Apache-2.0 |

Removed: `firebase-analytics`, `firebase-storage`.

The Kotlin upgrade was required because Firebase BoM 35 and `googleid` 1.2.1 ship Kotlin 2.4 metadata, which Kotlin 2.2 cannot read.

## 3. The push-notification constraint (most important cost finding)

Without a server, one phone **cannot** send an FCM push to the other. Options:

| Option | Cost | Reliability | Privacy | Verdict |
|---|---|---|---|---|
| A. A foreground service on each phone keeps an RTDB listener open (the current approach) | ₹0 | Good while the service lives. OEM battery killers may stop it. Needs a persistent notification. | Best: no third party | **v2.0 default**, plus a "reliability check" that guides battery-optimisation exemption |
| B. Cloudflare Worker relay holding the FCM service-account key | ₹0 if no card is required (unverified) | High-priority FCM can wake the app | The relay sees metadata. The key lives off-device. | P2, owner must verify the terms |
| C. Public ntfy.sh or UnifiedPush distributor | ₹0 | Varies | Third party; topic names act as secrets | Rejected for SOS; possible P2 for nudges |
| D. SMS as the push channel for SOS only | Carrier cost | Works without internet | Carrier | **Keep** as the SOS fallback, not for everyday use |

## 4. Quota budget for two people (estimate)

| Flow | Size per event | Events per day | Download per day |
|---|---|---|---|
| Location while on a trip (15 s cadence, 2 h/day) | ~250 B incl. overhead | 480 | ~0.12 MB |
| Idle presence and location (5 min cadence, 22 h) | ~250 B | 264 | ~0.07 MB |
| Text messages | ~400 B | 200 | ~0.08 MB |
| Photos (≤ 120 KB, each downloaded by one partner) | 120 KB | 10 | ~1.2 MB |
| Reconnect re-syncs (bounded queries) | — | — | < 2 MB |
| **Total** | | | **< 5 MB/day vs 360 MB/day quota** |

The current design's main risk is unbounded `keepSynced("chat")` plus multi-MB Base64 avatars being re-fetched. v2 removes both.
