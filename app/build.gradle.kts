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

    // The dev box may be running gradle on a much newer JDK than AGP 8.x
    // supports (we've seen JDK 25 here). Pin the toolchain to 17 and let
    // the foojay resolver in settings.gradle.kts auto-download it.
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    buildFeatures {
        viewBinding = true
        // AGP 8.x disables BuildConfig generation by default. We use
        // BuildConfig.DEBUG and BuildConfig.VERSION_NAME in MainActivity.
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")

    // SUNMI printer SDK — optional. The driver auto-detects whether the
    // SDK is on the classpath and switches between the real implementation
    // and a no-op stub. Download SunmiPrinterLibrary.aar from
    // https://developer.sunmi.com and drop it into app/libs/ to enable.
    // The build succeeds either way; without the AAR you still get a
    // working WebView-only APK (window.print() fallback).
    compileOnly(fileTree("libs") { include("*.aar") })
    runtimeOnly(fileTree("libs") { include("*.aar") })
}
