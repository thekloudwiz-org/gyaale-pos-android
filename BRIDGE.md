# Gyaale POS — JS bridge contract

This document is the **source of truth** for the `window.GyaalePOS` JavaScript
bridge exposed by the Android wrapper to the WebView-hosted admin app
(`app.gyaale.thekloudwiz.com`).

Anything the web app calls must be listed here. Anything not listed here is
**not guaranteed** to exist across brand-specific wrapper builds, and the web
app must feature-detect before relying on it.

For the architectural overview (WebView → bridge → driver), see
[`README.md`](./README.md). This file is just the contract.

---

## Compatibility promise

| Status | What this means for the web app |
|---|---|
| **Stable** | Every wrapper build (SUNMI, Star, Epson, generic ESC/POS, future brands) implements this exactly as documented. Web app may call it without guarding behind a capability check. |
| **Optional** | Implemented only on wrappers whose hardware supports it. Web app MUST inspect `getCapabilities()` before calling. |
| **Experimental** | May change between wrapper versions. Web app should guard with both capability check AND a try/catch. |

The web app already feature-detects the bridge itself with `!!window.GyaalePOS`
and falls back to `window.print()` when it's absent (see
`app/admin/js/pos-bridge.js` in the main repo). That fallback is the
contract for non-wrapper surfaces (regular browser tabs, mobile PWA).

---

## Bridge methods

All methods are exposed as members of `window.GyaalePOS`. Per Android's
`@JavascriptInterface` contract they run on a worker thread, not on the
WebView UI thread, so synchronous hardware I/O is allowed inside the
implementation. Return values cross the bridge as primitives or strings;
**objects must be serialised to JSON by the implementation** and parsed
on the JS side.

### `isPrinterReady(): boolean`  — **Stable**

Returns `true` when the printer driver is bound and a print call would be
attempted (it does not guarantee the print will succeed; paper out / cover
open are reported on the result of `printReceiptBase64`).

```js
if (window.GyaalePOS.isPrinterReady()) {
  // safe to call printReceiptBase64
}
```

### `getCapabilities(): string` (JSON)  — **Stable**

Returns a JSON-encoded string describing what this wrapper exposes. Web app
must `JSON.parse` it.

Required keys (every wrapper must include these):

| Key | Type | Meaning |
|---|---|---|
| `printer` | `boolean` | Built-in or attached printer is bound. |
| `printerLabel` | `string` | Human-readable name for status UI (e.g. `"SUNMI built-in"`, `"Epson TM-m30 (Bluetooth)"`). |
| `cashDrawer` | `boolean` | RJ11-driven drawer or equivalent can be kicked. |
| `scanner` | `boolean` | Hardware barcode scanner is wired into intent broadcasts. |
| `customerDisplay` | `boolean` | Second-screen Presentation surface available (T1 / T2 dual screen, etc.). |

Wrappers MAY add brand-specific keys (e.g. `"sunmiModel"`) but the web app
must not depend on them — they are advisory only.

```js
const caps = JSON.parse(window.GyaalePOS.getCapabilities());
// { printer: true, printerLabel: "SUNMI built-in", cashDrawer: true, ... }
```

### `printReceiptBase64(b64: string): boolean`  — **Stable**

Decodes the base64 payload to bytes and forwards to the printer driver's
`printRaw`. The web app renders ESC/POS bytes via
`app/lambdas/shared/receiptEscPos.js` (browser port at
`app/admin/js/receiptEscPos.browser.js`) and passes them across as base64.

- Returns `true` if the print was successfully queued at the driver layer.
- Returns `false` on bad base64, driver not bound, or driver-level failure
  (paper out, cover open, hardware fault). The web app should treat `false`
  as "tell the operator to check the printer."

```js
const { base64 } = window.gyaale.escpos.renderEscPosReceipt(order);
const ok = window.GyaalePOS.printReceiptBase64(base64);
if (!ok) ui.showToast('Printer not responding — check paper and cover.', 'error');
```

The wrapper MUST NOT block longer than 5 seconds inside this call. If the
underlying SDK does, the implementation should kick off an async send and
return optimistically.

### `openCashDrawer(): boolean`  — **Optional** (`capabilities.cashDrawer`)

Kicks the cash drawer wired to the printer (most thermal printers expose
an RJ11 pin that drives a 24V solenoid in the drawer). The reference
implementation falls back to sending the universal ESC/POS kick
(`ESC p 0 25 250`) if the brand SDK call refuses.

Returns `true` if the kick was issued. There's no acknowledgment from the
hardware — `true` means "we sent the signal," not "the drawer physically
opened."

Web app must guard:

```js
if (caps.cashDrawer) window.GyaalePOS.openCashDrawer();
```

### `versionName(): string`  — **Stable**

Returns the wrapper APK's `versionName` from `BuildConfig`. The web app
uses this in the error reporter and the user-menu "About" line so support
can tell which wrapper build the operator is on without making them dig
through Android settings.

```js
const v = window.GyaalePOS.versionName(); // "1.2.0"
```

---

## Bridge-side globals set by the wrapper

The wrapper also injects a small read-only object into the page when the
WebView finishes loading. This is **convenience metadata**, not a method
call — useful for the web app to know "we're inside the POS wrapper"
before any JS bridge methods are invoked.

```js
window.GyaalePOS_CAPABILITIES = {
  printer: true,
  cashDrawer: true,
  version: "1.2.0"
};
```

A `gyaalepos-ready` event is also dispatched on `window` when the bridge
is fully wired:

```js
window.addEventListener('gyaalepos-ready', () => {
  // safe to use window.GyaalePOS from this point on
});
```

Web app code today doesn't gate on the event (the bridge is available by
the time `DOMContentLoaded` fires in practice), but new wrappers SHOULD
keep dispatching it for forward compatibility.

---

## Threading & error semantics

- **Threading.** Every method is invoked on a worker thread per the Android
  `JavascriptInterface` contract. The JS side waits synchronously for the
  return value, so an implementation may safely do blocking I/O.
- **Exceptions.** Implementations must NOT throw across the bridge. Any
  caught exception should be logged and surfaced as the documented
  failure return value (`false` for booleans, `""` for strings, `{}` for
  JSON). The web app treats falsy returns as failures.
- **No callbacks, no promises.** All current methods are synchronous. If a
  future method needs to push data the other way (e.g. scanner events), it
  goes through `WebView.evaluateJavascript` and dispatches a `CustomEvent`
  on `window` — same pattern as `gyaalepos-ready`.

---

## Porting to a new brand

The bridge layer is brand-agnostic; only the printer driver changes per
brand. Every wrapper plugs into the same JS contract above, and that's
guaranteed by injecting a different `PrinterDriver` implementation in
`MainActivity.kt`.

**To add a new brand:**

1. **Implement `PrinterDriver`** (see
   `app/src/main/java/com/thekloudwiz/gyaale/pos/printer/PrinterDriver.kt`).
   Five methods: `isConnected`, `label`, `connect`, `disconnect`,
   `printRaw(bytes)`, `openCashDrawer()`. Use the brand's SDK inside;
   keep all SDK types contained in this class.

2. **Wire it in `MainActivity.kt`** — swap `SunmiPrinterDriver(this)` for
   the new driver. Long-term, fold this into a Gradle build flavour
   (`sunmi`, `epson`, `star`, …) so one source tree produces N APKs.

3. **Bump `capabilities`** — if the brand doesn't support cash-drawer
   kicks (rare) or supports something extra (e.g. integrated MSR), reflect
   it in `getCapabilities()`. The web app already branches on these flags
   for things like the "kick drawer on cash sale" behaviour.

4. **Smoke-test the bridge** — print a receipt, kick the drawer, open the
   admin in the WebView, run one walk-in POS sale. If those three work,
   the wrapper is functionally complete; the web app doesn't need to know
   the brand.

5. **Update `printerLabel`** so support staff can identify which build
   is on a given device when troubleshooting remotely.

**Out-of-scope for a port:** anything in the web app. If you find yourself
editing JS while porting a wrapper, the bridge contract has drifted —
fix the contract here first, then the JS, then the wrapper. Brand wrappers
should never carry brand-specific JS.

---

## Versioning

This document tracks the bridge contract. When adding, removing, or
changing the semantics of a method:

1. Update this file in the same PR as the bridge change.
2. Bump the wrapper `versionName` (the web app surfaces it via
   `versionName()` so support can correlate behaviour to a build).
3. If you remove or rename an existing method, keep the old name as a
   deprecated alias for at least one wrapper release. Web app deploys
   land in seconds; APK deploys are an operator action (sideload or Play
   Store update) and can lag by days or weeks.

**Don't break the JS app trying to clean up the bridge.** If you must,
gate the change behind a feature flag in `getCapabilities()` and have the
web app branch on it for one release before removing the old path.
