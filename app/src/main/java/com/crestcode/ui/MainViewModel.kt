package com.crestcode.ui

import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crestcode.editor.MonacoBridge
import com.crestcode.ui.components.CrestTabItem
import com.crestcode.ui.components.FileTreeItem
import com.crestcode.workspace.WorkspaceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel : ViewModel() {

    private val _fileTreeItems = MutableStateFlow<List<FileTreeItem>>(emptyList())
    val fileTreeItems: StateFlow<List<FileTreeItem>> = _fileTreeItems.asStateFlow()

    private val _openTabs = MutableStateFlow<List<CrestTabItem>>(emptyList())
    val openTabs: StateFlow<List<CrestTabItem>> = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _activeLanguage = MutableStateFlow("javascript")
    val activeLanguage: StateFlow<String> = _activeLanguage.asStateFlow()

    private val _showTerminal = MutableStateFlow(false)
    val showTerminal: StateFlow<Boolean> = _showTerminal.asStateFlow()

    // Dialog states
    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _isFolderCreation = MutableStateFlow(false)
    val isFolderCreation: StateFlow<Boolean> = _isFolderCreation.asStateFlow()

    private val _newNameInput = MutableStateFlow("")
    val newNameInput: StateFlow<String> = _newNameInput.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun loadWorkspace(context: Context, webView: WebView? = null) {
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val items = WorkspaceManager.listFilesRecursively(workspaceDir)
            _fileTreeItems.value = items

            if (_openTabs.value.isEmpty()) {
                val firstFile = items.firstOrNull { !it.isDirectory }
                firstFile?.let { item ->
                    openFile(File(item.path), webView)
                }
            }
        }
    }

    fun refreshWorkspace(context: Context) {
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            _fileTreeItems.value = WorkspaceManager.listFilesRecursively(workspaceDir)
        }
    }

    fun openFile(file: File, webView: WebView?) {
        if (file.isDirectory) return

        viewModelScope.launch {
            val path = file.absolutePath
            val readResult = WorkspaceManager.readFileContent(file)
            val content = readResult.getOrDefault("")
            val language = WorkspaceManager.detectLanguage(file.name)

            val currentTabs = _openTabs.value
            if (currentTabs.none { it.id == path }) {
                _openTabs.value = currentTabs + CrestTabItem(id = path, title = file.name, isModified = false)
            }
            _activeTabId.value = path
            _activeLanguage.value = language

            MonacoBridge.openFile(webView, path, content, language)
        }
    }

    fun closeTab(tabId: String, webView: WebView?) {
        val currentTabs = _openTabs.value
        val newTabs = currentTabs.filterNot { it.id == tabId }
        _openTabs.value = newTabs

        if (_activeTabId.value == tabId) {
            if (newTabs.isNotEmpty()) {
                val nextTabFile = File(newTabs.last().id)
                openFile(nextTabFile, webView)
            } else {
                _activeTabId.value = ""
                _activeLanguage.value = "-"
                MonacoBridge.clearEditor(webView)
            }
        }
    }

    fun markActiveTabModified(isModified: Boolean) {
        val currentActive = _activeTabId.value
        if (currentActive.isEmpty()) return

        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == currentActive) {
                tab.copy(isModified = isModified)
            } else {
                tab
            }
        }
    }

    fun saveContent(context: Context, filePath: String, content: String) {
        viewModelScope.launch {
            val targetFile = File(filePath)
            val writeResult = WorkspaceManager.writeFileContent(targetFile, content)

            writeResult.onSuccess {
                _openTabs.value = _openTabs.value.map { tab ->
                    if (tab.id == filePath) tab.copy(isModified = false) else tab
                }
                _toastMessage.value = "Saved ${targetFile.name}"
            }.onFailure { ex ->
                _toastMessage.value = "Save failed: ${ex.localizedMessage}"
            }
        }
    }

    fun requestSave(context: Context, webView: WebView?) {
        val currentActive = _activeTabId.value
        if (currentActive.isEmpty()) return

        MonacoBridge.getContent(webView) { content ->
            saveContent(context, currentActive, content)
        }
    }

    fun setShowTerminal(show: Boolean) {
        _showTerminal.value = show
    }

    fun openCreateDialog(isFolder: Boolean) {
        _isFolderCreation.value = isFolder
        _newNameInput.value = ""
        _validationError.value = null
        _showCreateDialog.value = true
    }

    fun closeCreateDialog() {
        _showCreateDialog.value = false
        _newNameInput.value = ""
        _validationError.value = null
    }

    fun updateNewNameInput(context: Context, name: String) {
        _newNameInput.value = name
        val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
        _validationError.value = WorkspaceManager.validateName(workspaceDir, name)
    }

    fun createNewItem(context: Context, webView: WebView?) {
        viewModelScope.launch {
            val workspaceDir = WorkspaceManager.getWorkspaceDir(context)
            val name = _newNameInput.value
            val err = WorkspaceManager.validateName(workspaceDir, name)

            if (err != null) {
                _validationError.value = err
                return@launch
            }

            val isFolder = _isFolderCreation.value
            val result = if (isFolder) {
                WorkspaceManager.createNewFolder(workspaceDir, name)
            } else {
                WorkspaceManager.createNewFile(workspaceDir, name)
            }

            result.onSuccess { createdFile ->
                closeCreateDialog()
                refreshWorkspace(context)
                if (!createdFile.isDirectory) {
                    openFile(createdFile, webView)
                }
            }.onFailure { ex ->
                _validationError.value = ex.message ?: "Creation failed."
            }
        }
    }
}
