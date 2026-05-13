# Gyaale POS — Android wrapper

Thin Android WebView shell around the Gyaale tenant operations site (`app.gyaale.thekloudwiz.com`), with native bindings for printer / cash-drawer / scanner / customer-display hardware.

Built for **SUNMI** POS devices first (T1 mini-G, P2 mobile, T2, V-series), but the printer layer is interface-driven so non-Sunmi printers (Star, Epson, generic ESC/POS over Bluetooth or IP) can be added without touching the WebView side.

## How it fits together

```
┌─ Gyaale POS APK ──────────────────────────────────────────┐
│                                                           │
│  MainActivity                                             │
│   └─ WebView → https://app.gyaale.thekloudwiz.com         │
│        └─ window.GyaalePOS (JavaScript bridge)            │
│              │                                            │
│              ▼                                            │
│        GyaalePOSBridge.kt  (Java <-> JS contract)         │
│              │                                            │
│              ▼                                            │
│        PrinterDriver interface                            │
│         ├─ SunmiPrinterDriver  (built-in, default)        │
│         ├─ EscPosTcpDriver     (planned)                  │
│         └─ EscPosBluetoothDriver (planned)                │
└───────────────────────────────────────────────────────────┘
```

The web app renders the receipt to ESC/POS bytes in JS using
`app/lambdas/shared/receiptEscPos.js` from the main Gyaale repo and
sends a base64 string across the bridge. The driver decodes and pushes
to the hardware. One receipt format, every supported printer.

## JS bridge contract

When the wrapper hosts the page, `window.GyaalePOS` is defined. The web
app feature-detects via:

```js
const onPOS = !!window.GyaalePOS;
const caps  = onPOS ? JSON.parse(window.GyaalePOS.getCapabilities()) : {};
```

| Method | Returns | Notes |
|---|---|---|
| `isPrinterReady()` | `boolean` | Printer service bound and ready. |
| `getCapabilities()` | JSON string | `{ printer, printerLabel, cashDrawer, scanner, customerDisplay }`. |
| `printReceiptBase64(b64)` | `boolean` | Base64-encoded ESC/POS bytes. |
| `openCashDrawer()` | `boolean` | RJ11-wired cash drawers, kicked via ESC p. |
| `versionName()` | `string` | App version for debug surface. |

Bridge methods run off the WebView UI thread (Android contract) so they can do synchronous hardware I/O without blocking the JS event loop on the page side.

## Build

Requires JDK 17 (auto-resolved by the foojay plugin if you don't have it), Android SDK 34, and a Gradle wrapper. The wrapper jar is checked in — first checkout just runs:

```bash
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed APK (requires release keystore)
```

If you don't have the Android SDK installed yet, install Android Studio and let it download SDK 34 + build-tools 34.0.0. Or grab the command-line tools and run `sdkmanager "platforms;android-34" "build-tools;34.0.0"`.

## Sunmi SDK (optional)

The driver compiles and runs without the Sunmi SDK on the classpath — `isPrinterReady()` returns `false` and the JS bridge surfaces that so the web app can fall back to `window.print()`. To enable real printing:

1. Sign in at https://developer.sunmi.com
2. Download `SunmiPrinterLibrary_*.aar` from the Printer SDK section.
3. Drop the AAR into `app/libs/` (see `app/libs/README.md`).
4. Rebuild — `app/build.gradle.kts` picks it up via `flatDir`. The driver's reflective lookups now find the service interface and route prints to the built-in thermal printer.

## Signing the release build

Create `keystore.properties` in the repo root (gitignored):

```
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=gyaale-pos
keyPassword=...
```

Add a `signingConfigs.release {}` block reading those values in `app/build.gradle.kts` when you're ready. A stub `signingConfig` falls back to the debug config if `release` isn't configured — fine for sideloading the first device, not for distribution.

## Installing on a SUNMI device

1. Enable Developer Options → USB debugging on the device.
2. Plug into a laptop, `adb devices` to confirm visibility.
3. `adb install app-debug.apk` (or `-r` to update).
4. Launch "Gyaale POS" from the home screen. The WebView loads
   `app.gyaale.thekloudwiz.com`; sign in as the OWNER / MANAGER / WAITER /
   CHEF user you provisioned in the Gyaale admin UI.
5. First print sanity check: with the Sunmi SDK AAR in place, place a
   walk-in cash order in admin POS — a receipt should print on the
   built-in thermal printer.

For fleet rollouts use **SUNMI Assistant** to push the APK from the
Sunmi dashboard, or publish to the SUNMI App Store once distribution
is set up.

## Roadmap

| Phase | Status | Notes |
|---|---|---|
| MVP — WebView + SUNMI printer + cash drawer | ✅ APK builds | Drop the Sunmi AAR into `app/libs/` to enable real printing. |
| Wire `POSBridge.printReceipt()` into admin POS | todo | One-liner in `app/admin/js/admin-pos.js` on the main repo. |
| Scanner | todo | SUNMI scanner key events + barcode intent broadcast on P2. |
| Customer-facing display | todo | T1 / T2 dual-screen — show running cart total to the customer using Android's `Presentation` API. |
| ESC/POS Bluetooth driver | todo | Generic 58/80mm BT printers (Goojprt, Munbyn). |
| ESC/POS TCP driver | todo | Star TSP100ECO / Epson TM-m30 over LAN. |
| Low-paper / printer-error events | todo | Surface to JS so the web app can show "paper out" toasts. |
| SUNMI App Store distribution | todo | Signed release build + store listing. |

## Companion changes in the Gyaale web repo

`app/lambdas/shared/receiptEscPos.js` is the JS adapter that produces the bytes this APK forwards. Two test orders (PICKUP with code, DELIVERY with address) are covered in `app/lambdas/__tests__/services/receiptEscPos.test.js`.

The web app should feature-detect `window.GyaalePOS` and, when present, call `GyaalePOS.printReceiptBase64(b64)` instead of `window.print()` after a successful order or status transition. That patch is the next thing to ship — kept on the web side so the wrapper APK can stay version-pinned.
