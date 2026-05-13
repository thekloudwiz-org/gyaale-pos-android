package com.thekloudwiz.gyaale.pos.printer

import android.content.Context
import android.util.Log
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.SdkException
import com.sunmi.printerx.api.PrintResult

/**
 * Driver for the built-in thermal printer on SUNMI Android devices
 * (T1 mini-G, T2, V1s, V2, V2 Pro, P2, etc.) using the official
 * SUNMI Printer SDK (`com.sunmi:printerx`, on Maven Central).
 *
 * Flow:
 *   1. connect() → PrinterSdk.getInstance().getPrinter(ctx, listener)
 *   2. listener.onDefPrinter(p) fires when the SDK has bound to the
 *      built-in printer service; we cache the Printer reference.
 *   3. printRaw(bytes) → printer.commandApi().sendEscCommand(bytes).
 *      The JS bridge renders receipts to ESC/POS via
 *      app/lambdas/shared/receiptEscPos.js so the same bytes work
 *      across every ESC/POS-speaking printer.
 *   4. openCashDrawer() → printer.cashDrawerApi().open(callback).
 *      Falls back to an ESC/POS kick if the SDK call refuses (e.g.
 *      on devices without an integrated drawer interface).
 *
 * Every SDK call can throw SdkException; we treat that as "not ready"
 * and let the JS bridge surface that to the web app so it can degrade
 * to window.print() on non-Sunmi hardware.
 */
class SunmiPrinterDriver(private val context: Context) : PrinterDriver {

    @Volatile private var printer: PrinterSdk.Printer? = null

    override val isConnected: Boolean
        get() = printer != null

    override val label: String = "SUNMI built-in"

    private val listener = object : PrinterSdk.PrinterListen {
        override fun onDefPrinter(p: PrinterSdk.Printer?) {
            printer = p
            Log.i(TAG, "SUNMI default printer bound: ${p?.toString()}")
        }

        override fun onPrinters(list: MutableList<PrinterSdk.Printer>?) {
            Log.i(TAG, "SUNMI printers visible: ${list?.size ?: 0}")
            // Multi-printer scenario — log only. Future settings screen
            // can let the operator pick a non-default printer by ID.
        }
    }

    override fun connect() {
        if (printer != null) return
        try {
            PrinterSdk.getInstance().getPrinter(context, listener)
        } catch (e: SdkException) {
            Log.w(TAG, "PrinterSdk.getPrinter refused: ${e.message}")
        } catch (e: Throwable) {
            // NoClassDefFoundError, SecurityException, anything else.
            Log.w(TAG, "PrinterSdk init failed: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    override fun disconnect() {
        try { PrinterSdk.getInstance().destroy() } catch (_: Throwable) { /* already gone */ }
        printer = null
    }

    override fun printRaw(bytes: ByteArray): Boolean {
        val p = printer ?: return false
        return try {
            p.commandApi().sendEscCommand(bytes)
            true
        } catch (e: SdkException) {
            Log.e(TAG, "sendEscCommand failed: ${e.message}")
            false
        } catch (e: Throwable) {
            Log.e(TAG, "sendEscCommand threw: ${e.message}")
            false
        }
    }

    override fun openCashDrawer(): Boolean {
        val p = printer ?: return false
        return try {
            p.cashDrawerApi().open(object : PrintResult() {
                override fun onResult(code: Int, msg: String?) {
                    Log.i(TAG, "cash drawer open result: code=$code msg=$msg")
                }
            })
            true
        } catch (e: Throwable) {
            Log.w(TAG, "cashDrawerApi.open failed: ${e.message} — falling back to raw kick")
            // ESC p 0 25 250 — the universal RJ11 drawer kick.
            printRaw(byteArrayOf(0x1B, 0x70, 0x00, 0x19.toByte(), 0xFA.toByte()))
        }
    }

    companion object {
        private const val TAG = "SunmiPrinter"
    }
}
