# AshGo v2 "Little Orbit" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild AshGo as a private, pair-scoped, honest-status safety app (Little Orbit) inside the existing `:app` module, then cut over in one release that upgrades v1 installs in place.

**Architecture:**
- v2 code lives in new packages (`core/ domain/ data/ service/ ui/ feature/`) beside the untouched v1 sources until the cutover stage.
- Pure-Kotlin domain logic is built and tested first.
- Then come the design system, Firebase rules (emulator-tested), repositories, and features.
- `MainActivity` switches to the v2 nav host only at cutover (Stage 11).

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (BOM), Material3, AndroidX Lifecycle/ViewModel, DataStore, WorkManager, Credential Manager + `googleid`, Firebase Auth + Realtime Database (Spark), Play services location, osmdroid 6.1.18, JUnit4 + kotlinx-coroutines-test, Firebase Database emulator + `@firebase/rules-unit-testing` (Node).

**Spec:**
- `docs/v2/ARCHITECTURE_PROPOSAL.md`
- `docs/v2/REDESIGN_SPEC.md`
- `docs/v2/PRODUCT_DECISIONS.md` (D-1 Google Sign-In, D-6 hold-to-send SOS, D-9 signing, D-14 retention)
- `docs/v2/USER_JOURNEYS.md`
- `docs/v2/FEATURE_OPPORTUNITY_MATRIX.md` (acceptance criteria)

**Structure of this plan:**
- Stages 0–2 are specified task-by-task below.
- Stages 3–11 are a roadmap with deliverables and acceptance criteria. Each gets its own detailed task plan (appended to this file) before it starts, because their interfaces depend on code written in Stages 0–2.

## Global Constraints

- **₹0:** no billing account, no Blaze plan, no Cloud Functions/Storage, no paid SDKs. Every new library is recorded in `docs/v2/ZERO_COST_DEPENDENCIES.md` with its licence.
- **Identity:** `applicationId`/`namespace` stay `com.varad.unstoppableash`; minSdk 26; targetSdk 34 (raising it is a separate, later decision); compileSdk 37.
- **Signing:** release builds sign with the existing debug key (`signingConfigs.getByName("debug")`) per D-9. No keystore or password is committed.
- **Widget FQCN:** `com.varad.unstoppableash.SosWidgetProvider` keeps its exact class name.
- **v1 code is not edited** in Stages 0–10 except where a task says so (Stage 0 build hygiene only). The app must build after every task.
- **Layering:** `domain/` imports nothing from `android.*`, `androidx.*` or `com.google.firebase.*`. `ui/` and `feature/` never import `com.google.firebase.*`.
- **No hardcoded personal names, nicknames or gendered pronouns** in v2 code or strings.
- **Timestamps:**
  - written as `ServerValue.TIMESTAMP`
  - domain functions take `now: Long` as a parameter
  - presence thresholds: Live < 120 000 ms; Recent < 900 000 ms; Offline when disconnected for > 120 000 ms
- **Colours, type and shape:** exact values from REDESIGN_SPEC §2–§4.
- **SOS:** a 3 000 ms continuous hold sends; nothing is sent before 3 000 ms; the cancel hold is 1 500 ms; only the sender can cancel.
- **SMS text:** GSM-7 only (no emoji), sent via `divideMessage` + `sendMultipartTextMessage`. Never "0,0"; the text says "Location unavailable" instead. **No real SMS in automated tests.**
- **Retention (D-14):**

  | Data | Kept for |
  |---|---|
  | chat | 7 d |
  | media | 3 d |
  | events | 7 d |
  | trips | 24 h after end |
  | SOS | 30 d after cancel |
  | active SOS / active trip | never deleted |
- **Logging:** no message text, coordinates or phone numbers in logs.
- **Build command prefix** for every Gradle step: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` and run from the repo root.
- **Downloads** (fonts, npm packages) need the owner's explicit OK in chat before the step runs. The step says so.

## Review Focus

1. **Clock skew / future timestamps:** a partner `location.at` slightly ahead of local server-time `now` must show **Live** (age clamped to 0), never a negative age or a crash. Pinned in Task 1.2.
2. **Partial or legacy snapshots:** missing `battery`, missing `sharing`, or a presence node with no location must map to a defined state (Unknown / NotSharing), never "Live". Pinned in Task 1.2.
3. **Out-of-order or duplicate SMS callbacks:**
   - DELIVERED before SENT counts as delivered.
   - A second SENT after DELIVERED doesn't downgrade.
   - A result for a stale `sosId` is ignored.

   Pinned in Task 1.3.
4. **Imprecise fixes near the destination:** a fix with 2 000 m accuracy must not count as "arrived" just because `max(100, accuracy)` is huge. Fixes with accuracy > 200 m neither count nor reset the streak. This rule is added by this plan and recorded in REDESIGN_SPEC. Pinned in Task 1.4.
5. **Hold released at 2 999 ms, or a rapid re-press:** must not send. A second full hold during an active SOS must not create a second SOS. Pinned in Task 1.3 (reducer) and Task 2.4 (UI).

---

## Stage 0 — Foundations

### Task 0.1: Build hygiene, dependencies, and the lint baseline

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Delete (tracked strays): `test.js`, `patch_receiver.py`, `patch_tracking.py`, `.kotlin/errors/errors-1789260371750.log`
- Modify: `docs/v2/ZERO_COST_DEPENDENCIES.md` (record chosen versions)

**Interfaces:**
- Produces: catalog aliases
  - `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose`
  - `androidx-datastore-preferences`, `androidx-work-runtime-ktx`
  - `androidx-credentials`, `androidx-credentials-play-services-auth`, `googleid`
  - `kotlinx-coroutines-test`
  - plus catalog entries for osmdroid, navigation-compose and material-icons-extended, which are currently hardcoded

- [ ] **Step 1: Record the baseline.** Run:

  ```bash
  ./gradlew --no-configuration-cache assembleDebug testDebugUnitTest lintDebug
  ```

  Expected: assemble and tests PASS; lint FAILS with exactly 1 error, `PermissionImpliesUnsupportedChromeOsHardware`.
- [ ] **Step 2: Fix the lint error.** Add `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to the manifest.
- [ ] **Step 3: Update dependencies.**
  - Remove `firebase-analytics`, `firebase-storage` and `coil-compose` from `app/build.gradle.kts`.
    - First run `grep -rn "coil\|AsyncImage" app/src/main/java`. If the legacy `AsyncImage` in `ReceiverScreen.kt` is still referenced, **keep coil** until Stage 11 and note it.
  - Move the three hardcoded dependencies into the catalog.
  - Add the new aliases above, using the latest **stable** versions from Google Maven / Maven Central at implementation time.
  - Bump the AndroidX and Firebase BoMs to the versions lint reported (`compose-bom 2026.09.00`, `firebase-bom 35.0.0`, `lifecycle 2.11.0`, `activity-compose 1.13.0`, `core-ktx 1.19.1`, `navigation 2.10.2`, `play-services-location 21.4.0`).
- [ ] **Step 4: Release build type.** Set `isDebuggable = false` (the default for release) and `signingConfig = signingConfigs.getByName("debug")`. Keep `optimization { enable = false }`; R8 is enabled in Stage 11.
- [ ] **Step 5: Remove the stray tracked files** with `git rm`. Leave the untracked `AndroidManifest.xml.bak` and the release clones untouched.
- [ ] **Step 6: Verify.** Run:

  ```bash
  ./gradlew --no-configuration-cache assembleDebug assembleRelease testDebugUnitTest lintDebug
  ```

  Expected: BUILD SUCCESSFUL, lint 0 errors.

  Then confirm the release APK has the same signer:

  ```bash
  ~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
  ```

  Expected SHA-1: `431666aef061ddbf09cb398f8a4f51b6cc80edfc`.
- [ ] **Step 7: Record** the chosen versions and licences in ZERO_COST_DEPENDENCIES §2.
- [ ] **Step 8: Commit.** Message: `build: v2 dependency baseline, lint clean, release signed with existing key`.

### Task 0.2: Test harness and layering guard

**Files:**
- Create: `app/src/test/java/com/varad/unstoppableash/LayeringTest.kt`
- Delete: `app/src/test/java/com/varad/unstoppableash/ExampleUnitTest.kt`, `app/src/androidTest/java/com/varad/unstoppableash/ExampleInstrumentedTest.kt`

**Interfaces:**
- Produces: `LayeringTest`, which scans source files under `app/src/main/java/com/varad/unstoppableash/{domain,ui,feature}` (paths resolved from the project dir via `System.getProperty("user.dir")`).

- [ ] **Step 1: Write the failing tests.**
  - `domainHasNoPlatformImports()`: asserts no file under `domain/` contains `import android.`, `import androidx.` or `import com.google.firebase`.
  - `uiAndFeatureHaveNoFirebaseImports()`: asserts the same for `com.google.firebase` under `ui/` and `feature/`.
  - `noPersonalNamesInV2()`: asserts no file under `core/ domain/ data/ service/ ui/ feature/` contains (case-insensitive) any entry from a private list loaded from `app/src/test/resources/forbidden_names.txt`. That file is **git-ignored**; the owner writes it, one name per line. If it is absent, the test is skipped.
  - Also add `app/src/test/resources/forbidden_names.txt` to `.gitignore`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*LayeringTest"`. Expected: FAIL, because the directories don't exist yet. Make the tests pass vacuously when a directory is missing, *but* assert that at least one of `domain/` or `ui/` exists. That assertion is the failure.
- [ ] **Step 3: Create** `domain/` with `package-info`-style placeholder `domain/model/Relationship.kt`:

  ```kotlin
  enum class Relationship { PARTNER, FAMILY, FRIENDS, TRUSTED }
  ```
- [ ] **Step 4: Run** the test again. Expected: PASS.
- [ ] **Step 5: Commit.** Message: `test: layering and personal-name guards`.

---

## Stage 1 — Domain core (pure Kotlin, JVM tests only)

Shared test command:

```bash
./gradlew testDebugUnitTest --tests "com.varad.unstoppableash.domain.*"
```

### Task 1.1: Clock and time helpers

**Files:**
- Create: `core/time/Clock.kt`, `domain/time/Durations.kt`
- Test: `app/src/test/java/com/varad/unstoppableash/domain/time/DurationsTest.kt`

**Interfaces:**
- Produces:
  - `fun interface Clock { fun now(): Long }`
  - `object Durations { const val MINUTE = 60_000L; const val HOUR = 3_600_000L; const val DAY = 86_400_000L }`
  - `fun formatAge(ageMs: Long): AgeText`, where `data class AgeText(val value: Int, val unit: AgeUnit)` and `enum class AgeUnit { JUST_NOW, MINUTES, HOURS, DAYS }`

- [ ] **Step 1: Write the failing tests.**
  - `formatAge(-5_000)` → `AgeText(0, JUST_NOW)`
  - `formatAge(59_999)` → `JUST_NOW`
  - `formatAge(60_000)` → `AgeText(1, MINUTES)`
  - `formatAge(59 * MINUTE + 59_999)` → `AgeText(59, MINUTES)`
  - `formatAge(HOUR)` → `AgeText(1, HOURS)`
  - `formatAge(23 * HOUR + 59 * MINUTE)` → `AgeText(23, HOURS)`
  - `formatAge(DAY)` → `AgeText(1, DAYS)`
- [ ] **Step 2: Run.** Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement.** Floor division; negative input clamps to 0.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat(domain): clock and age formatting`.

### Task 1.2: PresenceCalculator

**Files:**
- Create: `domain/model/Presence.kt`, `domain/presence/PresenceCalculator.kt`
- Test: `domain/presence/PresenceCalculatorTest.kt`

**Interfaces:**
- Consumes: `Durations`.
- Produces:

  ```kotlin
  enum class SharingMode { LIVE, PAUSED, OFF }
  data class PresenceSnapshot(
      val online: Boolean?, val lastSeen: Long?, val heartbeatAt: Long?,
      val battery: Int?, val charging: Boolean?, val sharing: SharingMode?,
      val locationAt: Long?, val accuracyM: Float?,
  )
  sealed interface PresenceState {
      data class Live(val ageMs: Long) : PresenceState
      data class Recent(val ageMs: Long) : PresenceState
      data class Stale(val ageMs: Long) : PresenceState
      data class Paused(val lastLocationAt: Long?) : PresenceState
      data class Offline(val since: Long) : PresenceState
      data object NotSharing : PresenceState
      data object Unknown : PresenceState
  }
  object PresenceCalculator {
      const val LIVE_MS = 120_000L
      const val RECENT_MS = 900_000L
      const val OFFLINE_GRACE_MS = 120_000L
      fun compute(s: PresenceSnapshot?, now: Long): PresenceState
  }
  ```

- [ ] **Step 1: Write the failing table-driven tests.** Each row is (snapshot → expected); `n = 1_000_000_000L`.

  | # | Snapshot | Expected |
  |---|---|---|
  | 1 | `null` | `Unknown` |
  | 2 | all fields null | `Unknown` |
  | 3 | sharing PAUSED, locationAt n−3h | `Paused(n−3h)` |
  | 4 | sharing PAUSED, locationAt null | `Paused(null)` |
  | 5 | sharing OFF | `NotSharing` |
  | 6 | sharing null, locationAt n−10 s (legacy partner, no sharing field) | `Live(10_000)` |
  | 7 | sharing LIVE, online false, lastSeen n−121 000 | `Offline(n−121_000)` |
  | 8 | sharing LIVE, online false, lastSeen n−60 000, locationAt n−60 000 (within grace) | `Live(60_000)` |
  | 9 | sharing LIVE, online true, locationAt n | `Live(0)` |
  | 10 | locationAt n+5 000 (future, clock skew) | `Live(0)` |
  | 11 | locationAt n−119 999 | `Live` |
  | 12 | locationAt n−120 000 | `Recent(120_000)` |
  | 13 | locationAt n−899 999 | `Recent` |
  | 14 | locationAt n−900 000 | `Stale(900_000)` |
  | 15 | sharing LIVE, locationAt null | `Unknown` |
  | 16 | sharing PAUSED and online false | `Paused` (user intent wins) |
  | 17 | sharing OFF and online false | `NotSharing` |

- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement `compute`.** Precedence:
  1. null → Unknown
  2. PAUSED → Paused
  3. OFF → NotSharing
  4. `online == false && lastSeen != null && now − lastSeen > OFFLINE_GRACE_MS` → Offline(lastSeen)
  5. `locationAt == null` → Unknown
  6. age = `max(0, now − locationAt)`, then thresholds
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat(domain): truthful presence calculator`.

### Task 1.3: SosStateMachine

**Files:**
- Create: `domain/model/Sos.kt`, `domain/sos/SosStateMachine.kt`
- Test: `domain/sos/SosStateMachineTest.kt`

**Interfaces:**
- Produces:

  ```kotlin
  data class SmsTarget(val contactId: String)
  enum class SmsStatus { QUEUED, SENT, DELIVERED, FAILED }
  data class SmsReceipt(val status: SmsStatus, val failure: String? = null)
  enum class BackendStatus { PENDING, SAVED, FAILED }
  sealed interface SosState {
      data object Idle : SosState
      data class Active(
          val sosId: String, val raisedAt: Long, val backend: BackendStatus,
          val serverAt: Long?, val deliveredToPartnerAt: Long?, val partnerAckAt: Long?,
          val sms: Map<String, SmsReceipt>,
      ) : SosState
      data class Cancelled(val sosId: String, val cancelledAt: Long, val sms: Map<String, SmsReceipt>) : SosState
  }
  sealed interface SosAction {
      data class Raise(val sosId: String, val now: Long, val targets: List<SmsTarget>) : SosAction
      data class BackendSaved(val sosId: String, val serverAt: Long) : SosAction
      data class BackendFailed(val sosId: String) : SosAction
      data class SmsResult(val sosId: String, val contactId: String, val status: SmsStatus, val failure: String? = null) : SosAction
      data class PartnerDelivered(val sosId: String, val at: Long) : SosAction
      data class PartnerAcked(val sosId: String, val at: Long) : SosAction
      data class RetryFailedSms(val sosId: String) : SosAction
      data class Cancel(val sosId: String, val now: Long) : SosAction
      data object Reset : SosAction
  }
  sealed interface SosEffect {
      data class WriteBackend(val sosId: String) : SosEffect
      data class SendSms(val sosId: String, val contactId: String) : SosEffect
      data class WriteCancel(val sosId: String) : SosEffect
  }
  data class SosTransition(val state: SosState, val effects: List<SosEffect>)
  object SosStateMachine { fun reduce(state: SosState, action: SosAction): SosTransition }
  ```

- [ ] **Step 1: Write the failing tests.**
  - `raiseFromIdle_queuesBackendAndOneSmsPerContact`: two targets → `Active` with backend PENDING, both receipts QUEUED; effects = `WriteBackend` + `SendSms`×2, in target order.
  - `raiseWithNoContacts_stillWritesBackend`
  - `raiseWhileActive_isNoOp`: same state, empty effects. *(Review Focus 5.)*
  - `backendSaved_setsSavedAndServerAt`; `backendFailed_setsFailed`
  - `smsSentThenDelivered_endsDelivered`
  - `smsDeliveredBeforeSent_staysDelivered`: DELIVERED then SENT → DELIVERED. *(Review Focus 3.)*
  - `smsFailed_thenRetry_requeuesOnlyFailed`: effects contain `SendSms` only for failed contacts.
  - `resultForOtherSosId_isIgnored` *(Review Focus 3)*
  - `partnerDeliveredAndAcked_recorded`; `ackDoesNotImplyNothingElse` (ack doesn't change sms or backend)
  - `cancelActive_movesToCancelledWithWriteCancel`
  - `cancelFromIdle_isNoOp`; `cancelWrongId_isNoOp`
  - `raiseAfterCancelled_startsNewActive`
  - `smsResultAfterCancel_stillRecorded` (late callbacks update `Cancelled.sms`)
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement `reduce`.** Use a status rank (QUEUED 0 < SENT 1 < DELIVERED 2; FAILED applies only when the current status is QUEUED or SENT, never over DELIVERED). An incoming status replaces the current one only if it has a higher rank, or is FAILED under that rule.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat(domain): SOS state machine with honest receipts`.

### Task 1.4: Location policies (cadence, arrival, distance)

**Files:**
- Create: `domain/location/CadencePolicy.kt`, `domain/location/ArrivalDetector.kt`, `domain/location/Geo.kt`
- Test: `domain/location/CadencePolicyTest.kt`, `ArrivalDetectorTest.kt`, `GeoTest.kt`

**Interfaces:**
- Consumes: `SharingMode`.
- Produces:
  - `enum class LocationAccuracy { HIGH, BALANCED }`
  - `data class LocationCadence(val intervalMs: Long, val minDistanceM: Float, val accuracy: LocationAccuracy)`
  - `object CadencePolicy { fun cadenceFor(mode: SharingMode, onTrip: Boolean, batteryPct: Int?, charging: Boolean?): LocationCadence? }`
  - `fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double` (haversine, R = 6 371 008.8 m)
  - `class ArrivalDetector(private val floorM: Float = 100f, private val requiredFixes: Int = 2, private val maxUsableAccuracyM: Float = 200f) { fun onFix(distanceM: Double, accuracyM: Float): Boolean; fun reset() }`

- [ ] **Step 1: Write the failing tests.**
  - **Cadence:**
    - PAUSED → `null`; OFF → `null`
    - LIVE + trip → `(15_000, 10f, HIGH)`
    - LIVE, no trip → `(180_000, 0f, BALANCED)`
    - LIVE, battery 14, not charging (trip or not) → `(600_000, 0f, BALANCED)`
    - battery 14 **charging** → normal cadence
    - battery null → normal cadence
  - **Geo:**
    - identical points → 0
    - (18.5204, 73.8567)→(18.5314, 73.8446) = 1 767.4 m ± 1
    - (0, 0)→(0, 1) = 111 195.1 m ± 1
  - **Arrival:**
    - two consecutive fixes at 50 m, accuracy 20 → first `false`, second `true`
    - 50 m then 300 m then 50 m → `false, false, false` (streak reset)
    - accuracy 150, distance 140 → counts (radius = max(100, 150))
    - accuracy 2 000, distance 1 900, twice → `false, false` *(Review Focus 4)*, and the streak is unchanged by unusable fixes: 50/20 → unusable → 50/20 = `false`, `false`, `true`
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement** as specified. Fixes with `accuracyM > maxUsableAccuracyM` return the current arrived value without changing the streak.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Update the spec.** In `docs/v2/REDESIGN_SPEC.md` §9 Trip sheet, add the line: "Fixes with accuracy worse than 200 m are ignored for arrival." Also add the same sentence to ARCHITECTURE_PROPOSAL §8.
- [ ] **Step 6: Commit.** Message: `feat(domain): location cadence, distance and arrival detection`.

### Task 1.5: OverdueEvaluator and RetentionSelector

**Files:**
- Create: `domain/model/Trip.kt`, `domain/trips/OverdueEvaluator.kt`, `domain/retention/RetentionSelector.kt`
- Test: `domain/trips/OverdueEvaluatorTest.kt`, `domain/retention/RetentionSelectorTest.kt`

**Interfaces:**
- Produces:

  ```kotlin
  enum class TripState { ACTIVE, ARRIVED, ENDED, OVERDUE }
  data class TripSnapshot(val state: TripState, val expectBy: Long?, val extendedTo: Long?, val endedAt: Long?)
  enum class OverduePhase { NOT_DUE, ASK_TRAVELLER, ALERT_PARTNER }
  object OverdueEvaluator {
      const val GRACE_MS = 600_000L
      fun evaluate(trip: TripSnapshot?, now: Long): OverduePhase
  }
  enum class RetainedKind { CHAT, MEDIA, EVENT, TRIP, SOS }
  data class RetentionItem(val id: String, val kind: RetainedKind, val at: Long,
                           val endedAt: Long? = null, val sosActive: Boolean = false, val cancelledAt: Long? = null)
  object RetentionSelector { fun selectExpired(items: List<RetentionItem>, now: Long): List<String> }
  ```

- [ ] **Step 1: Write the failing tests.**
  - **Overdue:**
    - null → NOT_DUE
    - state ARRIVED → NOT_DUE
    - ACTIVE, no expectBy → NOT_DUE
    - now = expectBy − 1 → NOT_DUE
    - now = expectBy → ASK_TRAVELLER
    - now = expectBy + 599 999 → ASK_TRAVELLER
    - now = expectBy + 600 000 → ALERT_PARTNER
    - extendedTo overrides expectBy (extendedTo = expectBy + 15 min; now = expectBy + 10 min → NOT_DUE)
    - state OVERDUE, now past grace → ALERT_PARTNER
  - **Retention:**
    - CHAT at now − 7 d − 1 → expired; at now − 7 d + 1 → kept
    - MEDIA boundary at 3 d
    - EVENT boundary at 7 d
    - TRIP with `endedAt = null` (active), at now − 30 d → kept
    - TRIP endedAt now − 24 h − 1 → expired
    - SOS `sosActive = true` at now − 90 d → kept
    - SOS cancelledAt now − 30 d − 1 → expired
    - SOS cancelled 29 d ago → kept
    - result preserves input order
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** Retention constants as `const val` in `RetentionSelector`: `CHAT_MS = 7 * DAY`, `MEDIA_MS = 3 * DAY`, `EVENT_MS = 7 * DAY`, `TRIP_AFTER_END_MS = DAY`, `SOS_AFTER_CANCEL_MS = 30 * DAY`.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat(domain): overdue evaluation and retention selection`.

### Task 1.6: ImageBudget, CopyVariant and greeting slot

**Files:**
- Create: `domain/media/ImageBudget.kt`, `domain/copy/CopyVariant.kt`
- Test: `domain/media/ImageBudgetTest.kt`, `domain/copy/CopyVariantTest.kt`

**Interfaces:**
- Produces:

  ```kotlin
  object ImageBudget {
      const val MAX_EDGE_PX = 1024; const val TARGET_BYTES = 120_000; const val HARD_LIMIT_BYTES = 150_000
      const val AVATAR_EDGE_PX = 256; const val AVATAR_MAX_BYTES = 30_000
      val QUALITY_STEPS = listOf(85, 75, 65, 55, 45)
      fun scaledSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int>
      fun chooseQuality(encodedBytesAt: (Int) -> Int, target: Int, hardLimit: Int): Int?
  }
  enum class CopyVariant { WARM, NEUTRAL, CASUAL }
  enum class GreetingSlot { MORNING, AFTERNOON, EVENING, NIGHT }
  fun copyVariantFor(rel: Relationship, greetingEnabled: Boolean): CopyVariant
  fun greetingSlot(hourOfDay: Int): GreetingSlot
  ```

- [ ] **Step 1: Write the failing tests.**
  - **scaledSize:**
    - (4000, 3000, 1024) → (1024, 768)
    - (3000, 4000, 1024) → (768, 1024)
    - (800, 600, 1024) → (800, 600), never upscaled
    - (1, 5000, 1024) → (1, 1024), minimum 1 px
  - **chooseQuality:**
    - encoder 200 k@85, 130 k@75, 110 k@65 → 65
    - all > target but 140 k@45 → 45
    - all > hardLimit → null
    - first step under target → 85
  - **copyVariantFor:** PARTNER+true → WARM; PARTNER+false → NEUTRAL; FAMILY → NEUTRAL; FRIENDS → CASUAL; TRUSTED → CASUAL
  - **greetingSlot:** 5 → MORNING, 11 → MORNING, 12 → AFTERNOON, 16 → AFTERNOON, 17 → EVENING, 21 → EVENING, 22 → NIGHT, 4 → NIGHT
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** `chooseQuality` returns the first step with bytes ≤ target; otherwise the last step if ≤ hardLimit; otherwise null.
- [ ] **Step 4: Run.** Expected: PASS. Also run the full suite with `./gradlew testDebugUnitTest`; expected PASS, including `LayeringTest`.
- [ ] **Step 5: Commit.** Message: `feat(domain): image budget and relationship-aware copy variants`.

---

## Stage 2 — Design system (`ui/`)

Verification for this stage uses Compose UI tests on the `Pixel_8_Pro` AVD (`./gradlew connectedDebugAndroidTest`; boot the emulator headless with `emulator -avd Pixel_8_Pro -no-window -no-audio &` and `adb wait-for-device`), plus a debug-only catalog screen for screenshots.

### Task 2.1: Colour tokens and the theme

**Files:**
- Create: `ui/theme/OrbitColors.kt`, `ui/theme/OrbitTheme.kt`, `ui/theme/Spacing.kt`, `ui/theme/Shapes.kt`
- Test: `app/src/test/java/com/varad/unstoppableash/ui/theme/ContrastTest.kt` (pure JVM: luminance maths on the token hex values)

**Interfaces:**
- Produces:
  - `@Immutable data class OrbitColors(...)` with fields exactly as REDESIGN_SPEC §2:
    - `background`, `surface`, `surfaceWarm`, `primary`, `onPrimary`
    - `text`, `textSecondary`, `blush`, `lavender`
    - `goodBg`, `goodInk`, `warnBg`, `warnInk`
    - `sos`, `onSos`, `sosText`, `outline`
  - `val LightOrbitColors`, `val DarkOrbitColors`
  - `val LocalOrbitColors`
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`
  - `@Composable fun OrbitTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit)`, which also provides a Material3 `ColorScheme` mapped from the tokens
  - `object Spacing { xs=4.dp, sm=8.dp, md=12.dp, lg=16.dp, xl=24.dp, xxl=32.dp, xxxl=48.dp }`
  - `OrbitShapes` (small 12, medium 20, large 28)
  - Colour values are stored as `Long` ARGB constants in an `internal object OrbitPalette`, so the JVM contrast test can read them without Compose.

- [ ] **Step 1: Write the failing test.** `ContrastTest` asserts ≥ 4.5 for every text/background pair listed in REDESIGN_SPEC §2. The light-theme pairs include text/background, primary/background, onPrimary/primary, textSecondary/background, goodInk/goodBg, warnInk/warnBg and onSos/sos. The dark-theme pairs include text/background, primary/background, onPrimary/primary, sosText/background, and white-on-dark-sos ≥ 4.5.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests "*ContrastTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** the palette, tokens and theme.
- [ ] **Step 4: Run.** Expected: PASS. Then run `./gradlew assembleDebug`; expected success.
- [ ] **Step 5: Commit.** Message: `feat(ui): Little Orbit colour tokens and theme`.

### Task 2.2: Nunito typography

**Files:**
- Create: `app/src/main/res/font/nunito_regular.ttf`, `nunito_semibold.ttf`, `nunito_bold.ttf`, `nunito_extrabold.ttf`, `app/src/main/assets/licenses/OFL.txt`, `ui/theme/OrbitType.kt`
- Modify: `docs/v2/ZERO_COST_DEPENDENCIES.md`

**Interfaces:**
- Produces:
  - `val Nunito: FontFamily`
  - `val OrbitTypography: Typography` (M3 slot mapping per REDESIGN_SPEC §3)
  - `val SosTextStyle: TextStyle` (28/34 sp, weight 800)

- [ ] **Step 1: Ask the owner in chat** to approve downloading the static Nunito TTFs and `OFL.txt` from `https://github.com/googlefonts/nunito`. State the source, the four files, and the approximate size (about 130 KB each). **Do not download without a clear yes.**
- [ ] **Step 2: Place the files.** Verify the licence header in `OFL.txt` reads "SIL OPEN FONT LICENSE Version 1.1".
- [ ] **Step 3: Implement** `OrbitType.kt` with the exact sizes, line heights and weights from REDESIGN_SPEC §3, and wire `OrbitTypography` into `OrbitTheme`.
- [ ] **Step 4: Verify.** Run `./gradlew assembleDebug lintDebug`. Expected: success, 0 errors.
- [ ] **Step 5: Commit.** Message: `feat(ui): bundle Nunito (OFL) and type scale`.

### Task 2.3: Core components and the debug catalog

**Files:**
- Create in `ui/components/`: `OrbitButton.kt`, `StatusPill.kt`, `OrbitCard.kt`, `OrbitTextField.kt`, `OrbitDialog.kt`, `OrbitSheet.kt`, `EmptyState.kt`, `LoadingOrbit.kt`, `ErrorBanner.kt`, `OfflineBar.kt`, `AvatarImage.kt`, `PresenceRing.kt`, `ReceiptRow.kt`
- Create: `ui/components/PresenceUi.kt` (maps `PresenceState` → icon, colour pair and a sentence string resource)
- Create: `app/src/main/res/values/strings_v2.xml`, `app/src/main/res/drawable/ic_orbit_glyph.xml`, `ill_empty_chat.xml`
- Create (debug only): `app/src/debug/java/com/varad/unstoppableash/catalog/DesignCatalogActivity.kt`, `app/src/debug/AndroidManifest.xml` (exported=false activity)
- Test: `app/src/androidTest/java/com/varad/unstoppableash/ui/components/StatusPillTest.kt`, `OrbitButtonTest.kt`

**Interfaces:**
- Consumes: `PresenceState`, `formatAge`, the theme tokens.
- Produces:
  - `@Composable fun OrbitButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: OrbitButtonStyle = OrbitButtonStyle.Primary, enabled: Boolean = true, loading: Boolean = false)`
  - `@Composable fun StatusPill(name: String, state: PresenceState, modifier: Modifier = Modifier)`
  - `@Composable fun ReceiptRow(label: String, status: ReceiptStatus, time: String?, onRetry: (() -> Unit)?)` with `enum class ReceiptStatus { QUEUED, PENDING, DONE, FAILED, UNKNOWN }`
  - The other components follow the REDESIGN_SPEC §5 table.

- [ ] **Step 1: Write the failing UI tests.**
  - `statusPill_liveShowsSentence`: `StatusPill("Sam", Live(60_000))` has a node with contentDescription "Sam: live, 1 minute ago".
  - `statusPill_staleIsNotLive`: `Stale(40 min)` has no text containing "Live" and shows "Last update 40 min ago".
  - `statusPill_pausedShowsLastSeen`: shows "Paused".
  - `statusPill_unknownNeverLive`
  - `orbitButton_loadingIgnoresClicks`: click count stays 0 while `loading = true`.
  - `orbitButton_minTouchTarget`: height ≥ 48 dp.
- [ ] **Step 2: Run** `./gradlew connectedDebugAndroidTest` (emulator booted). Expected: FAIL.
- [ ] **Step 3: Implement** the components per REDESIGN_SPEC §5, with strings from `strings_v2.xml` (no literals in code). Then implement `DesignCatalogActivity`, which shows every component in both themes plus a font-scale toggle (1.0 / 1.3 / 2.0).
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Visual check.** Run:

  ```bash
  adb shell am start -n com.varad.unstoppableash/.catalog.DesignCatalogActivity
  ```

  Capture screenshots in light, dark, and at 2.0× scale with `adb exec-out screencap -p`, saved to the scratchpad. Attach them to the stage report; don't commit them.
- [ ] **Step 6: Commit.** Message: `feat(ui): Little Orbit component library and debug catalog`.

### Task 2.4: HoldToConfirmButton

**Files:**
- Create: `ui/components/HoldToConfirmButton.kt`
- Test: `app/src/androidTest/java/com/varad/unstoppableash/ui/components/HoldToConfirmButtonTest.kt`

**Interfaces:**
- Produces: `@Composable fun HoldToConfirmButton(label: String, holdMillis: Long, onConfirmed: () -> Unit, modifier: Modifier = Modifier, colors: HoldColors = HoldDefaults.sos(), size: Dp = 120.dp, enabled: Boolean = true)`
  - It exposes a semantics custom action labelled `label`, which calls `onConfirmed` (the TalkBack path).
  - It shows hint text `R.string.hold_released_early` after an early release.

- [ ] **Step 1: Write the failing tests.** Use `composeTestRule.mainClock.autoAdvance = false`.
  - `release_at_2999ms_doesNotConfirm` *(Review Focus 5)*: `performTouchInput { down(center) }`, advance 2 999 ms, `up()` → callback count 0, hint visible.
  - `hold_3000ms_confirmsOnce`: down, advance 3 000 ms → count 1; advancing another 2 000 ms keeps count 1.
  - `rapidRepress_restartsFromZero`: down 2 000 / up / down 2 000 → count 0.
  - `disabled_neverConfirms`
  - `accessibilityAction_confirms`: `performSemanticsAction(CustomActions)` → count 1.
- [ ] **Step 2: Run.** Expected: FAIL.
- [ ] **Step 3: Implement.** Use `pointerInput` with `awaitEachGesture`. The progress `Animatable` is driven by `withFrameMillis`, so it obeys the test clock. Haptics: `HapticFeedbackType.TextHandleMove` at each whole second, `LongPress` on confirm. When animations are disabled, the progress ring still renders (it is functional, not decorative).
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit.** Message: `feat(ui): hold-to-confirm button for SOS and cancel`.

**Stage 2 exit:**
- `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug connectedDebugAndroidTest` all pass.
- Catalog screenshots are reviewed with the owner before Stage 5 uses the components.

---

## Stages 3–11 — Roadmap

Each stage below gets a detailed task plan, appended here, before it starts. Every stage ends with:
- a full build, unit tests, lint and connected tests
- a diff review for secrets and personal data
- updates to the docs (FEATURE_MATRIX status, ZERO_COST_DEPENDENCIES)

| Stage | Deliverable | Acceptance (from FEATURE_OPPORTUNITY_MATRIX / spec) | Needs owner |
|---|---|---|---|
| **3. Rules + emulator harness** | `firebase/firebase.json`, `firebase/database.rules.json` (phase 1: new paths + a copy of the owner's current legacy rules), `firebase/test/rules.test.ts`, `package.json` | Every case in ARCHITECTURE §6 passes against the Database emulator (outsider, third joiner, expired invite, unverified email, forged `from`, client `at`, partner can't cancel SOS, partner can ack, oversized media, chat immutable) | OK to `npm install` firebase-tools + rules-unit-testing; paste current rules from the console |
| **4. Data layer** | `core/time/ServerClock`, `data/firebase/DbPaths` + mappers, `AuthRepository` (Credential Manager, anonymous→Google link), `PairRepository`, `PresenceRepository`, `LocationRepository`, `ChatRepository`, `EventRepository`, `SosRepository`, `TripRepository`, `SettingsStore`, `EmergencyContactsStore`, `ActiveSosStore`, `AppContainer` | Mapper unit tests (including legacy shapes); repository tests with fake sources; listeners removed on cancellation (a test asserts `removeEventListener` is called); `Outcome` failures typed | — |
| **5. Onboarding, nav shell, Home** | `AshGoNavHost`, onboarding screens, Home + ViewModel (PresenceRing, status line O-2, battery, my sharing state), Check-in sheet, profile + avatar (256 px / ≤ 30 KB), Settings shell. Reachable via a debug launcher, **not** yet the default. | Journeys J1–J3, J5 on the emulator with two test Google accounts (the owner's); Home shows every presence state from a fake repo; no "Live" without data | Sign in on the emulator with test accounts |
| **6. Chat** | Chat screen + ViewModel, idempotent send, nudges (O-3: typed, 1 per 30 s, event chips inline), media off the main thread (`ImageBudget`), unread via `lastReadAt` | Double-tap sends once; > 150 KB rejected; old messages page; IME keeps the list anchored | — |
| **7. Safety / SOS** | Safety screen, `SosHoldActivity`, `SosActiveActivity`, `PartnerSosActivity`, `SmsDispatcher` (fake in tests), `SmsResultReceiver`, SOS widget (same FQCN) → hold screen, `SosTileService`, emergency contacts (picker, no `READ_CONTACTS`), call shortcuts, false-alarm SMS confirm | Reducer-driven receipts on screen; a tap never sends; the fake dispatcher sees one send per contact; partner ack round-trip on two emulators; **device:** SMS to an owner-controlled number | Real SMS test on the owner's phone |
| **8. Map, trips, location** | `LocationShareService` (`location` FGS, `CadencePolicy`), pause / time-boxed sharing (`sharingUntil`, D-14 keeps the last point), Map screen (attribution, faded stale markers, no fake pin, recentre buttons), TripSheet, arrival → check-in, overdue alarm + partner-side evaluation | J6, J9 on the emulator with mock locations; osmdroid UA `AshGo/<version>`; **device:** a real walk/drive trip | Contact string for the OSM User-Agent |
| **9. Stay connected + notifications + reliability** | `Channels`, `Notifier`, `StayConnectedService` (`specialUse`), `BootReceiver`, `HeartbeatWorker` (15 min) + `onDisconnect` presence, battery writes on 5 % change, dedupe store, Reliability screen, test notification | No notification posted to an unknown channel (unit test over the channel registry); one notification per event; **device:** 24 h survival, reboot start, DND | Battery-optimisation exemption on both phones |
| **10. Supporting features** | Settings sub-screens, Leave orbit (rules-tested delete), Privacy & Data + `CleanupWorker`, `UpdateChecker` (public releases + SHA-256), FakeCall, Our page (local photos), Help, About/licences, `LegacyImporter` | The importer maps the legacy role correctly (unit); the updater rejects a hash mismatch; cleanup never deletes an active SOS | Make the releases repo public (D-10) |
| **11. Cutover + hardening** | `MainActivity` → v2; delete v1 screens and services (after a reference check); manifest cleanup (remove the accessibility service, `REQUEST_INSTALL_PACKAGES` kept only for the updater); R8 on with keep rules; `docs/v2/DEVICE_VERIFICATION.md`; release-readiness report; rules phase 2 file ready | Release APK installs over v1 (same signer) and keeps local contacts; lint 0 errors; all tests pass; grep shows no tokens or personal names; the device checklist is run with the owner | Backup DB + rules; deploy phase-1 rules before install; phase-2 after both migrate |


---

## Stage 3 — Rules + emulator harness (detailed, written 2026-10-14)

**Inputs:**
- The owner's deployed rules: root `auth != null`, plus `notifications` world-open (SECURITY S1 confirmed, S10).
- ARCHITECTURE §5–§6, as amended: `userPairs/{uid}` replaces `users/{uid}`.

**Tooling:** owner-approved 2026-10-14. Packages:
- `firebase-tools@15.33.0`
- `@firebase/rules-unit-testing@6.0.0`
- `firebase@13.0.0`

Install them with `npm install --save-dev` into `firebase/`, and git-ignore `firebase/node_modules`. Tests use Node's built-in `node:test` (no TypeScript toolchain).

**Test command** (from `firebase/`, with the Android Studio JBR as `JAVA_HOME` for the emulator):

```bash
npx firebase emulators:exec --only database --project demo-ashgo "node --test --test-concurrency=1 test/"
```

The `demo-` project ID guarantees nothing ever talks to the real project.

**Phase-1 rule principles:**
- No root-level grant.
- Legacy v1 paths (`chat`, `tracking`, `alerts`, `buzz`, `users`) are enumerated with `auth != null`, so v1 keeps working.
- `app_config` and `notifications`: read `auth != null`, write `false`. v1 never writes them; the console bypasses rules.
- Every v2 path is membership-checked, `from`/`at` are validated against `auth.uid` and `now`, and `$other` children are rejected.

### Task 3.1: Harness + legacy phase-1 rules
**Files:**
- Create: `firebase/package.json`, `firebase/firebase.json`, `firebase/database.rules.json`, `firebase/test/helpers.mjs`, `firebase/test/legacy.test.mjs`
- Modify: `.gitignore`

**Tests:**
- `legacy chat readable/writable by signed-in v1 client`
- `unauthenticated client cannot read or write legacy paths`
- `nobody can write notifications` (signed-in and anonymous)
- `clients cannot write app_config but can read it`
- `root read is denied`, so pairs never leak through a cascade

**RED:** start with `{"rules": {}}` (deny all). The legacy-readable test must fail.

### Task 3.2: Pairing rules (`userPairs`, `invites`, `pairs/*/meta`, leave)
**Tests:**
- create pair as memberA
- cannot create a pair naming someone else as memberA
- inviter writes an invite (expires ≤ 24 h)
- invite with expiry > 24 h rejected
- invitee with the matching verified email can read the invite
- a different email cannot read it
- unverified email cannot join
- expired invite cannot join
- matching invite joins as memberB
- third account cannot join a full pair
- memberA cannot join their own pair as memberB
- `userPairs` can only point to a pair you belong to
- outsider cannot read the pair
- memberA confirms (`confirmedAt == now`), memberB cannot
- either member can delete the whole pair (Leave)
- outsider cannot delete it

### Task 3.3: Pair data rules (profiles, presence, location, trips, chat, media, events, sos)
**Tests:**
- member writes own profile, presence and location; cannot write the partner's
- location out of range rejected
- client-chosen `at` rejected
- chat with forged `from` rejected
- chat update rejected (immutable)
- sender may delete own chat; partner may delete chat only after 7 days
- media over 205 000 chars rejected
- event: partner may set `seenBy/<self>` and `deliveredTo/<self>`; sender may not set its own receipts
- SOS:
  - member raises SOS (state active)
  - partner cannot cancel
  - sender cancels with `cancelledAt == now`
  - partner acks (`ackBy/<self> == now`)
  - sender cannot ack own SOS
  - partner cannot edit `smsSent`
  - active SOS cannot be deleted
- unknown child field rejected

**Exit:**
- All rules tests pass in the emulator.
- ARCHITECTURE §6 is updated if any rule deviates.
- SECURITY_PRIVACY_AUDIT §7 lists the deploy steps for the owner.
- A fresh reviewer checks the rules, which are security-critical.

---

## Stage 4 — Data layer (detailed, written 2026-10-14)

**Goal:** repositories that talk to the Stage 3 rules, fully JVM-testable, with an end-to-end check against the local emulators.

**Design:**
- **`RtdbClient` is the only Firebase-database seam:**
  - `observe(path): Flow<Any?>`
  - `observeLast(path, limit, orderByChild)`
  - `set`, `update`, `remove`, each returning `Outcome<Unit>`
  - `newKey(path)`, `connected(): Flow<Boolean>`, `onDisconnectSet(path, value)`, `serverTimestamp` sentinel, `serverTimeOffset(): Flow<Long>`
- **Implementations:**
  - `FirebaseRtdbClient` (real; `callbackFlow` + `awaitClose { removeEventListener }`)
  - `FakeRtdb` (test fixture: in-memory tree, server-time substitution, live-observer count, per-path failure injection)
- **Mappers** turn the Firebase value (`Map<String, Any?>`, `Long`, `Double`, `String`, `Boolean`) into domain models, so no `DataSnapshot` appears in tests.
- **Firebase Auth** sits behind `AuthGateway` (`currentUser: Flow<AuthUser?>`, `signInWithGoogleIdToken(idToken)`, which links an anonymous v1 user when present, `signOut()`).
- **Credential Manager** UI glue is in Stage 5.

**New dependencies** (free, Apache-2.0):
- `kotlinx-serialization-json:1.11.0` + the `org.jetbrains.kotlin.plugin.serialization:2.4.21` plugin, for local stores and the v1 contacts migration
- `app.cash.turbine:turbine:1.2.1` (test only)

**Test command:** `./gradlew testDebugUnitTest --tests "com.varad.unstoppableash.data.*" --tests "com.varad.unstoppableash.core.*"`

### Task 4.1 — Foundations
**Files:** `core/result/Outcome.kt`, `core/time/ServerClock.kt`, `data/firebase/RtdbClient.kt`, `data/firebase/FirebaseRtdbClient.kt`, `data/firebase/DbPaths.kt`; test fixture `src/test/.../data/firebase/FakeRtdb.kt`.

**Tests:**
- `ServerClock` adds the latest offset and starts at offset 0
- `DbPaths` builds every ARCHITECTURE §5 path
- `emailKey` lowercases and replaces every `.` with `,` and trims whitespace
- `FakeRtdb` contract tests:
  - set / observe / update / remove
  - `serverTimestamp` becomes clock time
  - observer count returns to 0 after collection is cancelled
  - injected failure becomes `Outcome.Failure(PermissionDenied)`

### Task 4.2 — Auth + pairing
**Files:** `data/auth/AuthGateway.kt` (+ `FirebaseAuthGateway`), `data/pair/PairRepository.kt`, `data/firebase/Mappers.kt` (pair part).

**Tests (with FakeRtdb and a fake AuthGateway):**
- `createPair` writes meta, own profile and `userPairs` in rule-compatible order
- `invite` writes `invites/<key>/<pairId>` with `fromEmail` and expiry ≤ 24 h
- `myInvites` lists invites for the signed-in email
- `join` writes memberB, then `userPairs`, then deletes the invite
- `confirm` sets `confirmedAt`
- `leave` removes the pair and clears own `userPairs`
- `observePairState`:
  - Unpaired / Waiting / NeedsConfirmation / Paired
  - a stale `userPairs` pointing at a deleted pair reads as Unpaired, never a crash (review #9)
- `signIn` failure surfaces typed `Outcome`

### Task 4.3 — Presence + location
**Files:** `data/presence/PresenceRepository.kt`, `data/location/LocationRepository.kt`, mappers.

**Tests:**
- `partnerPresence` combines `presence/<partner>` + `location/<partner>` into a `PresenceSnapshot`
- legacy/partial values map to null fields
- `locationAt` ≤ 0 is treated as absent (review M10)
- `setSharing` writes mode + `sharingUntil`
- `goOnline` registers `onDisconnect` (`online=false`, `lastSeen=server`)
- `writeLocation` sends `at=serverTimestamp` and drops accuracy 0 as unknown (review M10)
- battery is written only on a ≥ 5 % change

### Task 4.4 — Chat + events
**Files:** `data/chat/ChatRepository.kt`, `data/events/EventRepository.kt`, `domain/model/Chat.kt`, `domain/model/OrbitEvent.kt`.

**Tests:**
- `messages` maps and orders the last 100
- `send` with the same client id twice writes once and reports success
- a photo over `ImageBudget.HARD_LIMIT_BYTES` is rejected before any write
- `markRead` writes `lastReadAt=server`
- `unreadCount` counts partner messages after `lastReadAt`
- `checkIn` / `nudge` write typed events
- `markSeen` writes only for partner events
- nudge is rate-limited to 1 per 30 s (returns `Failure(RateLimited)`)

### Task 4.5 — SOS + trips
**Files:** `data/sos/SosRepository.kt`, `data/trips/TripRepository.kt`, mappers.

**Tests:**
- `raise` writes `{from, state:active, at:server, lat/lng/locAt when known}`
- `cancel` updates state + `cancelledAt` together
- `ack` / `markDelivered` only for partner SOS
- `partnerActiveSos` emits the latest active one
- SMS counts are written by the sender
- `startTrip` / `extend` / `end` / `arrive` write rule-valid shapes
- `partnerTrip` maps to `TripSnapshot`

### Task 4.6 — Local stores, v1 local migration, AppContainer
**Files:** `data/local/SettingsStore.kt`, `EmergencyContactsStore.kt`, `ActiveSosStore.kt`, `data/legacy/LegacyLocalMigration.kt`, `AppContainer.kt`, `AshGoApplication.kt` (creates the container only; v1 behaviour unchanged).

**Tests:**
- contacts round-trip
- parse the v1 `emergency_contacts_v2` JSON (and the plain `emergency_contacts` CSV fallback), de-duplicating numbers
- fake-call voices parse
- `ActiveSosStore` round-trips an `Active` SOS with receipts

### Task 4.7 — End-to-end against the emulators (instrumented)
**Files:** `src/androidTest/.../data/FirebaseEndToEndTest.kt`, `firebase/firebase.json` (+ auth emulator on 9099).

**Steps:**
1. Run `firebase emulators:start --only auth,database --project demo-ashgo` on the host.
2. The Android emulator reaches it at `10.0.2.2`.
3. Two Google test users sign in with emulator fake ID tokens.
4. Through the real `FirebaseRtdbClient` + repositories: create → invite → join → confirm, then chat round-trip, SOS raise/ack/cancel, and an outsider denied.

**Exit:**
- all unit, rules and connected tests pass
- lint has no new issues
- a fresh review of the stage


---

## Stage 5 — Onboarding, navigation shell, Home (detailed, written 2026-10-14)

**Production state:** phase-1 rules deployed by the owner. Probe: unauthenticated read and write of `notifications` → 401.

**Approach:**
- Screens are **stateless**, taking a `UiState` plus callbacks, and are tested with Compose UI tests using hand-built states.
- ViewModels are tested on the JVM with `FakeRtdb` / `FakeAuthGateway` and a test main dispatcher.
- v2 is reachable only through a **debug-only launcher activity** (`V2Activity`, registered in `src/debug/AndroidManifest.xml`). The v1 `MainActivity` stays the release launcher until Stage 11.

**Dev loop on the emulator:**
- In debug builds, `V2Activity` accepts `--ez useEmulators true`. It then points Firebase at `10.0.2.2` and offers a "Dev sign-in" (an Auth-emulator fake Google token), so the full flow can be exercised and screenshotted without real accounts.
- Real Credential Manager sign-in is the production path; the owner checks it once on a device.

### Task 5.1 — Presence ticker + Home state
**Files:** `feature/home/HomeUiState.kt`, `feature/home/HomeViewModel.kt`, `feature/home/PresenceTicker.kt`.

**Tests (JVM):**
- Presence is re-evaluated every 30 s without a new snapshot (Live → Recent after 2 min)
- No partner location → never Live
- Home combines:
  - the partner's name and avatar
  - the partner's presence and battery
  - my sharing mode
  - unread count
  - the partner's latest check-in
  - the partner's active SOS
  - the partner's trip
- Check-in and nudge results surface as `Sending → Saved | Failed(reason)`
- `RATE_LIMITED` surfaces as its own message
- `readFailures` raises `offline/error` banners

### Task 5.2 — Onboarding state machine
**Files:** `feature/onboarding/OnboardingViewModel.kt`, `OnboardingUiState.kt`.

**Steps:** `SignIn → Choose → CreateProfile → InvitePartner → Waiting → ConfirmPartner (memberA) / WaitingForConfirmation (memberB) → Done`, and `Choose → Invitations → Join`.

**Tests (JVM):**
- Step derivation from `PairState` + invites
- Name and email validation messages
- "That's not them" leaves
- Failures keep the user on the same step with the reason

### Task 5.3 — Onboarding screens
**Files:** `feature/onboarding/*Screen.kt`, `strings_v2.xml`.

**UI tests:**
- Continue-with-Google button present
- Create disabled until a name is entered
- Invalid email shows an error
- Invitation shows the inviter's **email** (D-16)
- Confirm screen shows the partner's avatar and email

### Task 5.4 — Home screen + check-in sheet
**Files:** `feature/home/HomeScreen.kt`, `CheckInSheet.kt`.

**UI tests:**
- Every presence state renders, and "Live" appears only for Live
- No location → no map preview
- The SOS row exists, a tap doesn't send, and it's separated from the actions
- The partner-SOS banner appears when an SOS is active
- The unread badge shows
- `OfflineBar` shows on failures
- Layout works at 2.0× font

### Task 5.5 — Navigation shell, V2Activity, sign-in glue, settings shell
**Files:**
- `ui/nav/AshGoNavHost.kt`, `ui/nav/Destinations.kt`
- `feature/settings/SettingsScreen.kt` (profile summary, theme, Leave orbit, Sign out)
- `feature/onboarding/GoogleSignIn.kt` (Credential Manager)
- `V2Activity.kt` (main source, edge-to-edge, status-bar icons follow the theme)
- `src/debug/AndroidManifest.xml` launcher entry
- `data/dev/DevFirebase.kt` (debug-only emulator wiring)

**Tests:**
- Nav: signed-out → onboarding
- Paired → Home with the 4 tabs
- Not-yet-built tabs show a clear "Coming in a later stage" state (debug build only)

### Task 5.6 — Manual verification on the emulator (recorded)
Run the full flow against the emulators:
1. Dev sign-in as A
2. Create the orbit, invite B
3. Seed B joining via an admin script
4. Confirm, then view Home with seeded presence

Screenshots go in the stage report. A fresh review of the stage follows.

---

## Stage 6 — Chat (detailed, written 2026-10-14)

**Spec:** REDESIGN_SPEC §Chat, O-3 nudges, D-5 media budget. **Owner input:** none; the owner asked (2026-10-14) for Stages 6–11 to run without per-stage approval.

**Approach:** same pattern as Stage 5. Pure timeline logic in `domain/chat`. A `PhotoEncoder` seam: the Android implementation runs on `Dispatchers.Default`, and JVM tests use a fake. `ChatViewModel` is tested with `FakeRtdb`, and the stateless `ChatScreen` is tested with built states.

### Task 6.1 — Timeline (pure)
**Files:** `domain/chat/Timeline.kt`.

`buildTimeline(messages, events, outbox, myUid, zone): List<TimelineItem>` merges messages, check-in/nudge chips and my not-yet-confirmed sends. Items are oldest → newest:
- `DaySeparator(date)` when the local date changes;
- `Bubble(id, mine, text?, mediaId?, at, status, showTime)`, where `showTime` is true on the last bubble of a group (same sender, within 5 min);
- `EventChip(event)`.

An outbox entry whose id is already on the server is dropped, so a send is never shown twice. Outbox statuses are SENDING and FAILED. Server messages are SENT, or SEEN once the partner's `lastReadAt` ≥ `at`.

**Tests:** separators across midnight in the given zone; grouping at 5 min; outbox deduped by id; a FAILED entry stays put; SEEN only from the partner's lastReadAt.

### Task 6.2 — Repository additions
`ChatRepository.lastReadAt(pairId, uid): Flow<Long?>`, plus paging: `messages(pairId, limit)` exists, and the VM grows `limit` in steps of 50. `hasMore = list.size >= limit`.

**Tests:** reading the partner's lastReadAt; a bigger limit returns older messages.

### Task 6.3 — PhotoEncoder
- `interface PhotoEncoder { suspend fun encode(uri: String): EncodedPhoto? }`, with `EncodedPhoto(jpeg, w, h)`.
- `AndroidPhotoEncoder(contentResolver)`: `ImageDecoder` to a target size from `ImageBudget.scaledSize`, then JPEG over `ImageBudget.QUALITY_STEPS` via `chooseQuality`, on `Dispatchers.Default`. Returns null when even the lowest step exceeds `HARD_LIMIT_BYTES`.
- **Instrumented tests:**
  - 4000×3000 gradient → ≤ 1024 px edge and ≤ 150 KB;
  - random-noise 1024² at the lowest quality, too big → null;
  - the call doesn't run on the main thread.

### Task 6.4 — ChatViewModel
**State:** partner name/avatar/presence, timeline, composer text, `sending`, `hasMore`, `loadingOlder`, `error`, `photoError`, `nudgeStatus`.

**Behaviour:**
- `send()`: the client id is generated once per composed message. Re-entry while in flight is ignored (a double tap sends once). On failure the entry stays FAILED with Retry, which reuses the same id.
- `sendPhoto(uri)`: encode, then refuse when null ("Photo too large even after compression"), then send with a fresh id.
- `retry(id)`.
- `nudge(kind)`: the shared EventRepository, limited to one per 30 s.
- `markRead()` whenever the visible timeline gains a partner message, and `markSeen` on partner events.
- `loadOlder()`.

**Tests:**
- a double send writes one message;
- failure → FAILED → retry with the same id → one message;
- an over-budget photo → `photoError`, nothing written;
- markRead advances lastReadAt and the unread count goes to 0;
- loadOlder raises the limit;
- a nudge inside 30 s → RATE_LIMITED.

### Task 6.5 — ChatScreen
- Reverse `LazyColumn` anchored at the newest message, with `imePadding`.
- Day separators and bubbles: mine blush/right, partner lavender/left, 20 dp radius with a 6 dp tail. Status 🕓/✓/Seen/⚠ Retry.
- Centred event chips.
- Photo bubble: base64 decoded off the main thread (`produceState` + `Dispatchers.Default`), max 240 dp, tap → fullscreen dialog.
- Composer: 📷 (`PickVisualMedia`, no permission), ⚡ (NudgeSheet), multiline field up to 5 lines, Send disabled when blank or in flight.
- Empty state "Say hi" per CopyVariant. Reaching the top triggers loadOlder.
- **UI tests:**
  - the send button disables while sending;
  - a failed bubble shows Retry and calls back with its id;
  - an event chip renders the kind label;
  - the empty state;
  - the photo-too-large message;
  - TalkBack: each bubble reads "{name}: {text}, {time}, {status}".

### Task 6.6 — Wire-up + manual verification
- The Chat tab in MainShell, and the unread badge from Home's state.
- Run on the emulator against the emulators with Bob (REST, as in 5.6):
  - a message both ways;
  - a photo;
  - a nudge chip;
  - unread clears when the tab opens.
- Then the fresh review.

---

## Stage 7 — Safety / SOS (detailed, written 2026-10-14)

**Spec:** REDESIGN_SPEC §Safety, §SosActive, §PartnerSos, §EmergencyContacts; D-6, D-8; USER_JOURNEYS J10.

**Rulings carried into this stage:**
- **Profiles have no phone field.** Adding one would need a production rules redeploy, and would put a phone number on the server. "Call {partner}" therefore uses a partner number stored only on this phone, chosen with the system contact picker (no `READ_CONTACTS`), in `SettingsStore`.
- **SOS entry points stay debug-only until Stage 11:** `SosHoldActivity`, `SosTileService`, and the in-app Safety tab. The release build still runs v1. The v1 widget (`SosWidgetProvider`, same FQCN) is repointed to the hold screen at cutover, never before, so the live v1 app keeps its current behaviour.
- **Automated tests never send SMS.** `SmsDispatcher` is an interface. JVM tests use a fake. `AndroidSmsDispatcher` is exercised only by hand on the emulator, whose modem never reaches a carrier.

### Task 7.1 — SMS text and part aggregation (pure)
**Files:** `domain/sos/SmsComposer.kt`, `domain/sos/SmsAggregator.kt`.

`SmsComposer.sos(name, fix?, battery?, nowMs)` builds GSM-7-safe text:
- `AshGo SOS: {name} needs help. Location: https://www.openstreetmap.org/?mlat={lat}&mlon={lng}#map=17/{lat}/{lng} (±{acc} m, {age} ago). Battery {b}%. Sent automatically by AshGo.`
- Coordinates have 5 decimals and are never 0,0. With no fix: "Location unavailable."

`SmsComposer.falseAlarm(name)`.

`SmsAggregator`: per-part sent/delivered results → one worst-of-parts `SmsStatus` per attempt.

**Tests:**
- coordinates are formatted, and there is no "0,0";
- missing location → "Location unavailable";
- the text is GSM-7 only, apart from the name;
- the aggregator: any FAILED → FAILED; all SENT → SENT; DELIVERED only when every part is delivered.

### Task 7.2 — Ports: SMS dispatcher, device location, contact numbers
**Files:** `service/sos/SmsDispatcher.kt` (interface + `AndroidSmsDispatcher` + `SmsResultReceiver`) and `service/location/LocationSource.kt` (interface + `FusedLocationSource`).

- `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` with a 10 s timeout, falling back to `lastLocation`. Returns null without permission.
- Results arrive as `SmsResult(sosId, contactId, status)` through a `SharedFlow`.

### Task 7.3 — SosController (orchestrator, app-scoped)
**Files:** `service/sos/SosController.kt`.

It owns `StateFlow<SosState>`, persisted through `ActiveSosStore` and restored on start, and runs the reducer's effects:
- WriteBackend → `SosRepository.raise` with a location (or none), then `BackendSaved`/`BackendFailed`. Retried with backoff while active.
- SendSms → number from `EmergencyContactsStore` → dispatcher.
- WriteCancel → `SosRepository.cancel`.

It also:
- mirrors the record's `deliveredTo`/`ackBy` into `PartnerDelivered`/`PartnerAcked`;
- pushes SMS counts to the record;
- provides `raise()`, `cancel()`, `retryFailedSms()`, `falseAlarmRecipients()` and `sendFalseAlarm()`.

**Restore after process death:**
- a PENDING backend is re-raised (idempotent);
- a QUEUED SMS becomes FAILED("not confirmed: app restarted"), and is never auto-resent.

**Tests** (FakeRtdb, fake dispatcher, fake location, temp DataStore):
- one record and one SMS per contact;
- raising again while active creates nothing;
- no contacts → no SMS;
- SMS not permitted → no sends, `smsOff`;
- location arrives after the core write;
- cancel writes once, and the partner can't cancel (rules, already tested);
- false alarm only to SENT/DELIVERED;
- partner ack → state;
- restore semantics.

### Task 7.4 — Safety screen + SosActive panel
**Files:** `feature/safety/SafetyViewModel.kt`, `SafetyScreen.kt`, `SosActivePanel.kt`.

- The Safety tab shows the hold (3 s) and "What happens", built from real state: paired, contacts count + SMS permission, location permission.
- Call 112 and Call {partner} open the dialer, with no `CALL_PHONE`.
- Rows: emergency contacts, partner number, fake call (Stage 10).
- While an SOS is active, the tab shows the SosActive panel:
  - hold 1.5 s "I'm safe: cancel SOS";
  - ReceiptRows: saved, delivered to partner, seen, one per SMS contact with Retry, and location;
  - after cancelling, a false-alarm confirmation that lists recipients and mentions charges.
- Home's SOS row reads "SOS active · open" while active.
- **UI tests:** a tap never raises; receipts render each fact; the false-alarm dialog lists names; cancel needs a hold.

### Task 7.5 — PartnerSos
**Files:** `feature/safety/PartnerSosScreen.kt`, plus view-model wiring.

- Full red. Shows the time raised, the last location + age, and battery.
- Acknowledge, Call {partner}, Call 112.
- Opening it writes `deliveredTo` (the phone has shown it); Acknowledge writes `ackBy`.
- Reached from Home's banner now; Stage 9 adds the full-screen notification.
- **Tests:** VM: display → delivered once; ack once; own SOS never shown.

### Task 7.6 — Emergency contacts + partner number
**Files:** `feature/safety/ContactsScreen.kt`.

- The picker is `ACTION_PICK` on `Phone.CONTENT_TYPE`, which grants that one row, so no `READ_CONTACTS`.
- Manual add, remove, and an SMS cost note. At most 5 contacts.

### Task 7.7 — SosHoldActivity + Quick Settings tile (debug manifest)
- The hold screen works over the lock screen (`showWhenLocked`, `turnScreenOn`) and holds 3 s; a tap never sends.
- The tile opens it.
- **Test:** the activity raises only after the hold.

### Task 7.8 — Manual verification + review
On the emulator against the emulators:
- raise;
- Bob (REST) delivers and acks → rows update;
- an SMS to the emulator's own number (5554), which never leaves the machine;
- cancel → false alarm;
- PartnerSos as Alice when Bob raises.

Then the fresh review.

---

## Stage 8 — Map, trips, location sharing (detailed, written 2026-10-14)

**Spec:** REDESIGN_SPEC §Map/§Trip sheet; D-7 (osmdroid, attribution, UA, no prefetch); D-14 (pause keeps the last point, shown as paused); USER_JOURNEYS J6 and J9; CadencePolicy, ArrivalDetector and OverdueEvaluator (Stage 1).

**Rulings carried in:**
- **OSM User-Agent** is `AshGo/<versionName> (+https://github.com/varad-oss/AshGo)`. D-7 allowed a GitHub URL when no other contact is given, and the owner can swap it.
- **Notifications** (overdue prompt, arrival notice, the ongoing sharing notification's actions beyond Pause) come in Stage 9. Stage 8 shows overdue in the app.
- **The location service** is started only from the foreground, when sharing is LIVE. Restart after reboot is Stage 9.

### Task 8.1 — Sharing plan (pure)
`domain/location/SharingPlan.kt`:
- `effective(mode, until, now): SharingMode` — a PAUSED mode with `until` ≤ now becomes LIVE again.
- `pauseUntil(choice, now)` with choices: 1 h, until tomorrow 07:00 local, and indefinitely.

**Tests:** expiry; indefinite pause; OFF stays OFF.

### Task 8.2 — LocationShareEngine (testable core of the service)
`service/location/LocationShareEngine.kt` takes:
- my presence (sharing mode and until)
- my trip
- battery
- a `LocationUpdates` port: `fun updates(cadence): Flow<LocationFix>`

It writes `LocationRepository.write` for each fix while effectively LIVE, and stops writing while PAUSED or OFF (D-14 keeps the last point). Cadence changes when a trip starts or ends or the battery drops.

For an active trip with a destination:
- feeds `ArrivalDetector`;
- on arrival → `TripRepository.arrive`, plus a check-in event with kind `arrived` if `notifyPartner` is set — once only.

At the moment a pause expires it writes `sharing=live`.

**Tests** (FakeRtdb plus a fake updates flow):
- writes while LIVE;
- nothing while PAUSED, and the last point is kept;
- pause expiry resumes;
- trip cadence is used;
- arrival fires once and ignores inaccurate fixes;
- low battery switches cadence.

### Task 8.3 — LocationShareService (FGS `location`) + FusedLocationUpdates
A thin wrapper around the engine:
- an ongoing notification "Sharing your location with {partner}" with a Pause 1 h action, on the `service` channel (low);
- started from the Map/Home sharing controls only when permission is granted;
- stops itself when sharing is not LIVE.

**Test:** a manual emulator walk with `adb emu geo fix`.

### Task 8.4 — Map screen + view-model
- osmdroid `MapView` in `AndroidView`, with the tile cache in the app cache dir and no prefetch.
- Attribution "© OpenStreetMap contributors" is always visible.
- Markers:
  - my marker, only when I have a location;
  - the partner's marker only when a location exists (no fake pin), faded when Stale/Offline/Paused, with an age label.
- Recentre buttons: Me and {partner}.
- Header: StatusPill and age.
- Sharing controls: Share live / Pause 1 h / Pause until morning / Pause until I resume / Stop.
- A trip card opens the TripSheet.

**VM tests:** marker visibility per presence state; the age; the sharing actions write presence.

**UI tests** on the overlay (not the tiles): attribution present; no partner marker label without a location.

### Task 8.5 — Trips: sheet + view-model + overdue in the app
- TripSheet: destination (none / long-press on the map / my current spot), an expect-by time, a note (≤ 80 chars), "Tell {partner} I've started" (on by default).
- While active: I'm here / End trip / +15 min.

Overdue in the app:
- **traveller**, ASK_TRAVELLER: a banner "Are you okay?" with I'm safe (arrive), +15 min, and Get help (opens Safety);
- **partner**, ALERT_PARTNER for the partner's trip: an Overdue card on Home in warn colours with last location + age, battery, Call, and "Raise SOS for them" (opens Safety).

**Tests:**
- VM: start / extend / arrive / end write the right fields;
- the overdue phases drive the banners;
- "Raise SOS for them" never sends without a hold.

### Task 8.6 — Wire-up + manual verification
- The Map tab replaces ComingLater. Home's Trip and Manage buttons go to Map. The Home location card opens Map.
- On the emulator against the emulators, using `adb emu geo fix` to walk:
  - share LIVE → the record updates;
  - pause → it stops, and the partner sees Paused;
  - a trip to a destination → arrival → a check-in chip in Chat;
  - Bob's overdue trip → the Home card.
- Then the fresh review.
