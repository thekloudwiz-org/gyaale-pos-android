package com.thekloudwiz.gyaale.pos.printer

/**
 * Hardware-agnostic printer contract. Swap implementations per vendor
 * (SUNMI built-in, ESC/POS over Bluetooth/IP for Star/Epson) — the JS
 * bridge stays identical.
 *
 * The JS side renders the receipt to raw ESC/POS bytes (see
 * `app/lambdas/shared/receiptEscPos.js` in the main repo) and passes
 * a base64 string across. The driver decodes and forwards to whatever
 * the hardware actually wants — SUNMI's `sendRAWData`, an ESC/POS TCP
 * stream, or a USB write.
 */
interface PrinterDriver {

    /** True if the driver is bound and ready to print. */
    val isConnected: Boolean

    /** Display name for status UI. */
    val label: String

    /** Establish whatever binding the driver needs. Safe to call repeatedly. */
    fun connect()

    /** Tear down the binding. */
    fun disconnect()

    /**
     * Send a pre-rendered ESC/POS byte stream to the printer.
     * Returns true if the print queued successfully.
     */
    fun printRaw(bytes: ByteArray): Boolean

    /**
     * Kick the cash drawer wired to the printer (most cash drawers
     * connect via RJ11 to the printer and respond to ESC p 0 25 250).
     */
    fun openCashDrawer(): Boolean
}
