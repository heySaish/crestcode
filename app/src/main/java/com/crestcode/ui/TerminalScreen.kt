package com.crestcode.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.crestcode.runtime.AlpineManager
import com.crestcode.runtime.NativeTerminalClient
import com.crestcode.runtime.TerminalService
import com.crestcode.runtime.TerminalSessionManager
import kotlin.concurrent.thread

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onClose: () -> Unit,
    initialPath: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var statusText by remember { mutableStateOf("Initializing...") }
    var currentSession by remember { mutableStateOf<TerminalSession?>(null) }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var clientRef by remember { mutableStateOf<NativeTerminalClient?>(null) }

    var isCtrlActive by remember { mutableStateOf(false) }
    var isAltActive by remember { mutableStateOf(false) }

    val alpineManager = remember { AlpineManager(context) }

    fun showKeyboard() {
        terminalViewRef?.let { view ->
            view.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    fun hideKeyboard() {
        keyboardController?.hide()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val view = terminalViewRef ?: (context as? android.app.Activity)?.currentFocus
        if (view != null) {
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    val handleClose = {
        hideKeyboard()
        onClose()
    }

    BackHandler {
        handleClose()
    }

    LaunchedEffect(Unit) {
        try {
            val serviceIntent = Intent(context, TerminalService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Protect against background service start exception on UI launch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Alpine Terminal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Alpine Linux Shell",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (statusText.contains("Active")) Color(0xFF22C55E) else Color(0xFFEAB308)
                                        )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyboard() }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Show Keyboard"
                        )
                    }
                    IconButton(onClick = {
                        currentSession?.write("clear\r")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Screen"
                        )
                    }
                    IconButton(onClick = {
                        TerminalSessionManager.restartSession(
                            context = context,
                            initialPath = initialPath,
                            onStatusUpdate = { statusText = it },
                            onSessionReady = { session ->
                                currentSession = session
                                clientRef = TerminalSessionManager.activeClient
                                terminalViewRef?.post {
                                    terminalViewRef?.attachSession(session)
                                    showKeyboard()
                                }
                            }
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Alpine"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
                .background(Color(0xFF0F172A))
        ) {
            // Native Termux TerminalView
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = TerminalView(ctx, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setTextSize(36) // Default text size
                            isFocusable = true
                            isFocusableInTouchMode = true
                        }
                        terminalViewRef = view

                        TerminalSessionManager.getOrCreateSession(
                            context = ctx,
                            initialPath = initialPath,
                            onStatusUpdate = { status ->
                                statusText = status
                            },
                            onSessionReady = { session ->
                                currentSession = session
                                val client = TerminalSessionManager.activeClient
                                clientRef = client
                                view.post {
                                    if (client == null) {
                                        android.util.Log.e(
                                            "TerminalScreen",
                                            "Cannot attach terminal session: TerminalViewClient is null"
                                        )
                                        statusText = "Terminal Client Initialization Failed"
                                        return@post
                                    }

                                    client.terminalView = view
                                    view.setTerminalViewClient(client)
                                    view.attachSession(session)

                                    view.requestFocus()
                                    showKeyboard()
                                }
                            }
                        )

                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Termux-Style Unified Crest Extra Keys Bar
            com.crestcode.ui.components.CrestExtraKeysBar(
                isCtrlActive = isCtrlActive,
                isAltActive = isAltActive,
                onToggleCtrl = {
                    val newState = !isCtrlActive
                    isCtrlActive = newState
                    clientRef?.isCtrlActive = newState
                },
                onToggleAlt = {
                    val newState = !isAltActive
                    isAltActive = newState
                    clientRef?.isAltActive = newState
                },
                onToggleKeyboard = { showKeyboard() },
                onKeyClick = { key ->
                    val ctrl = isCtrlActive
                    val alt = isAltActive
                    if (ctrl || alt) {
                        isCtrlActive = false
                        isAltActive = false
                        clientRef?.isCtrlActive = false
                        clientRef?.isAltActive = false

                        if (key.length == 1) {
                            val ch = key[0]
                            if (ctrl) {
                                val codePoint = when {
                                    ch in 'a'..'z' -> (ch - 'a' + 1).code
                                    ch in 'A'..'Z' -> (ch - 'A' + 1).code
                                    else -> ch.code
                                }
                                currentSession?.writeCodePoint(alt, codePoint)
                            } else if (alt) {
                                currentSession?.writeCodePoint(true, ch.code)
                            }
                        } else {
                            val seq = when (key) {
                                "ESC" -> "\u001b"
                                "HOME" -> "\u001b[1~"
                                "END" -> "\u001b[4~"
                                "PGUP" -> "\u001b[5~"
                                "PGDN" -> "\u001b[6~"
                                "TAB" -> "\t"
                                "↑" -> "\u001b[A"
                                "↓" -> "\u001b[B"
                                "←" -> "\u001b[D"
                                "→" -> "\u001b[C"
                                else -> key
                            }
                            if (alt) {
                                currentSession?.write("\u001b" + seq)
                            } else {
                                currentSession?.write(seq)
                            }
                        }
                    } else {
                        val seq = when (key) {
                            "ESC" -> "\u001b"
                            "HOME" -> "\u001b[1~"
                            "END" -> "\u001b[4~"
                            "PGUP" -> "\u001b[5~"
                            "PGDN" -> "\u001b[6~"
                            "TAB" -> "\t"
                            "↑" -> "\u001b[A"
                            "↓" -> "\u001b[B"
                            "←" -> "\u001b[D"
                            "→" -> "\u001b[C"
                            else -> key
                        }
                        currentSession?.write(seq)
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            hideKeyboard()
        }
    }
}
