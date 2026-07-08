package com.chatroom.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiffView(
    diffContent: String,
    modifier: Modifier = Modifier
) {
    val lines = diffContent.split("\n")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        lines.forEach { line ->
            val backgroundColor = when {
                line.startsWith("+") && !line.startsWith("+++") ->
                    androidx.compose.ui.graphics.Color(0xFF1B3B1B)
                line.startsWith("-") && !line.startsWith("---") ->
                    androidx.compose.ui.graphics.Color(0xFF3B1B1B)
                line.startsWith("@@") ->
                    androidx.compose.ui.graphics.Color(0xFF1B2B4B)
                line.startsWith("diff --git") || line.startsWith("index") ||
                line.startsWith("---") || line.startsWith("+++") ->
                    androidx.compose.ui.graphics.Color(0xFF2B2B2B)
                else ->
                    androidx.compose.ui.graphics.Color.Transparent
            }

            val textColor = when {
                line.startsWith("+") && !line.startsWith("+++") ->
                    androidx.compose.ui.graphics.Color(0xFF6A9955)
                line.startsWith("-") && !line.startsWith("---") ->
                    androidx.compose.ui.graphics.Color(0xFFF44747)
                line.startsWith("@@") ->
                    androidx.compose.ui.graphics.Color(0xFF569CD6)
                line.startsWith("diff --git") ->
                    MaterialTheme.colorScheme.primary
                else ->
                    androidx.compose.ui.graphics.Color(0xFFD4D4D4)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.dp)
            ) {
                // Line number placeholder
                BasicText(
                    text = "",
                    modifier = Modifier.padding(horizontal = 0.dp)
                )

                BasicText(
                    text = if (line.length > 120) line.take(120) + "..." else line,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor
                    ),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                        .let { mod ->
                            if (backgroundColor != androidx.compose.ui.graphics.Color.Transparent) {
                                mod.then(
                                    Modifier.padding(vertical = 0.dp)
                                )
                            } else mod
                        }
                )
            }
        }
    }
}
