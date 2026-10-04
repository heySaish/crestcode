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
import com.crest.editor.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val TAG = "CrestWebView"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CrestTheme {
                val context = LocalContext.current
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val workspaceDir = remember { WorkspaceManager.getWorkspaceDir(context) }
                var fileTreeItems by remember {
                    mutableStateOf(WorkspaceManager.listFilesRecursively(workspaceDir))
                }

                var openTabs by remember { mutableStateOf<List<CrestTabItem>>(emptyList()) }
                var activeTabId by remember { mutableStateOf("") }
                var activeLanguage by remember { mutableStateOf("javascript") }

                fun openFile(file: File) {
                    if (file.isDirectory) return

                    scope.launch {
                        val path = file.absolutePath
                        val content = WorkspaceManager.readFileContent(file)
                        val language = WorkspaceManager.detectLanguage(file.name)

                        if (openTabs.none { it.id == path }) {
                            openTabs = openTabs + CrestTabItem(id = path, title = file.name)
                        }
                        activeTabId = path
                        activeLanguage = language

                        webView?.let { wv ->
                            val jsCode = "if (window.CrestEditorAPI && window.CrestEditorAPI.openFile) { " +
                                    "window.CrestEditorAPI.openFile(" +
                                    JSONObject.quote(path) + ", " +
                                    JSONObject.quote(content) + ", " +
                                    JSONObject.quote(language) + "); }"
                            wv.evaluateJavascript(jsCode, null)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    fileTreeItems = WorkspaceManager.listFilesRecursively(workspaceDir)
                    val firstFile = fileTreeItems.firstOrNull { !it.isDirectory }
                    firstFile?.let { item ->
                        openFile(File(item.path))
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            CrestFileDrawer(
                                projectName = workspaceDir.name,
                                fileList = fileTreeItems,
                                onFileSelect = { item ->
                                    scope.launch {
                                        drawerState.close()
                                        if (!item.isDirectory) {
                                            openFile(File(item.path))
                                        }
                                    }
                                },
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
                                    projectName = "workspace / " + (openTabs.find { it.id == activeTabId }?.title ?: "Crest"),
                                    onMenuClick = {
                                        scope.launch {
                                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                        }
                                    }
                                )
                                CrestTabBar(
                                    tabs = openTabs,
                                    activeTabId = activeTabId,
                                    onTabSelect = { tabId ->
                                        openFile(File(tabId))
                                    },
                                    onTabClose = { tabId ->
                                        val newTabs = openTabs.filterNot { it.id == tabId }
                                        openTabs = newTabs
                                        if (activeTabId == tabId && newTabs.isNotEmpty()) {
                                            openFile(File(newTabs.last().id))
                                        }
                                    }
                                )
                            }
                        },
                        bottomBar = {
                            Column {
                                CrestExtraKeysBar()
                                CrestStatusBar(
                                    language = activeLanguage
                                )
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
                                    if (activeTabId.isNotEmpty()) {
                                        openFile(File(activeTabId))
                                    }
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
