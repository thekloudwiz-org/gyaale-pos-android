# SUNMI SDK uses runtime reflection on its AIDL stubs — keep them.
-keep class com.sunmi.peripheral.printer.** { *; }
-keep class woyou.aidlservice.** { *; }
# WebView JS bridge methods are looked up by name from JavaScript.
-keepclassmembers class com.thekloudwiz.gyaale.pos.bridge.GyaalePOSBridge {
    @android.webkit.JavascriptInterface <methods>;
}
