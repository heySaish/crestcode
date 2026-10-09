package com.crestcode.runtime

import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import java.io.File
import kotlin.concurrent.thread

object TerminalSessionManager {
    private const val TAG = "TerminalSessionManager"

    var activeSession: TerminalSession? = null
        private set
    var activeClient: NativeTerminalClient? = null
        private set

    private var isInitializing = false

    fun getOrCreateSession(
        context: Context,
        initialPath: String?,
        onStatusUpdate: (String) -> Unit,
        onSessionReady: (TerminalSession) -> Unit
    ) {
        val existingSession = activeSession
        if (existingSession != null && existingSession.isRunning) {
            Log.d(TAG, "Reattaching to existing active TerminalSession")
            onStatusUpdate("Alpine Linux Active")
            onSessionReady(existingSession)
            return
        }

        if (isInitializing) {
            Log.d(TAG, "Session initialization already in progress")
            return
        }

        isInitializing = true
        onStatusUpdate("Initializing Alpine Environment...")

        thread {
            try {
                val alpineManager = AlpineManager(context)
                val ready = alpineManager.setupAlpineEnvironment { status ->
                    onStatusUpdate(status)
                }

                if (ready) {
                    val client = NativeTerminalClient(context, onSessionFinishedCallback = {
                        onStatusUpdate("Alpine Process Terminated")
                    })
                    val session = alpineManager.createAlpineTerminalSession(client, initialPath)

                    activeClient = client
                    activeSession = session
                    isInitializing = false

                    onStatusUpdate("Alpine Linux Active")
                    onSessionReady(session)
                } else {
                    isInitializing = false
                    onStatusUpdate("Alpine Setup Failed")
                }
            } catch (e: Exception) {
                isInitializing = false
                Log.e(TAG, "Error initializing terminal session", e)
                onStatusUpdate("Error: ${e.localizedMessage}")
            }
        }
    }

    fun restartSession(
        context: Context,
        initialPath: String?,
        onStatusUpdate: (String) -> Unit,
        onSessionReady: (TerminalSession) -> Unit
    ) {
        activeSession?.finishIfRunning()
        activeSession = null
        activeClient = null
        isInitializing = false
        getOrCreateSession(context, initialPath, onStatusUpdate, onSessionReady)
    }

    fun terminateSession() {
        activeSession?.finishIfRunning()
        activeSession = null
        activeClient = null
        isInitializing = false
    }
}
