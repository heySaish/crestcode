package com.crestcode.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

private const val IME_SENTINEL_CHAR = "\u200B"
private const val IME_SENTINEL_COUNT = 200
private const val IME_INITIAL_CURSOR = 150
private val IME_SENTINEL_TEXT = IME_SENTINEL_CHAR.repeat(IME_SENTINEL_COUNT)

@Composable
fun CrestNativeEditor(
    engine: NativeEditorEngine,
    activeTabId: String,
    onContentChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renderState = remember { mutableStateOf(engine.getRenderState()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current

    var inputFieldValue by remember {
        mutableStateOf(TextFieldValue(IME_SENTINEL_TEXT, TextRange(IME_INITIAL_CURSOR)))
    }

    fun refreshState() {
        renderState.value = engine.getRenderState()
        onContentChanged()
    }

    // Automatically update render state when active tab changes
    LaunchedEffect(activeTabId) {
        renderState.value = engine.getRenderState()
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val state = renderState.value
    val highlightSpans = remember(state.lines, state.lineCount) {
        engine.getHighlightSpans(0, state.lineCount)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CrestBackground)
            .clickable {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
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
                } else false
            }
            .focusable()
    ) {
        val minEditorWidth = maxWidth

        // Hidden TextField to connect Android Soft Keyboard (IME)
        BasicTextField(
            value = inputFieldValue,
            onValueChange = { newValue ->
                val newText = newValue.text
                val currentCursor = inputFieldValue.selection.start
                val newCursor = newValue.selection.start

                if (newCursor < currentCursor) {
                    // Backspace pressed (supports holding delete key continuously)
                    val deletedCount = (currentCursor - newCursor).coerceAtLeast(1)
                    repeat(deletedCount) {
                        engine.deleteBackspace()
                    }
                    refreshState()

                    if (newCursor < 20) {
                        // Re-center sentinel buffer when running low to maintain continuous hold-to-delete
                        inputFieldValue = TextFieldValue(IME_SENTINEL_TEXT, TextRange(IME_INITIAL_CURSOR))
                    } else {
                        // Keep text buffer padded with sentinel string so Gboard sees exact expected cursor position
                        inputFieldValue = TextFieldValue(IME_SENTINEL_TEXT, newValue.selection)
                    }
                } else if (newCursor > currentCursor || newText.length > inputFieldValue.text.length) {
                    // Text typed / pasted
                    val added = newText.substring(currentCursor.coerceAtMost(newText.length)).replace(IME_SENTINEL_CHAR, "")
                    if (added.isNotEmpty()) {
                        engine.insertText(added)
                        refreshState()
                    }
                    inputFieldValue = TextFieldValue(IME_SENTINEL_TEXT, TextRange(IME_INITIAL_CURSOR))
                } else {
                    inputFieldValue = newValue
                }
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester),
            cursorBrush = SolidColor(Color.Transparent)
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
                            .clickable {
                                val targetLineText = state.lines.getOrNull(lineIdx) ?: ""
                                val targetChar = state.cursor.character.coerceIn(0, targetLineText.length)
                                engine.setCursor(lineIdx, targetChar)
                                refreshState()
                                focusRequester.requestFocus()
                                keyboardController?.show()
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minWidth = 2000.dp)
                            .height(20.dp)
                            .background(
                                if (isCurrentLine) CrestSurfaceHeader.copy(alpha = 0.4f) else Color.Transparent
                            )
                            .pointerInput(lineIdx, lineText) {
                                detectTapGestures { offset ->
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
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
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

                        Text(
                            text = annotatedText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CrestTextActive,
                            onTextLayout = { textLayoutResult = it }
                        )

                        // Render cursor indicator precisely at cursor offset
                        if (isCurrentLine) {
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
                        .clickable {
                            val lastLineIdx = (state.lineCount - 1).coerceAtLeast(0)
                            val lastLineText = state.lines.getOrNull(lastLineIdx) ?: ""
                            engine.setCursor(lastLineIdx, lastLineText.length)
                            refreshState()
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                )
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
