package com.thekloudwiz.gyaale.pos.bridge

import android.app.Activity
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.thekloudwiz.gyaale.pos.printer.PrinterDriver

/**
 * Bridge exposed to the WebView as `window.GyaalePOS`. The JS side renders
 * the receipt to ESC/POS bytes (see app/lambdas/shared/receiptEscPos.js
 * in the main repo) and passes a base64 string here.
 *
 * Every method runs on a worker thread invoked from JS — Android's
 * JavascriptInterface contract guarantees it isn't on the UI thread.
 * SUNMI's printer service is itself synchronous and thread-safe.
 */
class GyaalePOSBridge(
    private val printer: PrinterDriver,
    private val activity: Activity,
) {

    /** JS: window.GyaalePOS.isPrinterReady() — returns a boolean string. */
    @JavascriptInterface
    fun isPrinterReady(): Boolean = printer.isConnected

    /** JS: window.GyaalePOS.getCapabilities() — JSON describing what we expose. */
    @JavascriptInterface
    fun getCapabilities(): String {
        val obj = mapOf(
            "printer" to printer.isConnected,
            "printerLabel" to printer.label,
            "cashDrawer" to true,
            "scanner" to false,         // TODO: hook intent broadcast + key-event capture
            "customerDisplay" to false, // TODO: T1 / T2 dual-screen Presentation API
        )
        return obj.entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":${if (v is Boolean || v is Number) v else "\"$v\""}"
        }
    }

    /**
     * JS: window.GyaalePOS.printReceiptBase64(b64).
     * Decodes ESC/POS bytes and forwards to the driver.
     * Returns true if queued, false if the printer isn't bound.
     */
    @JavascriptInterface
    fun printReceiptBase64(base64: String): Boolean {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            printer.printRaw(bytes)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Bad base64 from JS: ${e.message}")
            false
        }
    }

    /** JS: window.GyaalePOS.openCashDrawer(). */
    @JavascriptInterface
    fun openCashDrawer(): Boolean = printer.openCashDrawer()

    /** JS: window.GyaalePOS.versionName() — surfaces app version for debug. */
    @JavascriptInterface
    fun versionName(): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""

    companion object {
        private const val TAG = "GyaalePOSBridge"
    }
}
