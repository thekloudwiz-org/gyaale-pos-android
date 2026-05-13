package com.thekloudwiz.gyaale.pos.printer

import android.content.Context
import android.os.RemoteException
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService

/**
 * Driver for the built-in thermal printer on SUNMI Android devices
 * (T1 mini-G, T2, V1s, V2, V2 Pro, P2, etc.). Binds to the system-level
 * `woyou.aidlservice.jiuiv5` printer service.
 *
 * If the bind fails (we're running on non-Sunmi hardware), every method
 * returns false — the JS bridge surfaces that to the web app so it can
 * fall back to window.print() or a future BT/IP driver.
 */
class SunmiPrinterDriver(private val context: Context) : PrinterDriver {

    private var service: SunmiPrinterService? = null

    override val isConnected: Boolean
        get() = service != null

    override val label: String = "SUNMI built-in"

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(svc: SunmiPrinterService) {
            service = svc
            Log.i(TAG, "SUNMI printer service bound")
        }

        override fun onDisconnected() {
            service = null
            Log.i(TAG, "SUNMI printer service disconnected")
        }
    }

    override fun connect() {
        if (service != null) return
        try {
            InnerPrinterManager.getInstance().bindService(context, callback)
        } catch (e: Exception) {
            // Not running on Sunmi hardware, or service unavailable.
            Log.w(TAG, "SUNMI bind failed: ${e.message}")
        }
    }

    override fun disconnect() {
        val svc = service ?: return
        try {
            InnerPrinterManager.getInstance().unBindService(context, callback)
        } catch (e: Exception) {
            Log.w(TAG, "SUNMI unbind failed: ${e.message}")
        }
        service = null
    }

    override fun printRaw(bytes: ByteArray): Boolean {
        val svc = service ?: return false
        return try {
            svc.sendRAWData(bytes, null)
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "sendRAWData failed: ${e.message}")
            false
        }
    }

    override fun openCashDrawer(): Boolean {
        val svc = service ?: return false
        return try {
            // ESC p 0 25 250 — same kick command the JS adapter would emit.
            svc.sendRAWData(byteArrayOf(0x1B, 0x70, 0x00, 0x19.toByte(), 0xFA.toByte()), null)
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "openCashDrawer failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "SunmiPrinter"
    }
}
