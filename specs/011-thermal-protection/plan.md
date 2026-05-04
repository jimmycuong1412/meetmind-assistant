# Implementation Plan: Thermal Protection — End-of-Session Insight Fallback

**Feature Branch**: `feature/011-thermal-protection`  
**Spec**: `specs/011-thermal-protection/spec.md`  
**Created**: 2026-05-04

---

## Technical Context

- **Platform**: Android (API min 26, target 34)
- **Language**: Kotlin 1.9, JVM target 17
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture (domain / data / presentation / app modules)
- **Persistence**: Room 2.6 — NO schema change needed (title column already exists)
- **Coroutines**: Kotlin Coroutines + Flow
- **Thermal API**: `android.os.PowerManager` — `getCurrentThermalStatus()` + `addThermalStatusListener()` (API 29+)
- **Key existing files**:
  - `domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/SyncSttLlmUseCase.kt` — inference scheduling
  - `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainViewModel.kt` — lifecycle owner
  - `presentation/src/main/java/com/meetmind/assistant/presentation/main/MainUiState.kt` — UI state
  - `app/src/main/java/com/meetmind/assistant/ui/screens/InsightsSection.kt` — badge rendering

---

## Architecture Decisions

### Decision 1: Where does the thermal gate live?

**Chosen**: `SyncSttLlmUseCase` holds the gate state (`isHot: AtomicBoolean`) and the thermal buffer (`thermalBuffer: MutableList<TranscriptionSegmentWithSession>`). The gate is evaluated at every inference trigger point (reactive + interval). `MainViewModel` registers/unregisters the `OnThermalStatusChangedListener` and calls a `setThermalStatus(status: Int)` method on the use case via a `StateFlow` or direct call.

**Rationale**: Inference logic is already in `SyncSttLlmUseCase`; co-locating the gate avoids cross-layer coupling. The ViewModel owns the Android-specific `PowerManager` reference (it has Context access via Application).

**Alternative rejected**: Putting the gate in `MainViewModel` and blocking the use case call-site — requires passing thermal state as a parameter on every inference call; more fragile.

### Decision 2: Hysteresis implementation

Two consecutive `THERMAL_STATUS_LIGHT` or `THERMAL_STATUS_NONE` readings drop the gate. Readings arrive via the `OnThermalStatusChangedListener` callback (event-driven, not polling). A counter `coolReadings: Int` in `SyncSttLlmUseCase` is incremented on each cool reading and reset on any warm reading.

### Decision 3: End-of-session flush prompt

Re-use `InterviewPromptBuilder` (no new prompt). The flush concatenates all buffered segment texts with newline separators, passes to `InterviewPromptBuilder.build(role, fullText)`, and calls the existing LLM inference path. `toLlmInsight()` title is overridden to `"Session Summary"` in the flush call.

### Decision 4: API level guard

`PowerManager.getCurrentThermalStatus()` is wrapped in an `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)` block. Below API 29, `setThermalStatus()` is a no-op and `isHot` stays `false`.

---

## File Structure

### New files

```
domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/
    ThermalGateState.kt                    ← data class { isActive, coolReadings }
```

### Modified files

```
domain/src/main/java/com/meetmind/assistant/domain/usecase/sync/
    SyncSttLlmUseCase.kt                   ← thermal gate + buffer + flush

presentation/src/main/java/com/meetmind/assistant/presentation/main/
    MainUiState.kt                         ← thermalMode: Boolean field
    MainViewModel.kt                       ← PowerManager listener + setThermalStatus()

app/src/main/java/com/meetmind/assistant/ui/screens/
    InsightsSection.kt                     ← thermal badge composable

app/src/main/res/values/
    strings.xml                            ← "Thermal mode — summary at session end"
```

### No new Room migration needed

`title = "Session Summary"` is stored in the existing `title TEXT` column. No schema change.

---

## Research Notes

### `PowerManager` thermal API (API 29+)

```kotlin
// Registration (in ViewModel, needs Application context)
val pm = context.getSystemService(PowerManager::class.java)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    pm.addThermalStatusListener(thermalListener)
    // Initial status read
    syncSttLlmUseCase.setThermalStatus(pm.currentThermalStatus)
}

// Listener
val thermalListener = Consumer<Int> { status ->
    syncSttLlmUseCase.setThermalStatus(status)
    _uiState.update { it.copy(thermalMode = syncSttLlmUseCase.isHot.get()) }
}

// Cleanup
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    pm.removeThermalStatusListener(thermalListener)
}
```

### Threshold constants (`PowerManager`)

| Constant | Value | Meaning |
|----------|-------|---------|
| `THERMAL_STATUS_NONE` | 0 | No throttling |
| `THERMAL_STATUS_LIGHT` | 1 | Light throttling |
| `THERMAL_STATUS_MODERATE` | 2 | **Gate activates here** |
| `THERMAL_STATUS_SEVERE` | 3 | Severe throttling |
| `THERMAL_STATUS_CRITICAL` | 4 | Critical |
| `THERMAL_STATUS_EMERGENCY` | 5 | Emergency |

Gate activates at `status >= THERMAL_STATUS_MODERATE` (≥ 2).

---

## Implementation Notes

- `thermalBuffer` is cleared on `startStreaming()` alongside `newSegmentsSinceLastLlm`
- The flush in `stopStreaming()` runs only if `thermalBuffer.isNotEmpty()`; it runs in `viewModelScope` (or inside the use case coroutine scope) after the ticker is cancelled
- `isHot` is an `AtomicBoolean` — safe for concurrent read from the collector coroutine and write from the listener callback (different threads)
- Thermal badge: `AnimatedVisibility(visible = thermalMode && isRecording)` — same pattern as filler badge
- Unit tests: inject a `fakeThermalStatus: () -> Int` lambda into `SyncSttLlmUseCase` constructor so tests don't need a real `PowerManager`
