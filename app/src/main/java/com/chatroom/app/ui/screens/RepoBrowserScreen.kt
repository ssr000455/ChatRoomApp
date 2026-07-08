package com.chatroom.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatroom.app.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.ui.viewinterop.AndroidView

enum class RepoViewMode { LOCAL, ONLINE }

data class RepoFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0
)

@Composable
fun RepoBrowserScreen(
    activeSession: Session?,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repoName = activeSession?.repoName ?: ""
    val repoUrl = activeSession?.repoUrl ?: ""
    val repoToken = activeSession?.repoToken ?: ""
    val localPath = activeSession?.localPath ?: ""

    // Clone path: /storage/emulated/0/ChatRoom/编程会话-n/repoName
    val repoDir = if (localPath.isNotBlank()) {
        File(localPath, repoName)
    } else {
        context.filesDir.resolve("repos").resolve(repoName)
    }

    // Build authenticated URL for git clone
    fun getAuthUrl(): String {
        if (repoToken.isBlank()) return repoUrl
        return try {
            val uri = java.net.URI(repoUrl)
            java.net.URI(
                uri.scheme,
                "oauth2:${repoToken}",
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                uri.fragment
            ).toString()
        } catch (e: Exception) {
            repoUrl
        }
    }

    // Clone state
    var isCloning by remember { mutableStateOf(false) }
    var cloneProgress by remember { mutableStateOf("") }
    var cloneError by remember { mutableStateOf<String?>(null) }
    var isCloned by remember { mutableStateOf(false) }
    var cloneProcess by remember { mutableStateOf<Process?>(null) }

    // View mode toggle
    var viewMode by remember { mutableStateOf(RepoViewMode.LOCAL) }

    // File browsing state
    var currentDir by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<RepoFile>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }

    // Check if already cloned
    fun checkCloned() {
        if (repoDir.exists() && repoDir.listFiles()?.isNotEmpty() == true) {
            isCloned = true
            currentDir = repoDir
            refreshFiles(repoDir) { files = it }
        }
    }

    // Refresh file list
    fun refreshFiles(dir: File, onResult: (List<RepoFile>) -> Unit) {
        val list = dir.listFiles()?.map { file ->
            RepoFile(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
        onResult(list)
    }

    // Cancel clone
    fun cancelClone() {
        cloneProcess?.destroy()
        cloneProcess = null
        isCloning = false
        cloneProgress = "克隆已取消"
        cloneError = null
    }

    // Start clone (cancelable)
    fun startClone() {
        if (repoUrl.isBlank()) return
        val authUrl = getAuthUrl()
        scope.launch {
            isCloning = true
            cloneProgress = "正在克隆 $repoUrl ..."
            cloneError = null

            try {
                repoDir.parentFile?.mkdirs()
                // Clean existing if any
                if (repoDir.exists()) {
                    repoDir.deleteRecursively()
                }

                val result = withContext(Dispatchers.IO) {
                    val proc = ProcessBuilder()
                        .command("git", "clone", "--depth", "1", authUrl, repoDir.absolutePath)
                        .redirectErrorStream(true)
                        .start()

                    cloneProcess = proc

                    // Read output in chunks for real-time progress
                    val reader = proc.inputStream.bufferedReader()
                    val output = StringBuilder()
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        output.appendLine(line)
                        // Update progress for long clones
                        if (line.isNotBlank()) {
                            cloneProgress = line
                        }
                    }
                    val exitCode = proc.waitFor()
                    cloneProcess = null
                    Pair(exitCode, output.toString())
                }

                if (result.first == 0) {
                    cloneProgress = "克隆完成"
                    isCloned = true
                    currentDir = repoDir
                    refreshFiles(repoDir) { files = it }
                } else {
                    cloneError = "克隆失败:\n${result.second}"
                    cloneProgress = ""
                }
            } catch (e: Exception) {
                // Check if this was a cancellation
                if (cloneProcess == null && !isCloning) {
                    // Canceled, don't show error
                } else {
                    cloneError = "克隆失败: ${e.message}"
                    cloneProgress = ""
                }
            } finally {
                isCloning = false
                cloneProcess = null
            }
        }
    }

    // Auto-start clone and check on first composition
    LaunchedEffect(repoName, localPath) {
        checkCloned()
        if (!isCloned && repoUrl.isNotBlank() && !isCloning && repoDir.parentFile?.exists() != false) {
            startClone()
        }
    }

    // File viewer
    if (selectedFile != null && fileContent != null) {
        FileViewerScreen(
            fileName = selectedFile!!.name,
            content = fileContent!!,
            onBack = {
                selectedFile = null
                fileContent = null
            }
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 48.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSidebar,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (repoName.isNotBlank()) repoName else "仓库浏览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Refresh button (only when cloned)
            if (isCloned) {
                IconButton(
                    onClick = {
                        currentDir?.let { dir -> refreshFiles(dir) { files = it } }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Directory path breadcrumb
        if (isCloned && currentDir != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentDir == repoDir) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentDir!!.absolutePath.removePrefix(repoDir.parentFile?.absolutePath ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // View mode toggle (local files vs online web)
        if (repoUrl.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "查看方式：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    RepoViewMode.entries.forEach { mode ->
                        val selected = viewMode == mode
                        Text(
                            text = if (mode == RepoViewMode.LOCAL) "本地" else "在线",
                            modifier = Modifier
                                .clickable { viewMode = mode }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Loading / progress with cancel button
        if (isCloning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cloneProgress.ifBlank { "克隆中..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelClone() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("取消克隆")
                }
            }
        }

        // Clone error
        if (cloneError != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = cloneError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { startClone() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("重试")
                    }
                }
            }
        }

        // Content
        if (viewMode == RepoViewMode.ONLINE && repoUrl.isNotBlank()) {
            // Online web view
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        loadUrl(repoUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                // Not cloned yet - show clone button
                !isCloned && !isCloning && cloneError == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "仓库尚未克隆到本地",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (repoUrl.isNotBlank()) repoUrl else "暂无仓库地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { startClone() },
                            enabled = repoUrl.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "克隆仓库",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (localPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "保存至: $localPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Cloned - show file browser
                isCloned -> {
                    if (files.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "仓库为空",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (currentDir != repoDir) {
                                item {
                                    FileItem(
                                        name = "..",
                                        isDirectory = true,
                                        onClick = {
                                            currentDir = currentDir?.parentFile
                                            currentDir?.let { refreshFiles(it) { files = it } }
                                        }
                                    )
                                }
                            }

                            items(files) { file ->
                                FileItem(
                                    name = file.name,
                                    isDirectory = file.isDirectory,
                                    size = file.size,
                                    onClick = {
                                        val f = File(file.path)
                                        if (f.isDirectory) {
                                            currentDir = f
                                            refreshFiles(f) { files = it }
                                        } else if (f.isFile && f.length() < 5 * 1024 * 1024) {
                                            scope.launch {
                                                val content = withContext(Dispatchers.IO) {
                                                    try {
                                                        f.readText(Charsets.UTF_8)
                                                    } catch (e: Exception) {
                                                        "无法读取文件: ${e.message}"
                                                    }
                                                }
                                                selectedFile = f
                                                fileContent = content
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    name: String,
    isDirectory: Boolean,
    size: Long = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (isDirectory) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isDirectory) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!isDirectory && size > 0) {
            Text(
                text = formatFileSize(size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FileViewerScreen(
    fileName: String,
    content: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 48.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val lines = content.lines()
            items(lines.size) { index ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "${index + 1}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = lines[index],
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
