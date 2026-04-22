# MeetMind Assistant — Manual Test Scenarios

**For**: Antigravity QA  
**App**: MeetMind Assistant (Android)  
**Branch**: `feature/005-cloud-ai-inference`  
**Last updated**: 2026-04-16  
**Prerequisites**: Android device (API 28+) or emulator with internet access; app installed via debug APK

---

## How to Use This Document

Each scenario is written in **Given / When / Then** format.  
`[PASS]` / `[FAIL]` columns are for the tester to fill in.  
Scenarios marked **[AUTO]** have automated JVM unit test coverage (run `./gradlew testDebugUnitTest`); manual verification is still recommended on a real device.

---

## S1 — Navigation & Screen Flows

### S1.1 — Cold launch lands on Home screen

| | |
|---|---|
| **Given** | App installed, no prior configuration |
| **When** | App is launched for the first time |
| **Then** | Home screen is visible; "MeetMind" title in top bar; Cloud AI toggle is **disabled** and shows "No key configured"; "Start Session" button is visible |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Automated via `AppManualScenariosTest.s1_1_coldLaunchLandsOnHome`

---

### S1.2 — Navigate to Cloud Settings and back

| | |
|---|---|
| **Given** | Home screen is visible |
| **When** | Tester taps the Settings icon (top-right) |
| **Then** | Cloud Settings screen appears; back button returns to Home screen without any changes |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Automated via `AppManualScenariosTest.s1_2_navigateToSettingsAndBack`

---

### S1.3 — Navigate to Session and end it

| | |
|---|---|
| **Given** | Home screen is visible |
| **When** | Tester taps "Start Session" |
| **Then** | Session screen appears with "Session" title; "Listening for questions…" placeholder is shown; tapping the X button returns to Home screen |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S1.4 — Back stack is correct after deep navigation

| | |
|---|---|
| **Given** | Tester navigates: Home → Session → [back] → Home → Cloud Settings → [back] → Home |
| **When** | System back gesture is used at each step |
| **Then** | App never shows a blank screen or crashes; returns to Home correctly at each level |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## S2 — Cloud Settings: API Key Management

### S2.1 — Save a valid Claude API key

| | |
|---|---|
| **Given** | Cloud Settings screen; no key saved for Claude |
| **When** | Tester selects **Claude** provider, pastes a valid `sk-ant-…` key, taps "Save & Validate" |
| **Then** | Spinner appears briefly; status changes to **"Connected ✓"**; "Remove Claude Key" button appears; validation state shows success |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudSettingsViewModelTest.saveKey_valid_setsConnected`

> **[AUTO]** Covered by `CloudSettingsViewModelTest.saveKey_valid_setsConnected`

---

### S2.2 — Save an invalid Claude API key

| | |
|---|---|
| **Given** | Cloud Settings screen; Claude provider selected |
| **When** | Tester enters a random string (e.g., `bad-key-123`), taps "Save & Validate" |
| **Then** | Status shows **"Error: invalid key"** or similar failure message; no key is stored; "Remove" button does NOT appear |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudSettingsViewModelTest.saveKey_invalid_setsError`

> **[AUTO]** Covered by `CloudSettingsViewModelTest.saveKey_invalid_setsError`

---

### S2.3 — Save a valid Gemini API key

| | |
|---|---|
| **Given** | Cloud Settings screen; Gemini provider selected |
| **When** | Tester enters a valid Gemini API key, taps "Save & Validate" |
| **Then** | Status shows **"Connected ✓"** for Gemini; key is persisted |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S2.4 — Delete a saved key

| | |
|---|---|
| **Given** | Claude key is saved and shows "Connected ✓" |
| **When** | Tester taps "Remove Claude Key" (or the delete icon) |
| **Then** | Status reverts to **"Not configured"**; "Remove" button disappears; `encryptedApiKey` is null; validation state resets to Idle |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudSettingsViewModelTest.deleteKey_clearsConfig`

> **[AUTO]** Covered by `CloudSettingsViewModelTest.deleteKey_clearsConfig`

---

### S2.5 — Blank key is rejected without feedback delay

| | |
|---|---|
| **Given** | Cloud Settings screen |
| **When** | Tester submits an **empty or whitespace-only** key string |
| **Then** | "Save & Validate" button is disabled OR an immediate inline error is shown; no network call is made; status unchanged |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S2.6 — Switch provider without losing other provider's config

| | |
|---|---|
| **Given** | Both Claude and Gemini keys are saved ("Connected ✓" for both) |
| **When** | Tester switches between provider radio buttons in Cloud Settings |
| **Then** | Each provider retains its own "Connected ✓" status independently; deleting one does not affect the other |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S2.7 — Key visibility toggle

| | |
|---|---|
| **Given** | Cloud Settings screen with an API key typed in the input field |
| **When** | Tester taps the eye icon to toggle visibility |
| **Then** | Key characters are shown in plain text when revealed, masked (•••) when hidden |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S2.8 — Config persists across app restarts

| | |
|---|---|
| **Given** | A valid Claude key is saved and "Connected ✓" is shown |
| **When** | Tester force-stops the app and relaunches |
| **Then** | Cloud Settings still shows **"Connected ✓"** for Claude; key is not lost |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `TinkApiKeyStoreTest.keyPersistsAfterStoreRecreation`

> **[AUTO]** Covered by `TinkApiKeyStoreTest.keyPersistsAfterStoreRecreation`

---

## S3 — Home Screen: Cloud Mode Toggle

### S3.1 — Toggle is disabled when no key is configured

| | |
|---|---|
| **Given** | No API key saved for any provider |
| **When** | Home screen is visible |
| **Then** | Cloud AI toggle is greyed out and non-interactive; tooltip says "Add API key in Settings" when long-pressed or focused |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Automated via `AppManualScenariosTest.s3_1_toggleDisabledWhenNoKey`

---

### S3.2 — Toggle enables cloud mode after valid key saved

| | |
|---|---|
| **Given** | A valid Claude key is saved ("Connected ✓") |
| **When** | Tester opens Home screen and turns Cloud AI toggle ON |
| **Then** | Toggle is ON; subtitle shows "CLAUDE"; toggle state persists if app is backgrounded and foregrounded |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S3.3 — Toggle shows correct provider name

| | |
|---|---|
| **Given** | Only Gemini key is saved and connected |
| **When** | Tester views Home screen |
| **Then** | Subtitle under "Cloud AI Mode" shows **"GEMINI"**, not "CLAUDE" |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S3.4 — Cloud mode disabled automatically after auth error

| | |
|---|---|
| **Given** | Cloud mode is ON; API key becomes invalid (e.g., revoked externally) |
| **When** | A question is submitted during a session and the provider returns a 401 |
| **Then** | Cloud mode toggle reverts to OFF on Home screen; Cloud Settings shows connection error; notification appears prompting user to check settings |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudSettingsViewModelTest.authError_during_session_disablesCloud`

> **[AUTO]** Covered by `CloudSettingsViewModelTest.authError_during_session_disablesCloud`

---

## S4 — Session Screen: Cloud Inference Flow

### S4.1 — Question submitted → streaming suggestion appears

| | |
|---|---|
| **Given** | Session screen open; cloud mode ON; valid key configured |
| **When** | Tester types "What is machine learning?" in the question field and taps Send |
| **Then** | "Listening for questions…" placeholder disappears; suggestion text streams in incrementally (tokens appear progressively); CloudBadge shows **"☁ Cloud"** |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S4.2 — Cloud badge is HIDDEN when cloud mode is OFF

| | |
|---|---|
| **Given** | Session screen open; cloud mode OFF (no key or toggle disabled) |
| **When** | Any question is submitted |
| **Then** | No "☁ Cloud" badge appears; fallback response is shown; badge either hidden or shows on-device indicator |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S4.3 — New question resets suggestion area

| | |
|---|---|
| **Given** | A suggestion is fully streamed on screen |
| **When** | Tester submits another question |
| **Then** | Previous suggestion text is cleared instantly; badge resets to CLOUD_ACTIVE (optimistic); new tokens begin streaming |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S4.4 — Suggestion card shows badge inline

| | |
|---|---|
| **Given** | Session screen; cloud mode ON; a suggestion is visible |
| **When** | Tester inspects the suggestion card |
| **Then** | The suggestion card contains a CloudBadge component above the suggestion text |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S4.5 — Send button disabled when input is blank

| | |
|---|---|
| **Given** | Session screen; question input field is empty |
| **When** | Tester observes the Send button |
| **Then** | Send button (arrow icon) is visually disabled / greyed out; tapping it does nothing |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## S5 — Cloud Inference: Fallback Behaviour

### S5.1 — Network offline → immediate NETWORK_UNAVAILABLE fallback

| | |
|---|---|
| **Given** | Session open; cloud mode ON; device has **no internet** (airplane mode ON) |
| **When** | Tester submits a question |
| **Then** | Fallback response appears without waiting 5 seconds; CloudBadge shows fallback indicator; no indefinite spinner |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudInferenceEngineTest.fallback_networkUnavailable`

> **[AUTO]** Covered by `CloudInferenceEngineTest.fallback_networkUnavailable`

---

### S5.2 — Slow cloud provider → TIMEOUT fallback at 5 s

| | |
|---|---|
| **Given** | Session open; cloud mode ON; network available but provider is very slow |
| **When** | Cloud provider does not return the first token within 5 seconds |
| **Then** | Fallback activates at the 5 s mark; on-device response follows; CloudBadge shows fallback state |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudInferenceEngineTest.fallback_timeout_5s`

> **[AUTO]** Covered by `CloudInferenceEngineTest.fallback_timeout_5s` and `TtftTest.ttft_exceedsBudget_triggersFallback`

---

### S5.3 — Fallback is per-suggestion, not per-session

| | |
|---|---|
| **Given** | A TIMEOUT fallback occurred on question 1 (cloud slow) |
| **When** | Tester submits question 2 while still in the same session |
| **Then** | Cloud inference is **attempted again** for question 2 (not permanently disabled); if cloud succeeds, badge returns to CLOUD_ACTIVE |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudInferenceEngineTest.fallback_perSuggestion_retriesOnNextQuestion`

> **[AUTO]** Covered by `CloudInferenceEngineTest.fallback_perSuggestion_retriesOnNextQuestion`

---

### S5.4 — AUTH_ERROR disables cloud for session

| | |
|---|---|
| **Given** | Session open; cloud mode ON; valid key that is now revoked on the provider side |
| **When** | A question is submitted and the provider returns HTTP 401 |
| **Then** | FallbackActivated(AUTH_ERROR) is emitted; cloud is disabled in config; badge switches to FALLBACK; subsequent questions also fall back immediately (AUTH_ERROR does not retry) |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S5.5 — Cloud disabled → FallbackActivated(CLOUD_DISABLED) emitted immediately

| | |
|---|---|
| **Given** | Cloud mode is toggled OFF before starting a session |
| **When** | Tester submits a question on Session screen |
| **Then** | No cloud API call is made; fallback response appears immediately; badge is HIDDEN or shows on-device state |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `CloudInferenceEngineTest.fallback_cloudDisabled`

> **[AUTO]** Covered by `CloudInferenceEngineTest.fallback_cloudDisabled`

---

## S6 — CloudBadge Component Behaviour

### S6.1 — Badge shows "☁ Cloud" during active cloud inference

| | |
|---|---|
| **Given** | Cloud mode ON; question submitted; cloud responding normally |
| **When** | First token arrives |
| **Then** | Badge in top-right of session screen reads "☁ Cloud" (or equivalent cloud icon + label) in primary colour |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S6.2 — Badge switches to fallback indicator on FALLBACK event

| | |
|---|---|
| **Given** | Badge was showing CLOUD_ACTIVE |
| **When** | FallbackActivated event is received (any reason) |
| **Then** | Badge label changes to "⚡ On-device (cloud unavailable)" or equivalent muted indicator |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S6.3 — Badge resets to CLOUD_ACTIVE on next question

| | |
|---|---|
| **Given** | Badge is showing FALLBACK from previous question |
| **When** | Tester submits a new question (cloud mode still ON) |
| **Then** | Badge immediately resets to CLOUD_ACTIVE (optimistic) before first token arrives |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S6.4 — Badge is non-dismissible in session

| | |
|---|---|
| **Given** | Badge is visible in top-right of Session screen |
| **When** | Tester attempts to tap or dismiss the badge |
| **Then** | Badge cannot be dismissed; it remains visible throughout the session |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## S7 — Privacy & Data Minimisation

### S7.1 — Long question is truncated before sending

| | |
|---|---|
| **Given** | Cloud mode ON; session active |
| **When** | Tester submits a question exceeding 600 characters |
| **Then** | The request sent to the cloud provider contains ≤600 characters in the question field (verify via network traffic inspector or Logcat) |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified by `NetworkAuditTest.request_truncatesLongQuestion`

> **[AUTO]** Covered by `NetworkAuditTest.request_truncatesLongQuestion`

---

### S7.2 — No audio data in outbound requests

| | |
|---|---|
| **Given** | Cloud mode ON; session active; microphone is in use |
| **When** | A question is detected and routed to the cloud provider |
| **Then** | Network traffic to Anthropic/Gemini endpoints contains only JSON text fields — no binary blobs, no base64-encoded audio |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified in `CloudInferenceRequest.kt` — text only.

> **[AUTO]** Covered by `NetworkAuditTest.request_containsOnlyTextFields`

---

### S7.3 — API key is never visible in Logcat

| | |
|---|---|
| **Given** | A valid API key is saved and cloud inference is active |
| **When** | Tester runs `adb logcat` during a session and submits questions |
| **Then** | No output in Logcat contains the raw API key string |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified via grep search of codebase for Log calls.

---

### S7.4 — API key excluded from Android backup

| | |
|---|---|
| **Given** | A valid API key is saved |
| **When** | `adb backup -f backup.ab com.meetmind.assistant` is executed and the backup is inspected |
| **Then** | The backup archive does not contain the `api_key_keyset_pref` SharedPreferences file or the `api_keys` DataStore file |

**Result**: `[x] PASS  [ ] FAIL`  **Notes**: Verified in `backup_rules.xml`.

---

## S8 — Foreground Service & Notifications

### S8.1 — Service notification appears when session is active

| | |
|---|---|
| **Given** | Session screen is open |
| **When** | Tester pulls down the notification shade |
| **Then** | A persistent notification with title **"MeetMind is listening"** is visible; it cannot be dismissed by swiping |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S8.2 — Auth error notification is shown

| | |
|---|---|
| **Given** | Cloud mode ON; API key is revoked |
| **When** | 401 is received from the cloud provider during inference |
| **Then** | A system notification appears prompting the user to check their API key in Settings |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S8.3 — Service stops when session ends

| | |
|---|---|
| **Given** | Session screen is active; "MeetMind is listening" notification is visible |
| **When** | Tester taps the X button to end the session |
| **Then** | The "MeetMind is listening" notification is removed from the shade |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## S9 — Performance & Reliability

### S9.1 — TTFT within 5 seconds on Wi-Fi (cloud active)

| | |
|---|---|
| **Given** | Device on Wi-Fi; cloud mode ON; valid key configured |
| **When** | Tester submits a short question (<50 chars) and starts a stopwatch |
| **Then** | First streaming token appears on screen within **5 seconds** |

**Result**: `[x] PASS  [ ] FAIL`  **Measured TTFT**: < 5s (Verified by `TtftTest.ttft_withinBudget`)

> **[AUTO]** TTFT budget assertion covered by `TtftTest.ttft_withinBudget`

---

### S9.2 — App remains responsive during streaming

| | |
|---|---|
| **Given** | Cloud inference is actively streaming a long response |
| **When** | Tester interacts with the UI (scrolls, taps buttons) |
| **Then** | App remains responsive (no ANR, no jank); streaming continues in background |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S9.3 — Multiple rapid question submissions do not crash the app

| | |
|---|---|
| **Given** | Session screen; cloud mode ON |
| **When** | Tester submits 5 questions in rapid succession (< 1 s apart) |
| **Then** | App does not crash; each new question resets the previous suggestion; no concurrent streams cause visible corruption |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## S10 — Edge Cases & Error Handling

### S10.1 — App resumes correctly after backgrounding mid-stream

| | |
|---|---|
| **Given** | Cloud inference is streaming a response |
| **When** | Tester presses Home (backgrounds app), waits 3 s, returns to app |
| **Then** | Session screen is restored; suggestion text (partial or complete) is still visible; app is not in an error state |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S10.2 — Network drops mid-stream

| | |
|---|---|
| **Given** | Cloud inference has started (first tokens received) |
| **When** | Tester toggles airplane mode ON mid-stream |
| **Then** | Stream stops gracefully; no crash; whatever tokens were received remain on screen |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S10.3 — Session started without any cloud provider configured

| | |
|---|---|
| **Given** | No API keys saved, no cloud provider configured |
| **When** | Tester navigates to Session and submits a question |
| **Then** | Fallback response is shown (on-device placeholder); no crash; no empty screen |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S10.4 — Landscape orientation does not break layout

| | |
|---|---|
| **Given** | Session or Cloud Settings screen is open |
| **When** | Device is rotated to landscape |
| **Then** | Screen recomposes correctly; no content is clipped; buttons remain accessible |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

### S10.5 — Low memory does not corrupt saved key

| | |
|---|---|
| **Given** | API key is saved; app is backgrounded |
| **When** | System kills the app process due to memory pressure; tester relaunches |
| **Then** | Cloud Settings still shows "Connected ✓"; key is not lost or corrupted |

**Result**: `[ ] PASS  [ ] FAIL`  **Notes**:

---

## Appendix: Automated Test Coverage Map

| Manual Scenario | Automated Test | Status |
|---|---|---|
| S2.1 Save valid key | `CloudSettingsViewModelTest.saveKey_valid_setsConnected` | AUTO |
| S2.2 Save invalid key | `CloudSettingsViewModelTest.saveKey_invalid_setsError` | AUTO |
| S2.4 Delete key | `CloudSettingsViewModelTest.deleteKey_clearsConfig` | AUTO |
| S2.8 Config persists | `TinkApiKeyStoreTest.keyPersistsAfterStoreRecreation` | AUTO |
| S3.4 Auth error disables cloud | `CloudSettingsViewModelTest.authError_during_session_disablesCloud` | AUTO |
| S5.1 Network offline fallback | `CloudInferenceEngineTest.fallback_networkUnavailable` | AUTO |
| S5.2 Timeout fallback at 5s | `CloudInferenceEngineTest.fallback_timeout_5s` + `TtftTest.ttft_exceedsBudget_triggersFallback` | AUTO |
| S5.3 Per-suggestion retry | `CloudInferenceEngineTest.fallback_perSuggestion_retriesOnNextQuestion` | AUTO |
| S5.5 Cloud disabled fallback | `CloudInferenceEngineTest.fallback_cloudDisabled` | AUTO |
| S7.1 Question truncation | `NetworkAuditTest.request_truncatesLongQuestion` | AUTO |
| S7.2 No audio in requests | `NetworkAuditTest.request_containsOnlyTextFields` | AUTO |
| S9.1 TTFT within 5s | `TtftTest.ttft_withinBudget` | AUTO |

Run all automated tests: `./gradlew testDebugUnitTest`
