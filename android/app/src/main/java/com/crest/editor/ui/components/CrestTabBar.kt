package com.crest.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crest.editor.ui.theme.*

data class CrestTabItem(
    val id: String,
    val title: String,
    val isModified: Boolean = false
)

@Composable
fun CrestTabBar(
    tabs: List<CrestTabItem> = listOf(
        CrestTabItem("1", "index.html", true),
        CrestTabItem("2", "editor.js", false),
        CrestTabItem("3", "styles.css", false)
    ),
    activeTabId: String = "1",
    onTabSelect: (String) -> Unit = {},
    onTabClose: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(CrestSurfaceHeader)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            val bgColor = if (isActive) CrestTabActiveBg else CrestTabInactiveBg
            val textColor = if (isActive) CrestTextActive else CrestTextSecondary

            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(bgColor)
                    .clickable { onTabSelect(tab.id) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = if (isActive) CrestAccentPrimary else CrestTextSecondary,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = tab.title,
                    color = textColor,
                    fontSize = 13.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (tab.isModified) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CrestAccentYellow)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { onTabClose(tab.id) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        tint = CrestTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(1.dp))
        }
    }
}
