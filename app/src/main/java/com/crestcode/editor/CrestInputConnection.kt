package com.crestcode.editor

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest

class CrestInputConnection(
    targetView: View,
    private val engine: NativeEditorEngine,
    private val onContentChanged: () -> Unit
) : BaseInputConnection(targetView, false) {

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val count = beforeLength.coerceAtLeast(1)
        repeat(count) {
            engine.deleteBackspace()
        }
        onContentChanged()
        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        text?.toString()?.let { str ->
            if (str.isNotEmpty()) {
                engine.insertText(str)
                onContentChanged()
            }
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        text?.toString()?.let { str ->
            if (str.isNotEmpty()) {
                engine.insertText(str)
                onContentChanged()
            }
        }
        return true
    }

    override fun finishComposingText(): Boolean {
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> {
                    engine.deleteBackspace()
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_ENTER -> {
                    engine.insertText("\n")
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    engine.insertText("    ")
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    engine.moveCursor("left")
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    engine.moveCursor("right")
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    engine.moveCursor("up")
                    onContentChanged()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    engine.moveCursor("down")
                    onContentChanged()
                    return true
                }
            }
        }
        return super.sendKeyEvent(event)
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        val et = ExtractedText()
        et.text = "\u200B"
        et.selectionStart = 1
        et.selectionEnd = 1
        et.startOffset = 0
        return et
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        return "\u200B"
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        return ""
    }
}
