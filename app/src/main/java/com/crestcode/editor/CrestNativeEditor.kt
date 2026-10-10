package com.crestcode.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crestcode.ui.theme.*

@Composable
fun CrestNativeEditor(
    engine: NativeEditorEngine,
    activeTabId: String,
    onContentChanged: () -> Unit,
    keyboardToggleTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val renderState = remember { mutableStateOf(engine.getRenderState()) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val context = LocalContext.current
    var inputViewInstance by remember { mutableStateOf<CrestInputView?>(null) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    fun refreshState() {
        renderState.value = engine.getRenderState()
        onContentChanged()
    }

    fun requestInputFocus() {
        inputViewInstance?.let { view ->
            view.requestFocus()
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } ?: run {
            keyboardController?.show()
        }
    }

    DisposableEffect(engine) {
        engine.onStateChanged = {
            renderState.value = engine.getRenderState()
        }
        onDispose {
            engine.onStateChanged = null
        }
    }

    LaunchedEffect(keyboardToggleTrigger) {
        if (keyboardToggleTrigger > 0) {
            requestInputFocus()
        }
    }

    // Automatically update render state when active tab changes
    LaunchedEffect(activeTabId) {
        renderState.value = engine.getRenderState()
        requestInputFocus()
    }

    val state = renderState.value
    val highlightSpans = remember(state.lines, state.lineCount) {
        engine.getHighlightSpans(0, state.lineCount)
    }

    val selAnchor = state.selection.anchor
    val selHead = state.selection.head

    val selStart = if (selAnchor.line < selHead.line || (selAnchor.line == selHead.line && selAnchor.character <= selHead.character)) {
        selAnchor
    } else {
        selHead
    }

    val selEnd = if (selAnchor.line < selHead.line || (selAnchor.line == selHead.line && selAnchor.character <= selHead.character)) {
        selHead
    } else {
        selAnchor
    }

    val isSelectionActive = (selStart.line != selEnd.line || selStart.character != selEnd.character)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CrestBackground)
            .pointerInput(Unit) {
                detectTapGestures {
                    requestInputFocus()
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                    if (isCtrlOrCmd) {
                        when (keyEvent.key) {
                            Key.C -> {
                                val selText = engine.getSelectedText()
                                if (selText.isNotEmpty()) {
                                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                                }
                                true
                            }
                            Key.V -> {
                                val clip = clipboardManager?.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasteText.isNotEmpty()) {
                                        engine.insertText(pasteText)
                                        refreshState()
                                    }
                                }
                                true
                            }
                            Key.X -> {
                                val selText = engine.getSelectedText()
                                if (selText.isNotEmpty()) {
                                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                                    engine.deleteSelection()
                                    refreshState()
                                }
                                true
                            }
                            Key.A -> {
                                engine.selectAll()
                                refreshState()
                                true
                            }
                            else -> false
                        }
                    } else {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                engine.moveCursor("left")
                                refreshState()
                                true
                            }
                            Key.DirectionRight -> {
                                engine.moveCursor("right")
                                refreshState()
                                true
                            }
                            Key.DirectionUp -> {
                                engine.moveCursor("up")
                                refreshState()
                                true
                            }
                            Key.DirectionDown -> {
                                engine.moveCursor("down")
                                refreshState()
                                true
                            }
                            Key.Enter -> {
                                engine.insertText("\n")
                                refreshState()
                                true
                            }
                            Key.Tab -> {
                                engine.insertText("    ")
                                refreshState()
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
            .focusable()
    ) {
        val minEditorWidth = maxWidth

        // Custom Android InputView hosting raw Zed-style InputConnection
        AndroidView(
            factory = { ctx ->
                CrestInputView(ctx, engine, onContentChanged = { refreshState() }).also { view ->
                    inputViewInstance = view
                    view.requestFocus()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            },
            update = { view ->
                inputViewInstance = view
            },
            modifier = Modifier.size(1.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
        ) {
            // Line Numbers Column (clean transparent background, no box highlight)
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                for (i in 1..state.lineCount.coerceAtLeast(1)) {
                    val lineIdx = i - 1
                    Text(
                        text = "$i",
                        color = if (lineIdx == state.cursor.line) CrestAccentPrimary else CrestTextSecondary.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .height(20.dp)
                            .pointerInput(lineIdx) {
                                detectTapGestures {
                                    val targetLineText = state.lines.getOrNull(lineIdx) ?: ""
                                    val targetChar = state.cursor.character.coerceIn(0, targetLineText.length)
                                    engine.setCursor(lineIdx, targetChar)
                                    refreshState()
                                    requestInputFocus()
                                }
                            }
                    )
                }
            }

            // Code Text Content Area (full line width hitbox support)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .widthIn(min = minEditorWidth)
            ) {
                for (lineIdx in 0 until state.lineCount.coerceAtLeast(1)) {
                    val lineText = state.lines.getOrNull(lineIdx) ?: ""
                    val spans = highlightSpans[lineIdx] ?: emptyList()
                    val isCurrentLine = (lineIdx == state.cursor.line)

                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val cursorCol = if (isCurrentLine) state.cursor.character.coerceIn(0, lineText.length) else 0

                    val cursorOffsetPx = remember(textLayoutResult, cursorCol, isCurrentLine) {
                        if (isCurrentLine && textLayoutResult != null) {
                            try {
                                textLayoutResult?.getCursorRect(cursorCol)?.left ?: 0f
                            } catch (t: Throwable) {
                                0f
                            }
                        } else 0f
                    }
                    val cursorOffsetDp = with(density) { cursorOffsetPx.toDp() }

                    // Bracket pair positions on this line
                    val bracketsOnThisLine = remember(state.matchingBrackets, lineIdx, lineText) {
                        val list = mutableListOf<Int>()
                        state.matchingBrackets?.let { (openPos, closePos) ->
                            if (openPos.line == lineIdx && openPos.character < lineText.length) list.add(openPos.character)
                            if (closePos.line == lineIdx && closePos.character < lineText.length) list.add(closePos.character)
                        }
                        list
                    }

                    // Selection bounds on this line
                    val isLineInSelection = isSelectionActive && (lineIdx in selStart.line..selEnd.line)
                    val selColStart = if (!isLineInSelection) 0 else if (lineIdx == selStart.line) selStart.character.coerceIn(0, lineText.length) else 0
                    val selColEnd = if (!isLineInSelection) 0 else if (lineIdx == selEnd.line) selEnd.character.coerceIn(0, lineText.length) else lineText.length

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minWidth = 2000.dp)
                            .height(20.dp)
                            .background(
                                if (isCurrentLine) CrestSurfaceHeader.copy(alpha = 0.4f) else Color.Transparent
                            )
                            .pointerInput(lineIdx, lineText) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val layout = textLayoutResult
                                        val colIdx = if (layout == null) {
                                            val fontWidthPx = 13.sp.toPx() * 0.6f
                                            if (fontWidthPx > 0) (offset.x / fontWidthPx).toInt().coerceIn(0, lineText.length) else lineText.length
                                        } else {
                                            if (offset.x >= layout.size.width.toFloat()) {
                                                lineText.length
                                            } else if (offset.x <= 0f) {
                                                0
                                            } else {
                                                layout.getOffsetForPosition(offset).coerceIn(0, lineText.length)
                                            }
                                        }
                                        engine.setCursor(lineIdx, colIdx)
                                        refreshState()
                                        requestInputFocus()
                                    },
                                    onLongPress = { offset ->
                                        val layout = textLayoutResult
                                        val colIdx = if (layout == null) {
                                            val fontWidthPx = 13.sp.toPx() * 0.6f
                                            if (fontWidthPx > 0) (offset.x / fontWidthPx).toInt().coerceIn(0, lineText.length) else lineText.length
                                        } else {
                                            if (offset.x >= layout.size.width.toFloat()) {
                                                lineText.length
                                            } else if (offset.x <= 0f) {
                                                0
                                            } else {
                                                layout.getOffsetForPosition(offset).coerceIn(0, lineText.length)
                                            }
                                        }
                                        engine.selectWordAt(lineIdx, colIdx)
                                        refreshState()
                                        requestInputFocus()
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 1. Text Selection Highlight Background
                        if (isLineInSelection && selColStart < selColEnd) {
                            val fontWidthPx = with(density) { 13.sp.toPx() * 0.6f }
                            val startPx = try { textLayoutResult?.getCursorRect(selColStart)?.left ?: (selColStart * fontWidthPx) } catch (t: Throwable) { selColStart * fontWidthPx }
                            val endPx = try { textLayoutResult?.getCursorRect(selColEnd)?.left ?: (selColEnd * fontWidthPx) } catch (t: Throwable) { selColEnd * fontWidthPx }
                            val startDp = with(density) { startPx.toDp() }
                            val widthDp = with(density) { (endPx - startPx).coerceAtLeast(6f).toDp() }

                            Box(
                                modifier = Modifier
                                    .offset(x = startDp)
                                    .width(widthDp)
                                    .height(20.dp)
                                    .background(CrestAccentPrimary.copy(alpha = 0.35f))
                            )
                        }

                        val annotatedText = buildAnnotatedString {
                            if (spans.isEmpty()) {
                                append(lineText)
                            } else {
                                for (span in spans) {
                                    val start = span.colStart.coerceAtMost(lineText.length)
                                    val end = span.colEnd.coerceAtMost(lineText.length)
                                    if (start < end) {
                                        val style = getStyleForToken(span.tokenType)
                                        pushStyle(style)
                                        append(lineText.substring(start, end))
                                        pop()
                                    }
                                }
                            }
                        }

                        // 2. Matching bracket highlights (hitbox box style)
                        for (bChar in bracketsOnThisLine) {
                            val bracketOffsetPx = remember(textLayoutResult, bChar) {
                                if (textLayoutResult != null && bChar < lineText.length) {
                                    try {
                                        textLayoutResult?.getCursorRect(bChar)?.left ?: 0f
                                    } catch (t: Throwable) { 0f }
                                } else 0f
                            }
                            val fontWidthPx = with(density) { 13.sp.toPx() * 0.6f }
                            val charWidthDp = with(density) {
                                try {
                                    (textLayoutResult?.getCursorRect(bChar)?.width ?: fontWidthPx).toDp()
                                } catch (t: Throwable) { fontWidthPx.toDp() }
                            }
                            val bracketOffsetDp = with(density) { bracketOffsetPx.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset(x = bracketOffsetDp)
                                    .width(charWidthDp.coerceAtLeast(8.dp))
                                    .height(18.dp)
                                    .background(
                                        color = CrestAccentPrimary.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = CrestAccentPrimary,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }

                        Text(
                            text = annotatedText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CrestTextActive,
                            onTextLayout = { textLayoutResult = it }
                        )

                        // 3. Render selection start/end teardrop water-drop handles with drag gestures
                        if (isSelectionActive) {
                            val fontWidthPx = with(density) { 13.sp.toPx() * 0.6f }
                            if (lineIdx == selStart.line) {
                                val hStartPx = try { textLayoutResult?.getCursorRect(selStart.character)?.left ?: (selStart.character * fontWidthPx) } catch (t: Throwable) { selStart.character * fontWidthPx }
                                val hStartDp = with(density) { hStartPx.toDp() }
                                var startAccumulatedX by remember { mutableStateOf(0f) }

                                // Vertical cursor line at selection start
                                Box(
                                    modifier = Modifier
                                        .offset(x = hStartDp)
                                        .width(2.dp)
                                        .height(18.dp)
                                        .background(Color.White)
                                )
                                // Left teardrop handle (curves down & left) with touch drag detection
                                Box(
                                    modifier = Modifier
                                        .offset(x = hStartDp - 20.dp, y = 18.dp)
                                        .size(28.dp)
                                        .pointerInput(lineIdx, selStart, selEnd) {
                                            detectDragGestures(
                                                onDragStart = { startAccumulatedX = 0f },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    startAccumulatedX += dragAmount.x
                                                    if (fontWidthPx > 0 && kotlin.math.abs(startAccumulatedX) >= fontWidthPx) {
                                                        val charsMoved = (startAccumulatedX / fontWidthPx).toInt()
                                                        startAccumulatedX -= charsMoved * fontWidthPx
                                                        val newChar = (selStart.character + charsMoved).coerceIn(0, lineText.length)
                                                        if (newChar != selStart.character && newChar <= selEnd.character) {
                                                            engine.setSelection(lineIdx, newChar, selEnd.line, selEnd.character)
                                                            refreshState()
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = CrestAccentPrimary,
                                                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp, topEnd = 0.dp)
                                            )
                                    )
                                }
                            }
                            if (lineIdx == selEnd.line) {
                                val hEndPx = try { textLayoutResult?.getCursorRect(selEnd.character)?.left ?: (selEnd.character * fontWidthPx) } catch (t: Throwable) { selEnd.character * fontWidthPx }
                                val hEndDp = with(density) { hEndPx.toDp() }
                                var endAccumulatedX by remember { mutableStateOf(0f) }

                                // Vertical cursor line at selection end
                                Box(
                                    modifier = Modifier
                                        .offset(x = hEndDp)
                                        .width(2.dp)
                                        .height(18.dp)
                                        .background(Color.White)
                                )
                                // Right teardrop handle (curves down & right) with touch drag detection
                                Box(
                                    modifier = Modifier
                                        .offset(x = hEndDp, y = 18.dp)
                                        .size(28.dp)
                                        .pointerInput(lineIdx, selStart, selEnd) {
                                            detectDragGestures(
                                                onDragStart = { endAccumulatedX = 0f },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    endAccumulatedX += dragAmount.x
                                                    if (fontWidthPx > 0 && kotlin.math.abs(endAccumulatedX) >= fontWidthPx) {
                                                        val charsMoved = (endAccumulatedX / fontWidthPx).toInt()
                                                        endAccumulatedX -= charsMoved * fontWidthPx
                                                        val newChar = (selEnd.character + charsMoved).coerceIn(0, lineText.length)
                                                        if (newChar != selEnd.character && newChar >= selStart.character) {
                                                            engine.setSelection(selStart.line, selStart.character, lineIdx, newChar)
                                                            refreshState()
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(
                                                color = CrestAccentPrimary,
                                                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp, topEnd = 16.dp)
                                            )
                                    )
                                }
                            }
                        }

                        // 4. Render cursor indicator precisely at cursor offset
                        if (isCurrentLine && !isSelectionActive) {
                            Box(
                                modifier = Modifier
                                    .offset(x = cursorOffsetDp)
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(CrestAccentPrimary)
                            )
                        }
                    }
                }

                // Blank space below code lines - tapping sets cursor to last line end
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                val lastLineIdx = (state.lineCount - 1).coerceAtLeast(0)
                                val lastLineText = state.lines.getOrNull(lastLineIdx) ?: ""
                                engine.setCursor(lastLineIdx, lastLineText.length)
                                refreshState()
                                requestInputFocus()
                            }
                        }
                )
            }
        }

        // Floating Context Menu Toolbar (Copy, Cut, Paste, Select All - Clean Text, No Emojis)
        if (isSelectionActive) {
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(24.dp),
                color = CrestSurfaceHeader,
                shadowElevation = 8.dp,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CrestAccentPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val selText = engine.getSelectedText()
                            if (selText.isNotEmpty()) {
                                clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                            }
                        }
                    ) {
                        Text("Copy", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            val selText = engine.getSelectedText()
                            if (selText.isNotEmpty()) {
                                clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                                engine.deleteSelection()
                                refreshState()
                            }
                        }
                    ) {
                        Text("Cut", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            val clip = clipboardManager?.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                if (pasteText.isNotEmpty()) {
                                    engine.insertText(pasteText)
                                    refreshState()
                                }
                            }
                        }
                    ) {
                        Text("Paste", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            engine.selectAll()
                            refreshState()
                        }
                    ) {
                        Text("Select All", color = CrestAccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun getStyleForToken(tokenType: String): SpanStyle {
    return when (tokenType) {
        "Keyword" -> SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.Bold)
        "Type" -> SpanStyle(color = Color(0xFFE5C07B))
        "String" -> SpanStyle(color = Color(0xFF98C379))
        "Number" -> SpanStyle(color = Color(0xFFD19A66))
        "Comment" -> SpanStyle(color = Color(0xFF5C6370))
        "Operator" -> SpanStyle(color = Color(0xFF56B6C2))
        else -> SpanStyle(color = Color(0xFFABB2BF))
    }
}
