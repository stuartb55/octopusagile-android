plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.stuartb55.octopusagile"
    compileSdk = 35 // Consider using a stable SDK like 34 unless you need 35 features

    defaultConfig {
        applicationId = "com.stuartb55.octopusagile"
        minSdk =
            26 // Consider a lower minSdk for wider compatibility, e.g., 24 or 26, unless 33 is required
        targetSdk = 35 // Match compileSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Consider enabling for release builds
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

kotlin {
    jvmToolchain(21) // Or your desired common JVM version (e.g., 11, 8)
}

dependencies {
    // === Use ONLY ONE Compose BOM ===
    // Choose a stable, recent BOM version. Example:
    implementation(platform("androidx.compose:compose-bom:2025.05.01")) // Example: REPLACE with your chosen STABLE BOM

    // Core Android KTX (ensure aliases point to correct versions)
    implementation(libs.androidx.core.ktx) // This should be the main one
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (versions managed by the BOM)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.fragment.ktx)
    // implementation(libs.androidx.foundation) // This is also part of the BOM

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ViewModel for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Retrofit & Moshi (ensure aliases point to correct versions)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Traditional UI components (only if you're mixing XML views)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle (for non-Compose ViewModel/LiveData, if used)
    // implementation(libs.androidx.lifecycle.livedata.ktx)
    // implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Navigation (for non-Compose Navigation, if used)
    // implementation(libs.androidx.navigation.fragment.ktx)
    // implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // Java language implementation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Kotlin
    implementation(libs.androidx.navigation.fragment.ktx.v277)
    implementation(libs.androidx.navigation.ui.ktx.v277)

    // Feature module Support
    implementation(libs.androidx.navigation.dynamic.features.fragment)

    // Testing Navigation
    androidTestImplementation(libs.androidx.navigation.testing)

    // Jetpack Compose Integration
    implementation(libs.androidx.navigation.compose)
}