package com.crestcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestStatusBar(
    branchName: String = "main",
    lineCol: String = "Ln 1, Col 1",
    language: String = "JavaScript",
    encoding: String = "UTF-8"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(CrestAccentPrimary)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountTree,
                contentDescription = null,
                tint = CrestTextActive,
                modifier = Modifier.size(13.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = branchName,
                color = CrestTextActive,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "0 ⊗ 0 ⚠️",
                color = CrestTextActive,
                fontSize = 11.sp
            )
        }

        // Right info
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lineCol,
                color = CrestTextActive,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = encoding,
                color = CrestTextActive,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = language,
                color = CrestTextActive,
                fontSize = 11.sp
            )
        }
    }
}
