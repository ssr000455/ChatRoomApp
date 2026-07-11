package com.chatroom.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatroom.app.R

enum class DevToolType(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val descRes: Int) {
    CODE_SEARCH(R.string.dev_tool_search_code, Icons.Default.Search, R.string.dev_tool_search_code_desc),
    CODE_EXPLAIN(R.string.dev_tool_explain_code, Icons.Default.Construction, R.string.dev_tool_explain_code_desc),
    PERF_ANALYSIS(R.string.dev_tool_perf_analysis, Icons.Default.Speed, R.string.dev_tool_perf_analysis_desc),
    SECURITY_AUDIT(R.string.dev_tool_security_audit, Icons.Default.Lock, R.string.dev_tool_security_audit_desc),
    API_DEBUG(R.string.dev_tool_api_debug, Icons.Default.Api, R.string.dev_tool_api_debug_desc)
}

data class DevToolConfig(
    val selectedTool: DevToolType = DevToolType.CODE_SEARCH,
    val searchQuery: String = "",
    val searchGlob: String = "",
    val codeInput: String = "",
    val fileName: String = "",
    val apiUrl: String = "",
    val apiMethod: String = "GET",
    val apiRequestBody: String = "",
    val apiResponseBody: String = "",
    val apiResponseStatus: String = "",
    val apiError: String = "",
    val isExpanded: Boolean = false
)

@Composable
fun DevToolPanel(
    config: DevToolConfig,
    onConfigChange: (DevToolConfig) -> Unit,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dev_tools_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (config.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (config.isExpanded) stringResource(R.string.hide) else stringResource(R.string.show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onConfigChange(config.copy(isExpanded = !config.isExpanded)) }
                    .padding(4.dp)
            )
        }

        AnimatedVisibility(
            visible = config.isExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))

                // Tool selector
                Text(
                    text = stringResource(R.string.dev_tools_select_tool),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Tools grid
                DevToolType.entries.forEach { tool ->
                    val isSelected = config.selectedTool == tool
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onConfigChange(config.copy(selectedTool = tool)) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = stringResource(tool.labelRes),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(tool.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(tool.descRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Tool-specific inputs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (config.selectedTool) {
                        DevToolType.CODE_SEARCH -> {
                            OutlinedTextField(
                                value = config.searchQuery,
                                onValueChange = { onConfigChange(config.copy(searchQuery = it)) },
                                label = { Text(stringResource(R.string.dev_tool_search_query)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_search_query_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.searchGlob,
                                onValueChange = { onConfigChange(config.copy(searchGlob = it)) },
                                label = { Text(stringResource(R.string.dev_tool_file_filter)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_file_filter_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        DevToolType.CODE_EXPLAIN -> {
                            OutlinedTextField(
                                value = config.fileName,
                                onValueChange = { onConfigChange(config.copy(fileName = it)) },
                                label = { Text(stringResource(R.string.dev_tool_file_path)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_file_path_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.codeInput,
                                onValueChange = { onConfigChange(config.copy(codeInput = it)) },
                                label = { Text(stringResource(R.string.dev_tool_code_input)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        DevToolType.PERF_ANALYSIS -> {
                            OutlinedTextField(
                                value = config.fileName,
                                onValueChange = { onConfigChange(config.copy(fileName = it)) },
                                label = { Text(stringResource(R.string.dev_tool_file_path)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_file_path_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.codeInput,
                                onValueChange = { onConfigChange(config.copy(codeInput = it)) },
                                label = { Text(stringResource(R.string.dev_tool_code_input)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        DevToolType.SECURITY_AUDIT -> {
                            OutlinedTextField(
                                value = config.fileName,
                                onValueChange = { onConfigChange(config.copy(fileName = it)) },
                                label = { Text(stringResource(R.string.dev_tool_file_path)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_file_path_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.codeInput,
                                onValueChange = { onConfigChange(config.copy(codeInput = it)) },
                                label = { Text(stringResource(R.string.dev_tool_code_input)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        DevToolType.API_DEBUG -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = config.apiMethod,
                                    onValueChange = { onConfigChange(config.copy(apiMethod = it)) },
                                    label = { Text(stringResource(R.string.dev_tool_method)) },
                                    placeholder = { Text("GET") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(80.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = config.apiUrl,
                                    onValueChange = { onConfigChange(config.copy(apiUrl = it)) },
                                    label = { Text(stringResource(R.string.dev_tool_url)) },
                                    placeholder = { Text("https://api.example.com/endpoint") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.apiResponseStatus,
                                onValueChange = { onConfigChange(config.copy(apiResponseStatus = it)) },
                                label = { Text(stringResource(R.string.dev_tool_response_status)) },
                                placeholder = { Text(stringResource(R.string.dev_tool_response_status_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.apiRequestBody,
                                onValueChange = { onConfigChange(config.copy(apiRequestBody = it)) },
                                label = { Text(stringResource(R.string.dev_tool_request_body)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.apiResponseBody,
                                onValueChange = { onConfigChange(config.copy(apiResponseBody = it)) },
                                label = { Text(stringResource(R.string.dev_tool_response_body)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = config.apiError,
                                onValueChange = { onConfigChange(config.copy(apiError = it)) },
                                label = { Text(stringResource(R.string.dev_tool_error_message)) },
                                shape = RoundedCornerShape(12.dp),
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Execute button
        OutlinedButton(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            enabled = config.isExpanded
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.dev_tools_execute, stringResource(config.selectedTool.labelRes)))
        }
    }
}

@Composable
fun DevToolResultCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
