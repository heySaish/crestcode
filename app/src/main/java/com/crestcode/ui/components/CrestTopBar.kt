package com.crestcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestTopBar(
    projectName: String = "Crest Project",
    onMenuClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRunClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CrestSurfaceHeader)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open File Explorer",
                tint = CrestTextPrimary
            )
        }

        Text(
            text = projectName,
            color = CrestTextActive,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )

        IconButton(onClick = onSaveClick) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save File",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRunClick) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run / Preview",
                tint = CrestAccentGreen
            )
        }

        IconButton(onClick = onUndoClick) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRedoClick) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = CrestTextPrimary
            )
        }
    }
}
