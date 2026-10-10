package com.crestcode.editor

import org.json.JSONArray
import org.json.JSONObject

data class EditorPosition(val line: Int, val character: Int)
data class EditorSelection(val anchor: EditorPosition, val head: EditorPosition)
data class TokenSpan(val colStart: Int, val colEnd: Int, val tokenType: String)
data class RenderState(
    val uri: String,
    val languageId: String,
    val lines: List<String>,
    val lineCount: Int,
    val cursor: EditorPosition,
    val selection: EditorSelection,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isModified: Boolean,
    val matchingBrackets: Pair<EditorPosition, EditorPosition>? = null
)

data class CompletionItem(
    val label: String,
    val detail: String?,
    val insertText: String?
)

class NativeEditorEngine : AutoCloseable {
    private var nativePtr: Long = 0
    private var isLibraryLoaded = false

    private fun safeLogI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (t: Throwable) {
            println("[$tag] INFO: $msg")
        }
    }

    private fun safeLogE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (t: Throwable) {
            println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        }
    }

    init {
        try {
            System.loadLibrary("crest_editor")
            isLibraryLoaded = true
            nativePtr = nativeCreateEngine()
            safeLogI("NativeEditorEngine", "Successfully initialized Rust native editor engine pointer: $nativePtr")
        } catch (e: Throwable) {
            safeLogE("NativeEditorEngine", "Could not load libcrest_editor.so, falling back to pure Kotlin mock engine", e)
        }
    }

    // Fallback state if native library isn't loaded during testing
    private var fallbackUri: String = ""
    private var fallbackLanguage: String = "plaintext"
    private var fallbackLines: MutableList<String> = mutableListOf("")
    private var fallbackCursor = EditorPosition(0, 0)
    private var fallbackModified = false

    fun openFile(uri: String, languageId: String, content: String) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeOpenFile(nativePtr, uri, languageId, content)
        } else {
            fallbackUri = uri
            fallbackLanguage = languageId
            fallbackLines = if (content.isEmpty()) mutableListOf("") else content.replace("\r\n", "\n").split("\n").toMutableList()
            fallbackCursor = EditorPosition(0, 0)
            fallbackModified = false
        }
    }

    fun closeFile(uri: String) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeCloseFile(nativePtr, uri)
        }
    }

    fun insertText(text: String) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeInsertText(nativePtr, text)
        } else {
            if (fallbackLines.isEmpty()) fallbackLines.add("")
            val line = fallbackLines[fallbackCursor.line]
            val prefix = line.substring(0, fallbackCursor.character.coerceAtMost(line.length))
            val suffix = line.substring(fallbackCursor.character.coerceAtMost(line.length))

            val parts = text.replace("\r\n", "\n").split("\n")
            if (parts.size == 1) {
                fallbackLines[fallbackCursor.line] = prefix + text + suffix
                fallbackCursor = EditorPosition(fallbackCursor.line, fallbackCursor.character + text.length)
            } else {
                fallbackLines[fallbackCursor.line] = prefix + parts[0]
                for (i in 1 until parts.size - 1) {
                    fallbackLines.add(fallbackCursor.line + i, parts[i])
                }
                val lastIdx = parts.size - 1
                fallbackLines.add(fallbackCursor.line + lastIdx, parts[lastIdx] + suffix)
                fallbackCursor = EditorPosition(fallbackCursor.line + lastIdx, parts[lastIdx].length)
            }
            fallbackModified = true
        }
    }

    fun deleteBackspace() {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeDeleteBackspace(nativePtr)
        } else {
            if (fallbackLines.isEmpty()) return
            val line = fallbackLines[fallbackCursor.line]
            if (fallbackCursor.character > 0) {
                val prefix = line.substring(0, fallbackCursor.character - 1)
                val suffix = line.substring(fallbackCursor.character)
                fallbackLines[fallbackCursor.line] = prefix + suffix
                fallbackCursor = EditorPosition(fallbackCursor.line, fallbackCursor.character - 1)
            } else if (fallbackCursor.line > 0) {
                val prevLine = fallbackLines[fallbackCursor.line - 1]
                val prevLen = prevLine.length
                fallbackLines[fallbackCursor.line - 1] = prevLine + line
                fallbackLines.removeAt(fallbackCursor.line)
                fallbackCursor = EditorPosition(fallbackCursor.line - 1, prevLen)
            }
            fallbackModified = true
        }
    }

    fun moveCursor(direction: String, select: Boolean = false) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeMoveCursor(nativePtr, direction, select)
        } else {
            val maxLine = (fallbackLines.size - 1).coerceAtLeast(0)
            val currentLineLen = fallbackLines.getOrNull(fallbackCursor.line)?.length ?: 0
            fallbackCursor = when (direction) {
                "left" -> if (fallbackCursor.character > 0) EditorPosition(fallbackCursor.line, fallbackCursor.character - 1) else fallbackCursor
                "right" -> if (fallbackCursor.character < currentLineLen) EditorPosition(fallbackCursor.line, fallbackCursor.character + 1) else fallbackCursor
                "up" -> if (fallbackCursor.line > 0) EditorPosition(fallbackCursor.line - 1, fallbackCursor.character.coerceAtMost(fallbackLines[fallbackCursor.line - 1].length)) else fallbackCursor
                "down" -> if (fallbackCursor.line < maxLine) EditorPosition(fallbackCursor.line + 1, fallbackCursor.character.coerceAtMost(fallbackLines[fallbackCursor.line + 1].length)) else fallbackCursor
                "line_start" -> EditorPosition(fallbackCursor.line, 0)
                "line_end" -> EditorPosition(fallbackCursor.line, currentLineLen)
                else -> fallbackCursor
            }
        }
    }

    fun setSelection(anchorLine: Int, anchorChar: Int, headLine: Int, headChar: Int) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeSetSelection(nativePtr, anchorLine, anchorChar, headLine, headChar)
        } else {
            val validLine = headLine.coerceIn(0, (fallbackLines.size - 1).coerceAtLeast(0))
            val lineLen = fallbackLines.getOrNull(validLine)?.length ?: 0
            val validChar = headChar.coerceIn(0, lineLen)
            fallbackCursor = EditorPosition(validLine, validChar)
        }
    }

    fun selectWordAt(line: Int, col: Int) {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeSelectWordAt(nativePtr, line, col)
        }
    }

    fun getSelectedText(): String {
        if (isLibraryLoaded && nativePtr != 0L) {
            return nativeGetSelectedText(nativePtr)
        }
        return fallbackLines.getOrNull(fallbackCursor.line) ?: ""
    }

    fun deleteSelection(): String {
        if (isLibraryLoaded && nativePtr != 0L) {
            return nativeDeleteSelection(nativePtr)
        }
        return ""
    }

    fun selectAll() {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeSelectAll(nativePtr)
        }
    }

    fun setCursor(line: Int, character: Int) {
        setSelection(line, character, line, character)
    }

    fun undo(): Boolean {
        return if (isLibraryLoaded && nativePtr != 0L) {
            nativeUndo(nativePtr)
        } else false
    }

    fun redo(): Boolean {
        return if (isLibraryLoaded && nativePtr != 0L) {
            nativeRedo(nativePtr)
        } else false
    }

    fun getRenderState(): RenderState {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeGetRenderStateJson(nativePtr)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val obj = JSONObject(jsonStr)
                    val linesArr = obj.getJSONArray("lines")
                    val linesList = mutableListOf<String>()
                    for (i in 0 until linesArr.length()) {
                        linesList.add(linesArr.getString(i))
                    }

                    val curObj = obj.getJSONObject("cursor")
                    val cursor = EditorPosition(curObj.getInt("line"), curObj.getInt("character"))

                    val selObj = obj.getJSONObject("selection")
                    val ancObj = selObj.getJSONObject("anchor")
                    val headObj = selObj.getJSONObject("head")
                    val selection = EditorSelection(
                        EditorPosition(ancObj.getInt("line"), ancObj.getInt("character")),
                        EditorPosition(headObj.getInt("line"), headObj.getInt("character"))
                    )

                    val matchingBrackets = if (obj.has("matching_brackets") && !obj.isNull("matching_brackets")) {
                        try {
                            val mbArr = obj.getJSONArray("matching_brackets")
                            val openObj = mbArr.getJSONObject(0)
                            val closeObj = mbArr.getJSONObject(1)
                            Pair(
                                EditorPosition(openObj.getInt("line"), openObj.getInt("character")),
                                EditorPosition(closeObj.getInt("line"), closeObj.getInt("character"))
                            )
                        } catch (t: Throwable) {
                            null
                        }
                    } else null

                    return RenderState(
                        uri = obj.optString("uri", ""),
                        languageId = obj.optString("language_id", "plaintext"),
                        lines = linesList,
                        lineCount = obj.optInt("line_count", linesList.size),
                        cursor = cursor,
                        selection = selection,
                        canUndo = obj.optBoolean("can_undo", false),
                        canRedo = obj.optBoolean("can_redo", false),
                        isModified = obj.optBoolean("is_modified", false),
                        matchingBrackets = matchingBrackets
                    )
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing render state JSON", e)
                }
            }
        }

        return RenderState(
            uri = fallbackUri,
            languageId = fallbackLanguage,
            lines = fallbackLines,
            lineCount = fallbackLines.size,
            cursor = fallbackCursor,
            selection = EditorSelection(fallbackCursor, fallbackCursor),
            canUndo = false,
            canRedo = false,
            isModified = fallbackModified
        )
    }

    fun getHighlightSpans(startLine: Int, endLine: Int): Map<Int, List<TokenSpan>> {
        val result = mutableMapOf<Int, List<TokenSpan>>()
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeGetHighlightSpansJson(nativePtr, startLine, endLine)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val tuple = arr.getJSONArray(i)
                        val lineIdx = tuple.getInt(0)
                        val spansArr = tuple.getJSONArray(1)
                        val spansList = mutableListOf<TokenSpan>()
                        for (j in 0 until spansArr.length()) {
                            val spanObj = spansArr.getJSONObject(j)
                            spansList.add(
                                TokenSpan(
                                    colStart = spanObj.getInt("col_start"),
                                    colEnd = spanObj.getInt("col_end"),
                                    tokenType = spanObj.optString("token_type", "Default")
                                )
                            )
                        }
                        result[lineIdx] = spansList
                    }
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing highlight spans JSON", e)
                }
            }
        }
        return result
    }

    fun getContent(): String {
        return if (isLibraryLoaded && nativePtr != 0L) {
            nativeGetContent(nativePtr)
        } else {
            fallbackLines.joinToString("\n")
        }
    }

    fun getCompletions(): List<CompletionItem> {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeLspCompletionJson(nativePtr)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val arr = JSONArray(jsonStr)
                    val list = mutableListOf<CompletionItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            CompletionItem(
                                label = obj.getString("label"),
                                detail = if (obj.has("detail")) obj.optString("detail") else null,
                                insertText = if (obj.has("insert_text")) obj.optString("insert_text") else obj.getString("label")
                            )
                        )
                    }
                    return list
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing completions JSON", e)
                }
            }
        }
        return emptyList()
    }

    override fun close() {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeDestroyEngine(nativePtr)
            nativePtr = 0L
        }
    }

    // Native JNI methods
    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(ptr: Long)
    private external fun nativeOpenFile(ptr: Long, uri: String, languageId: String, content: String)
    private external fun nativeCloseFile(ptr: Long, uri: String)
    private external fun nativeInsertText(ptr: Long, text: String): String
    private external fun nativeDeleteBackspace(ptr: Long): String
    private external fun nativeMoveCursor(ptr: Long, direction: String, select: Boolean)
    private external fun nativeSetSelection(ptr: Long, anchorLine: Int, anchorChar: Int, headLine: Int, headChar: Int)
    private external fun nativeSelectWordAt(ptr: Long, line: Int, col: Int)
    private external fun nativeGetSelectedText(ptr: Long): String
    private external fun nativeDeleteSelection(ptr: Long): String
    private external fun nativeSelectAll(ptr: Long)
    private external fun nativeUndo(ptr: Long): Boolean
    private external fun nativeRedo(ptr: Long): Boolean
    private external fun nativeGetRenderStateJson(ptr: Long): String
    private external fun nativeGetHighlightSpansJson(ptr: Long, startLine: Int, endLine: Int): String
    private external fun nativeGetContent(ptr: Long): String
    private external fun nativeLspCompletionJson(ptr: Long): String
    private external fun nativeLspHoverJson(ptr: Long): String
    private external fun nativeLspGotoDefinitionJson(ptr: Long): String
}
