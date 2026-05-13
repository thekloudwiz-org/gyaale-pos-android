plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thekloudwiz.gyaale.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thekloudwiz.gyaale.pos"
        minSdk = 23  // Android 6 — covers every SUNMI device shipped since 2019
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // SUNMI SDK has reflection paths; safer off for now
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // SUNMI printer SDK — talks to the built-in thermal printer on T1, T2,
    // V1s, V2, V2 Pro, P2, and other Sunmi devices that ship one.
    // Bound at runtime; on non-Sunmi hardware the bind fails and the
    // driver falls back to a no-op (or future ESC/POS-over-Bluetooth driver).
    implementation("com.sunmi:sunmiui:1.1.7")
    implementation("com.sunmi:printerlibrary:1.0.18")
}
