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
