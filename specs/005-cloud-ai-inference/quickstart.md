# Quickstart: Cloud AI Inference Mode

**Feature**: 005-cloud-ai-inference
**Date**: 2026-04-14

---

## Prerequisites

- Spec 004 (question detection) fully implemented and smoke-tested
- Android Studio Hedgehog (2023.1.1) or newer
- Physical device (Snapdragon 8 Gen 3 recommended; any API 23+ device works for
  key storage; API 21+ for inference)
- A valid API key from at least one provider:
  - **Gemini**: Google AI Studio → https://aistudio.google.com/apikey (free tier
    available; Gemini Advanced subscription for higher quota)
  - **Claude**: Anthropic Console → https://console.anthropic.com/settings/keys
    (pay-as-you-go; Claude.ai Pro does not directly grant API access — use
    Anthropic API billing)

---

## Project Setup

### 1. Firebase project setup (Gemini path)

1. Create a Firebase project at https://console.firebase.google.com
2. Register your Android app (use your app's package name)
3. Download `google-services.json` → place at `app/google-services.json`
4. In Firebase console: AI Logic → configure Gemini Developer API backend →
   paste your AI Studio API key
5. **gitignore `google-services.json`** — it contains the Firebase App ID and
   API key config

### 2. Gradle dependencies

```kotlin
// app/build.gradle.kts

plugins {
    id("com.google.gms.google-services")  // ADD for Firebase
}

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true  // ADD for Jackson on API < 26
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Gemini (Firebase AI Logic)
    implementation("com.google.firebase:firebase-ai:17.0.0")

    // Claude
    implementation("com.anthropic:anthropic-java:2.20.0")

    // OkHttp (pinned)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Secure key storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.crypto.tink:tink-android:1.15.0")
}
```

```kotlin
// root build.gradle.kts
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

### 3. ProGuard rules (`app/proguard-rules.pro`)

```
# Anthropic SDK
-dontwarn com.anthropic.**
-keep class com.anthropic.** { *; }

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# Tink
-keep class com.google.crypto.tink.** { *; }
```

### 4. Manifest additions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Update existing service declaration from spec 004: -->
<service
    android:name=".service.AudioProcessingForegroundService"
    android:foregroundServiceType="microphone|dataSync"
    android:exported="false" />

<!-- Cloud-related data backup exclusion -->
<application
    android:dataExtractionRules="@xml/backup_rules"
    ...>
```

```xml
<!-- res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="api_key_keyset_pref" />
        <exclude domain="file" path="datastore/api_keys.preferences_pb" />
    </cloud-backup>
</data-extraction-rules>
```

---

## Manual Smoke Tests

### Smoke Test 1: API key configuration and persistence → ✅ Automated (spec 006)

1. Open the app → Settings → Cloud AI.
2. Select **Gemini**, enter a valid AI Studio API key, tap Save.
3. **Expected**: "Connected ✓" status shown. No error.
4. Force-stop and reopen the app.
5. **Expected**: Provider still shows "Connected ✓" — key survived restart.
6. Repeat with an invalid key.
7. **Expected**: Error message shown, key NOT saved.

### Smoke Test 2: Cloud badge visibility

1. Settings → Cloud AI → toggle Cloud mode ON.
2. Navigate to session home → select a mode → tap Start Listening.
3. **Expected**: "☁ Cloud" badge is visible on the session screen at all times.
4. Speak a question.
5. **Expected**: Suggestion appears with "☁ Cloud" badge.

### Smoke Test 3: Cloud suggestion quality comparison

1. Start a session in **Interview Mode** with Cloud mode ON (Gemini or Claude).
2. Ask: _"Tell me about a challenging project you led and what you learned from it."_
3. Note the suggestion.
4. Stop session. Toggle Cloud mode OFF.
5. Start a new session in Interview Mode.
6. Ask the same question.
7. **Expected**: The cloud suggestion is observably more detailed, specific, and
   coherent than the on-device suggestion.

### Smoke Test 4: Fallback on network unavailable → ✅ Automated (spec 006)

1. Enable Cloud mode ON.
2. Disable Wi-Fi AND mobile data on the device.
3. Start a session and speak a question.
4. **Expected**: A suggestion still appears (from on-device fallback) within 5
   seconds. A "⚡ On-device (cloud unavailable)" indicator is visible instead of
   "☁ Cloud".

### Smoke Test 5: Fallback on timeout (simulated) → ✅ Automated (spec 006)

1. Enable Cloud mode ON.
2. Use Android Studio's Network Inspector or `adb shell` to throttle connectivity to
   near-zero bandwidth (or use airplane mode with a short delay after session start).
3. Detect a question before the network drops.
4. **Expected**: After ~5 seconds without a first cloud token, a fallback indicator
   appears and an on-device suggestion is generated.

### Smoke Test 6: 401 auto-disable → ✅ Automated (spec 006)

1. Configure a key. Manually corrupt it in settings (edit one character).
2. Enable Cloud mode ON.
3. Start a session, speak a question.
4. **Expected**: The app detects the 401, automatically disables Cloud mode, shows
   a notification: "Cloud AI disabled — check your API key in Settings." No crash.

### Smoke Test 7: No audio transmitted (network audit) → ✅ Automated (spec 006)

1. Enable Cloud mode ON.
2. Use Android Studio's Network Inspector (or `mitmproxy` via system proxy) to
   capture all outbound traffic during a session.
3. Speak several questions and observe the generated suggestions.
4. **Expected**: All network requests go only to `generativelanguage.googleapis.com`
   (Gemini) or `api.anthropic.com` (Claude). Request bodies contain only JSON with
   text fields — no binary/audio data in any request.

---

## Streaming Display Implementation Note

Render tokens as they arrive using `collectAsStateWithLifecycle()` on the
`StateFlow<String>` from the inference engine. Do not wait for `Complete` before
updating the UI:

```kotlin
// SessionScreen.kt
val suggestion by viewModel.currentSuggestionTokens.collectAsStateWithLifecycle()

SuggestionCard(
    text = suggestion,  // updates live as tokens stream in
    inferenceMode = viewModel.lastInferenceMode,
    badgeState = viewModel.cloudBadgeState
)
```

---

## Known Limitations (v1)

- Only one provider can be active at a time; switching requires re-entering a key.
- The user must manage their subscription/billing directly with the provider.
- Model version is fixed in code (`gemini-2.5-flash`, `claude-haiku-4-5-20251001`);
  no in-app model selection.
- No retry logic beyond the 5-second timeout — a single failure triggers immediate
  fallback to on-device.
- On-device inference remains required even when Cloud mode is enabled (it is the
  fallback); the Phi-3-mini model must still be downloaded on first launch.
- `google-services.json` must be kept out of source control — document in project
  README.
