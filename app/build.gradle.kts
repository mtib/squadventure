plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.mtib.squadventure"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mtib.squadventure"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only arm64 (real phones + the Apple-Silicon emulator); MapLibre's native lib is
        // ~12MB per ABI, so this keeps the APK from carrying three unused copies.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Deterministic signing so releases install as in-place updates. Locally this falls back to
        // the standard ~/.android/debug.keystore; on CI, DEBUG_KEYSTORE_PATH points at the keystore
        // restored from the DEBUG_KEYSTORE_BASE64 secret (same key as local), so every build matches.
        getByName("debug") {
            System.getenv("DEBUG_KEYSTORE_PATH")?.let { path ->
                storeFile = file(path)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug keystore so personal sideload builds install without extra setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // PMTiles is read with random-access byte-range seeks; if AAPT compresses it in the APK the
        // offsets no longer line up and MapLibre's reader fails with "incorrect header check".
        noCompress += "pmtiles"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Offline vector map. Renders a bundled PMTiles basemap with zero network (INTERNET permission
    // is stripped in the manifest); MapLibre Native has no telemetry.
    implementation("org.maplibre.gl:android-sdk:13.3.1")

    // Pure-Kotlin domain (tile math, metrics, GPX) is JVM-unit-testable — no device needed.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
