##############################################
# MeetMind Assistant ProGuard / R8 rules
##############################################

# ---- Crash reporting: preserve stack traces in release ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Kotlin core ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontnote kotlin.**

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-dontwarn kotlinx.coroutines.**

# ---- Hilt / Dagger ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}
-keepclassmembers @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ---- Room ----
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }
-keepclassmembers class * implements androidx.datastore.core.Serializer { *; }

# ---- JNI: lib-sherpa-onnx — external fun bindings must not be renamed ----
-keep class com.k2fsa.sherpa.onnx.** { *; }

# ---- JNI: lib-llama-android — external fun bindings must not be renamed ----
-keep class com.arm.aichat.** { *; }

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Navigation Compose ----
-dontwarn androidx.navigation.**

# ---- Material Kolor (dynamic palette) ----
-dontwarn com.materialkolor.**

# ---- Compose RichText (alpha) ----
-keep class com.halilibo.** { *; }
-dontwarn com.halilibo.**

# ---- Compose Shimmer ----
-keep class com.valentinilk.shimmer.** { *; }
-dontwarn com.valentinilk.shimmer.**

# ---- Foreground Services: keep action constants used in explicit intents ----
# ModelDownloadService and LlmProcessingService use companion object ACTION_* constants
# that are referenced via string comparison in onStartCommand(). R8 must not remove them.
-keepclassmembers class com.meetmind.assistant.service.ModelDownloadService {
    public static final java.lang.String ACTION_START;
    public static final java.lang.String ACTION_STOP;
}
-keepclassmembers class com.meetmind.assistant.service.LlmProcessingService {
    public static final java.lang.String ACTION_START;
    public static final java.lang.String ACTION_STOP;
}

# ---- Domain models: sealed classes / data classes used across module boundaries ----
# DownloadState subclasses are matched by type in service observers; keep class names
# so the is-checks and smart-casts survive minification across module boundaries.
-keep class com.meetmind.assistant.domain.model.DownloadState { *; }
-keep class com.meetmind.assistant.domain.model.DownloadState$* { *; }
-keep class com.meetmind.assistant.domain.model.LlmModelVariant { *; }
-keep class com.meetmind.assistant.domain.model.ThermalThrottle { *; }
-keep class com.meetmind.assistant.domain.model.LlmSamplerConfig { *; }
-keep class com.meetmind.assistant.domain.model.BatchInsightProgress { *; }
-keep class com.meetmind.assistant.domain.model.BatchInsightProgress$* { *; }
-keep class com.meetmind.assistant.domain.model.InsightStrategy { *; }

# ---- Notification managers (Hilt-injected into services) ----
-keep class com.meetmind.assistant.service.ModelDownloadNotificationManager { *; }
-keep class com.meetmind.assistant.service.LlmProcessingNotificationManager { *; }

# ---- Service controller ----
-keep class com.meetmind.assistant.domain.service.LlmProcessingServiceController { *; }
-keep class com.meetmind.assistant.service.LlmProcessingServiceControllerImpl { *; }

# ---- Device tier detector ----
-keep class com.meetmind.assistant.data.device.DeviceTierDetector { *; }

# ---- Model config ----
-keep class com.meetmind.assistant.data.config.ModelConfig { *; }
-keep class com.meetmind.assistant.data.config.DefaultModelConfig { *; }
-keep class com.meetmind.assistant.data.config.LowEndModelConfig { *; }

# ---- Thermal monitor ----
-keep class com.meetmind.assistant.domain.service.ThermalMonitor { *; }
-keepclassmembers class * implements com.meetmind.assistant.domain.service.ThermalMonitor { *; }

# ---- Strip debug and verbose log calls from release builds ----
# This removes Log.v() and Log.d() at compile time — errors and warnings are kept
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
