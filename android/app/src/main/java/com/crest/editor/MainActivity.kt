package com.crest.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val TAG = "CrestWebView"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(false)
        webSettings.builtInZoomControls = false
        webSettings.displayZoomControls = false

        // Bridge interface for Monaco <-> Android Native communication
        webView.addJavascriptInterface(CrestAndroidBridge(), "CrestAndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(TAG, "[JS Console] ${it.sourceId()}:${it.lineNumber()} -> ${it.message()}")
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "WebView page finished loading: $url")
            }
        }

        // Load Monaco Editor web interface from Android assets
        webView.loadUrl("file:///android_asset/editor/index.html")
    }

    inner class CrestAndroidBridge {
        @JavascriptInterface
        fun getDeviceInfo(): String {
            return "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
        }

        @JavascriptInterface
        fun onEditorReady(infoJson: String) {
            Log.i(TAG, "Monaco Editor READY signal received from WebView: $infoJson")
        }

        @JavascriptInterface
        fun onContentChanged(contentStats: String) {
            Log.d(TAG, "Monaco Editor document content changed: $contentStats")
        }

        @JavascriptInterface
        fun logNative(message: String) {
            Log.i(TAG, "[Monaco WebView Log]: $message")
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
