package com.crestcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestExtraKeysBar(
    row1Keys: List<String> = listOf("ESC", "⌨", "HOME", "↑", "END", "PGUP", "{", "}", "(", ")", "[", "]", ";", "=", ":", "\"", "'"),
    row2Keys: List<String> = listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN", "/", "|", "_", "-"),
    isCtrlActive: Boolean = false,
    isAltActive: Boolean = false,
    onToggleCtrl: () -> Unit = {},
    onToggleAlt: () -> Unit = {},
    onToggleKeyboard: () -> Unit = {},
    onKeyClick: (String) -> Unit = {}
) {
    val scrollState1 = rememberScrollState()
    val scrollState2 = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CrestSurfaceHeader)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState1)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            row1Keys.forEach { key ->
                val isActive = false
                CrestKeyButton(
                    label = key,
                    isActive = isActive,
                    onClick = {
                        if (key == "⌨") onToggleKeyboard() else onKeyClick(key)
                    }
                )
            }
        }

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState2)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            row2Keys.forEach { key ->
                val isActive = (key == "CTRL" && isCtrlActive) || (key == "ALT" && isAltActive)
                CrestKeyButton(
                    label = key,
                    isActive = isActive,
                    onClick = {
                        when (key) {
                            "CTRL" -> onToggleCtrl()
                            "ALT" -> onToggleAlt()
                            else -> onKeyClick(key)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CrestKeyButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) CrestAccentPrimary else CrestKeyBg
    val borderClr = if (isActive) CrestAccentPrimary else CrestKeyBorder
    val txtClr = if (isActive) Color.White else CrestTextActive

    Box(
        modifier = Modifier
            .height(34.dp)
            .widthIn(min = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderClr, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = txtClr,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
