package com.crestcode

import android.os.Bundle
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crestcode.core.settings.SettingsManager
import com.crestcode.editor.CrestNativeEditor
import com.crestcode.runtime.AlpineManager
import com.crestcode.ui.MainViewModel
import com.crestcode.ui.TerminalScreen
import com.crestcode.ui.components.*
import com.crestcode.ui.theme.*
import com.crestcode.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var alpineManager: AlpineManager
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alpineManager = AlpineManager(this)
        settingsManager = SettingsManager(applicationContext)

        setContent {
            CrestTheme {
                val context = LocalContext.current
                val viewModel: MainViewModel = viewModel()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val fileTreeItems by viewModel.fileTreeItems.collectAsState()
                val openTabs by viewModel.openTabs.collectAsState()
                val activeTabId by viewModel.activeTabId.collectAsState()
                val activeLanguage by viewModel.activeLanguage.collectAsState()
                val showTerminal by viewModel.showTerminal.collectAsState()

                val showCreateDialog by viewModel.showCreateDialog.collectAsState()
                val isFolderCreation by viewModel.isFolderCreation.collectAsState()
                val newNameInput by viewModel.newNameInput.collectAsState()
                val validationError by viewModel.validationError.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                val workspaceDir = remember { WorkspaceManager.getWorkspaceDir(context) }

                LaunchedEffect(Unit) {
                    viewModel.loadWorkspace(context)
                }

                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        viewModel.clearToastMessage()
                    }
                }

                // Creation Dialog
                if (showCreateDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.closeCreateDialog()
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
                                        viewModel.updateNewNameInput(context, valStr)
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
                                    viewModel.createNewItem(context)
                                }
                            ) {
                                Text("Create", color = CrestAccentPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.closeCreateDialog()
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
                                            viewModel.openFile(File(item.path))
                                        }
                                    }
                                },
                                onNewFileClick = {
                                    viewModel.openCreateDialog(isFolder = false)
                                },
                                onNewFolderClick = {
                                    viewModel.openCreateDialog(isFolder = true)
                                },
                                onTerminalClick = {
                                    scope.launch {
                                        drawerState.close()
                                        viewModel.setShowTerminal(true)
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
                                    },
                                    onSaveClick = {
                                        viewModel.requestSave(context)
                                    },
                                    onUndoClick = {
                                        viewModel.engine.undo()
                                    },
                                    onRedoClick = {
                                        viewModel.engine.redo()
                                    }
                                )
                                CrestTabBar(
                                    tabs = openTabs,
                                    activeTabId = activeTabId,
                                    onTabSelect = { tabId ->
                                        viewModel.openFile(File(tabId))
                                    },
                                    onTabClose = { tabId ->
                                        viewModel.closeTab(tabId)
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
                                        viewModel.setShowTerminal(false)
                                    },
                                    initialPath = workspaceDir.absolutePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CrestNativeEditor(
                                    engine = viewModel.engine,
                                    activeTabId = activeTabId,
                                    onContentChanged = {
                                        viewModel.markActiveTabModified(true)
                                    },
                                    modifier = Modifier.fillMaxSize()
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
}
