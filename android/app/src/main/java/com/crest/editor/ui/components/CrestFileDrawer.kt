package com.crest.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crest.editor.ui.theme.*

data class FileTreeItem(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val level: Int = 0
)

@Composable
fun CrestFileDrawer(
    projectName: String = "Crest Project",
    fileList: List<FileTreeItem> = listOf(
        FileTreeItem("1", "src", true, 0),
        FileTreeItem("2", "web", true, 1),
        FileTreeItem("3", "index.html", false, 2),
        FileTreeItem("4", "editor.js", false, 2),
        FileTreeItem("5", "styles.css", false, 2),
        FileTreeItem("6", "android", true, 0),
        FileTreeItem("7", "package.json", false, 0),
        FileTreeItem("8", "README.md", false, 0)
    ),
    onFileSelect: (FileTreeItem) -> Unit = {},
    onCloseDrawer: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(CrestSurface)
    ) {
        // Workspace Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(CrestSurfaceHeader)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = CrestAccentPrimary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = projectName,
                color = CrestTextActive,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New File",
                    tint = CrestTextPrimary
                )
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = CrestTextPrimary
                )
            }
        }

        Divider(color = CrestBorder, thickness = 1.dp)

        // File Tree List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            items(fileList) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable { onFileSelect(item) }
                        .padding(start = (item.level * 16 + 12).dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (item.isDirectory) CrestAccentYellow else CrestAccentGreen,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = item.name,
                        color = CrestTextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Divider(color = CrestBorder, thickness = 1.dp)

        // Bottom Tools Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(CrestSurfaceHeader)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = CrestTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Terminal", color = CrestTextPrimary, fontSize = 12.sp)
            }

            TextButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = CrestTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Git", color = CrestTextPrimary, fontSize = 12.sp)
            }

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = CrestTextPrimary
                )
            }
        }
    }
}
