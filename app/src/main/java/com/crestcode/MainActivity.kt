package com.crestcode

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crestcode.ui.components.*
import com.crestcode.ui.theme.*
import com.crestcode.workspace.WorkspaceManager
import com.crestcode.ui.TerminalScreen
import com.crestcode.runtime.AlpineManager
import com.crestcode.core.settings.SettingsManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var alpineManager: AlpineManager
    private lateinit var settingsManager: SettingsManager

    private var webView: WebView? = null
    private val TAG = "CrestWebView"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alpineManager = AlpineManager(this)
        settingsManager = SettingsManager(applicationContext)

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

                // Dialog states for creation
                var showCreateDialog by remember { mutableStateOf(false) }
            var showTerminal by remember { mutableStateOf(false) }
                var isFolderCreation by remember { mutableStateOf(false) }
                var newNameInput by remember { mutableStateOf("") }
                var validationError by remember { mutableStateOf<String?>(null) }

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

                // Creation Dialog
                if (showCreateDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showCreateDialog = false
                            newNameInput = ""
                            validationError = null
                        },
                        title = {
                            Text(
                                text = if (isFolderCreation) "New Folder" else "New File",
                                color = CrestTextActive
                            )
                        },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = newNameInput,
                                    onValueChange = { valStr ->
                                        newNameInput = valStr
                                        validationError = WorkspaceManager.validateName(workspaceDir, valStr)
                                    },
                                    label = { Text(if (isFolderCreation) "Folder Name" else "File Name") },
                                    singleLine = true,
                                    isError = validationError != null,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CrestAccentPrimary,
                                        focusedLabelColor = CrestAccentPrimary
                                    )
                                )
                                if (validationError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = validationError!!,
                                        color = CrestAccentRed,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val err = WorkspaceManager.validateName(workspaceDir, newNameInput)
                                    if (err == null) {
                                        scope.launch {
                                            val result = if (isFolderCreation) {
                                                WorkspaceManager.createNewFolder(workspaceDir, newNameInput)
                                            } else {
                                                WorkspaceManager.createNewFile(workspaceDir, newNameInput)
                                            }
                                            result.onSuccess { createdFile ->
                                                showCreateDialog = false
                                                newNameInput = ""
                                                validationError = null
                                                fileTreeItems = WorkspaceManager.listFilesRecursively(workspaceDir)
                                                if (!createdFile.isDirectory) {
                                                    openFile(createdFile)
                                                }
                                            }.onFailure { ex ->
                                                validationError = ex.message ?: "Creation failed."
                                            }
                                        }
                                    } else {
                                        validationError = err
                                    }
                                }
                            ) {
                                Text("Create", color = CrestAccentPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showCreateDialog = false
                                    newNameInput = ""
                                    validationError = null
                                }
                            ) {
                                Text("Cancel", color = CrestTextSecondary)
                            }
                        },
                        containerColor = CrestSurfaceHeader
                    )
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
                                onNewFileClick = {
                                    isFolderCreation = false
                                    newNameInput = ""
                                    validationError = null
                                    showCreateDialog = true
                                },
                                onNewFolderClick = {
                                    isFolderCreation = true
                                    newNameInput = ""
                                    validationError = null
                                    showCreateDialog = true
                                },
                                onTerminalClick = {
                                    scope.launch {
                                        drawerState.close()
                                        showTerminal = true
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
                                    projectName = "workspace / " + (openTabs.find { it.id == activeTabId }?.title ?: ""),
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
                                        if (activeTabId == tabId) {
                                            if (newTabs.isNotEmpty()) {
                                                openFile(File(newTabs.last().id))
                                            } else {
                                                activeTabId = ""
                                                activeLanguage = "-"
                                                webView?.evaluateJavascript(
                                                    "if (window.CrestEditorAPI && window.CrestEditorAPI.clearEditor) { window.CrestEditorAPI.clearEditor(); }",
                                                    null
                                                )
                                            }
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
                            if (showTerminal) {
                                TerminalScreen(
                                    onClose = {
                                        showTerminal = false
                                    },
                                    initialPath = workspaceDir.absolutePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
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

                            if (!showTerminal && openTabs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(CrestBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Code,
                                            contentDescription = null,
                                            tint = CrestTextSecondary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No Open Files",
                                            color = CrestTextSecondary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Select a file from the menu drawer to start editing",
                                            color = CrestTextSecondary.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
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
