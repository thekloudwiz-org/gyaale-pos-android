package com.thekloudwiz.gyaale.pos

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.thekloudwiz.gyaale.pos.bridge.GyaalePOSBridge
import com.thekloudwiz.gyaale.pos.databinding.ActivityMainBinding
import com.thekloudwiz.gyaale.pos.printer.PrinterDriver
import com.thekloudwiz.gyaale.pos.printer.SunmiPrinterDriver

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printer: PrinterDriver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        printer = SunmiPrinterDriver(this)
        printer.connect()

        configureWebView(binding.webview)
        binding.webview.loadUrl(START_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(web: WebView) {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // The JS bridge — JS calls window.GyaalePOS.printReceiptBase64(b64).
        web.addJavascriptInterface(GyaalePOSBridge(printer, this), "GyaalePOS")

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                // Surface a capability flag so the web app can branch on
                // "are we inside the POS wrapper?" without sniffing user-agent.
                view.evaluateJavascript(
                    """
                    window.GyaalePOS_CAPABILITIES = {
                      printer: true,
                      cashDrawer: true,
                      version: "${BuildConfig.VERSION_NAME}"
                    };
                    window.dispatchEvent(new CustomEvent('gyaalepos-ready'));
                    """.trimIndent(),
                    null,
                )
            }
        }

        web.webChromeClient = WebChromeClient()
    }

    override fun onDestroy() {
        printer.disconnect()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        // Replace with a build-flavour switch if you ever ship dev / prod APKs.
        private const val START_URL = "https://app.gyaale.thekloudwiz.com"
    }
}
