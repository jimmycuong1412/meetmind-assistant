// T001: app/build.gradle.kts — Cloud AI inference dependencies
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    id("com.google.gms.google-services")  // T001: Firebase
}

android {
    namespace = "com.meetmind.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meetmind.assistant"
        minSdk = 23  // T013: Tink hardware-backed Keystore requires API 23+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true  // T001: required for Anthropic SDK Jackson on API < 26
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    // Desugaring — T001
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose — T001
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room (ContextProfile persistence)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore — T001: cloud config persistence
    implementation(libs.androidx.datastore.preferences)

    // Tink — T001: API key encryption
    implementation(libs.tink.android)

    // Firebase AI Logic (Gemini) — T001
    implementation(libs.firebase.ai)

    // Anthropic (Claude) — T001
    implementation(libs.anthropic.java)

    // OkHttp pinned — T001: avoid version conflicts with Anthropic SDK
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Material Design (for app theme)
    implementation(libs.material)
}
