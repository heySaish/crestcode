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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.crest.editor.ui.components.*
import com.crest.editor.ui.theme.CrestTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val TAG = "CrestWebView"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CrestTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            CrestFileDrawer(
                                onCloseDrawer = {
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            Column {
                                CrestTopBar(
                                    onMenuClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    }
                                )
                                CrestTabBar()
                            }
                        },
                        bottomBar = {
                            Column {
                                CrestExtraKeysBar()
                                CrestStatusBar()
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            CrestEditorScreen(
                                onWebViewCreated = { wv ->
                                    webView = wv
                                },
                                bridge = remember { CrestAndroidBridge() }
                            )
                        }
                    }
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

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CrestEditorScreen(
    onWebViewCreated: (WebView) -> Unit,
    bridge: Any
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

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

            setOnTouchListener { v, _ ->
                if (!v.hasFocus()) {
                    v.requestFocus()
                }
                false
            }

            loadUrl("file:///android_asset/editor/index.html")
            onWebViewCreated(this)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webView }
    )
}
