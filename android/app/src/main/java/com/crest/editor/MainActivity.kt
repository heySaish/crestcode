package com.crest.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val TAG = "CrestWebView"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrestEditorScreen(
                        onWebViewCreated = { wv ->
                            webView = wv
                        },
                        bridge = CrestAndroidBridge()
                    )
                }
            }
        }
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CrestEditorScreen(
    onWebViewCreated: (WebView) -> Unit,
    bridge: Any
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                }

                addJavascriptInterface(bridge, "CrestAndroidBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d("CrestWebView", "[JS Console] ${it.sourceId()}:${it.lineNumber()} -> ${it.message()}")
                        }
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("CrestWebView", "WebView page finished loading: $url")
                    }
                }

                loadUrl("file:///android_asset/editor/index.html")
                onWebViewCreated(this)
            }
        }
    )
}
