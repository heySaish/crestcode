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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestExtraKeysBar(
    keys: List<String> = listOf(
        "Tab", "{", "}", "(", ")", "[", "]", ";", "=", ":", "\"", "'", "<", ">", "/", "|", "_", "-", "←", "→", "↑", "↓"
    ),
    onKeyClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(CrestSurfaceHeader)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(min = 34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CrestKeyBg)
                    .border(1.dp, CrestKeyBorder, RoundedCornerShape(6.dp))
                    .clickable { onKeyClick(key) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = key,
                    color = CrestTextActive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}
