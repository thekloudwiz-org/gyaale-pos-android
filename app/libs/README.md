# Sunmi SDK drop-in folder

Drop the Sunmi printer SDK AAR here to enable native receipt printing.

1. Sign in at https://developer.sunmi.com
2. Navigate to **Resources → SDK → Printer** (or "Printer SDK").
3. Download `SunmiPrinterLibrary_*.aar` (or whatever filename their portal serves).
4. Drop the file into this directory (`app/libs/`).
5. Rebuild: `./gradlew assembleDebug`.

The build picks it up automatically via the `flatDir` repo declared in
`settings.gradle.kts` and the `compileOnly fileTree("libs")` dep in
`app/build.gradle.kts`. The driver in `SunmiPrinterDriver.kt` reflects
into the SDK at runtime, so the APK builds fine with or without the AAR
— with it, you get real printing; without it, the driver reports
`isConnected = false` and the JS bridge falls back gracefully.

Keep the AAR out of git — it's a vendor binary and Sunmi may issue
updates / version-pinned licences. `.gitignore` already excludes `*.aar`.
