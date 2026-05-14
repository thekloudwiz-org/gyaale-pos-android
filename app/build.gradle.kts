plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thekloudwiz.gyaale.pos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thekloudwiz.gyaale.pos"
        // Android 5.1 (API 22) covers the V1-B18 mobile POS in addition to every
        // SUNMI device shipped since 2019. The V1 hardware predates the Android-6
        // floor we'd otherwise prefer; nothing the wrapper does (JavascriptInterface,
        // evaluateJavascript, setWebContentsDebuggingEnabled) requires API 23+, and
        // the PrinterX SDK we depend on still targets the V1's printer service.
        minSdk = 22
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
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

    // SUNMI Printer SDK — high-level API (Printer, LineApi, CommandApi,
    // CashDrawerApi, etc.). Hosted on Maven Central, so no special repo
    // needed beyond what's already declared in settings.gradle.kts.
    // The driver guards every call so a non-Sunmi device just reports
    // isPrinterReady = false and lets the web fall back to window.print().
    implementation("com.sunmi:printerx:1.0.17")

    // Drop-in slot for additional vendor SDK AARs (Star, Epson, future
    // Sunmi versions) — see app/libs/README.md.
    compileOnly(fileTree("libs") { include("*.aar") })
    runtimeOnly(fileTree("libs") { include("*.aar") })
}
