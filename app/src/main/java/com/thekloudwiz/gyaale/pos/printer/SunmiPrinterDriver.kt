package com.thekloudwiz.gyaale.pos.printer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * Driver for the built-in thermal printer on SUNMI Android devices
 * (T1 mini-G, T2, V1s, V2, V2 Pro, P2, etc.).
 *
 * The SUNMI printer SDK isn't on a public Maven endpoint; vendors drop
 * `SunmiPrinterLibrary*.aar` into `app/libs/` after downloading it from
 * the SUNMI developer portal. To keep the APK buildable WITHOUT the SDK
 * (so you can ship a WebView-only build and add printing later), this
 * driver talks to the printer service via reflection rather than direct
 * SDK imports. Two outcomes:
 *
 *   - SDK AAR present at compile time: reflection finds the classes,
 *     binds the AIDL service, prints work.
 *   - SDK AAR absent: reflection lookups fail silently in `connect()`,
 *     the driver reports `isConnected = false`, and the JS bridge
 *     surfaces that to the web app so it can fall back to window.print().
 *
 * The AIDL service name (`woyou.aidlservice.jiuiv5`) is what every Sunmi
 * device exposes — the same identifier is queried in AndroidManifest.xml.
 */
class SunmiPrinterDriver(private val context: Context) : PrinterDriver {

    private var serviceBinder: IBinder? = null

    override val isConnected: Boolean
        get() = serviceBinder != null

    override val label: String = "SUNMI built-in"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = binder
            Log.i(TAG, "SUNMI printer service bound")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            Log.i(TAG, "SUNMI printer service disconnected")
        }
    }

    override fun connect() {
        if (serviceBinder != null) return
        val intent = Intent().apply {
            setPackage("woyou.aidlservice.jiuiv5")
            action = "woyou.aidlservice.jiuiv5.IWoyouService"
        }
        try {
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.w(TAG, "bindService returned false — not a Sunmi device, or service unavailable")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SUNMI bind failed (permission): ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "SUNMI bind failed: ${e.message}")
        }
    }

    override fun disconnect() {
        if (serviceBinder == null) return
        try { context.unbindService(connection) } catch (_: Exception) { /* not bound */ }
        serviceBinder = null
    }

    /**
     * Reflective forward to `IWoyouService.sendRAWData(byte[], ICallback)`.
     * The AIDL stub class is `woyou.aidlservice.jiuiv5.IWoyouService$Stub`
     * with `asInterface(IBinder)` returning the proxy. We invoke
     * `sendRAWData` with a null callback — fire-and-forget.
     */
    override fun printRaw(bytes: ByteArray): Boolean {
        val binder = serviceBinder ?: return false
        return try {
            val stubClass = Class.forName("woyou.aidlservice.jiuiv5.IWoyouService\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val service = asInterface.invoke(null, binder)
            val iface = Class.forName("woyou.aidlservice.jiuiv5.IWoyouService")
            val callbackIface = Class.forName("woyou.aidlservice.jiuiv5.ICallback")
            val sendRaw = iface.getMethod("sendRAWData", ByteArray::class.java, callbackIface)
            sendRaw.invoke(service, bytes, null)
            true
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "SUNMI SDK not on classpath — printing skipped. Drop the AAR into app/libs/ to enable.")
            false
        } catch (e: Exception) {
            Log.e(TAG, "sendRAWData reflective invoke failed: ${e.message}")
            false
        }
    }

    override fun openCashDrawer(): Boolean {
        // ESC p 0 25 250 — same kick command the JS adapter emits.
        return printRaw(byteArrayOf(0x1B, 0x70, 0x00, 0x19.toByte(), 0xFA.toByte()))
    }

    companion object {
        private const val TAG = "SunmiPrinter"
    }
}
