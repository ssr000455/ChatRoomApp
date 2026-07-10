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
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.ui.viewinterop.AndroidView
import com.chatroom.app.R
import com.chatroom.app.data.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    var gitAvailable by remember { mutableStateOf(true) }

    // Permission launcher for Android 11+ MANAGE_EXTERNAL_STORAGE
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    // View mode toggle
    var viewMode by remember { mutableStateOf(RepoViewMode.LOCAL) }

    // File browsing state
    var currentDir by remember { mutableStateOf<File?>(null) }
    var files by remember { mutableStateOf<List<RepoFile>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }

    // Clone speed tracking
    var cloneSpeed by remember { mutableStateOf("") }
    var clonePercentage by remember { mutableFloatStateOf(0f) }

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

    // Check if already cloned — verify by looking for .git directory (the git repo marker)
    // Supports two layouts:
    //   1) 编程会话-n/repoName/.git  (auto-clone layout)
    //   2) 编程会话-n/.git           (manual clone directly in session dir)
    fun checkCloned(): Boolean {
        // Try the standard path first: 编程会话-n/repoName/
        val gitDir = File(repoDir, ".git")
        if (gitDir.isDirectory()) {
            if (!isCloned) {
                isCloned = true
                currentDir = repoDir
                refreshFiles(repoDir) { files = it }
            }
            return true
        }
        // If repoDir is a subdirectory of localPath and user cloned directly into localPath,
        // check the parent: 编程会话-n/.git
        if (localPath.isNotBlank() && repoName.isNotBlank()) {
            val parentDir = File(localPath)
            if (parentDir != repoDir && File(parentDir, ".git").isDirectory()) {
                if (!isCloned) {
                    isCloned = true
                    currentDir = parentDir
                    refreshFiles(parentDir) { files = it }
                }
                return true
            }
        }
        return false
    }

    // Periodic polling: detect manual clone / external file changes
    LaunchedEffect(repoName, localPath) {
        while (true) {
            if (!isCloning && !isCloned && repoDir.exists()) {
                checkCloned()
            }
            delay(3000) // check every 3 seconds
        }
    }

    // Cancel clone
    fun cancelClone() {
        cloneProcess?.destroy()
        cloneProcess = null
        isCloning = false
        cloneProgress = "克隆已取消"
        cloneError = null
        cloneSpeed = ""
        clonePercentage = 0f
    }

    // Start clone (cancelable)
    fun startClone() {
        if (repoUrl.isBlank()) return
        cloneSpeed = ""
        clonePercentage = 0f

        // Check git availability
        if (!gitAvailable) {
            cloneError = "Git not found.\n\n请下载 git 静态二进制文件放到以下目录:\n${context.filesDir.resolve("tools/bin").absolutePath}\n\n或使用「在线」模式浏览仓库。"
            return
        }

        val authUrl = getAuthUrl()

        // Use internal storage to avoid permission issues on Android 10+
        val targetDir = if (localPath.isNotBlank()) {
            // External path - might need permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    cloneError = "需要存储权限才能在外部存储中克隆。\n请在设置中授予「管理所有文件」权限。\n将打开设置页面，请允许后重试。"
                    // Open settings for MANAGE_EXTERNAL_STORAGE
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        storagePermissionLauncher.launch(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            storagePermissionLauncher.launch(intent)
                        } catch (_: Exception) {}
                    }
                    return
                }
            }
            File(localPath, repoName)
        } else {
            context.filesDir.resolve("repos").resolve(repoName)
        }

        scope.launch {
            isCloning = true
            cloneProgress = "正在克隆 $repoUrl ..."
            cloneError = null

            try {
                // Ensure parent dir exists with proper permissions
                targetDir.parentFile?.let { parent ->
                    parent.mkdirs()
                    // Try to set executable permission on directories
                    try {
                        parent.setExecutable(true, false)
                    } catch (_: Exception) {}
                }

                // Clean existing if any
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }

                val result = withContext(Dispatchers.IO) {
                    val pb = ProcessBuilder()
                        .command("git", "clone", "--progress", "--depth", "1", authUrl, targetDir.absolutePath)
                        .redirectErrorStream(true)
                    // Add app's tools bin directory to PATH for bundled git
                    val toolsBin = context.filesDir.resolve("tools/bin")
                    if (toolsBin.isDirectory) {
                        val env = pb.environment()
                        val oldPath = env["PATH"] ?: "/system/bin:/system/xbin"
                        env["PATH"] = "${toolsBin.absolutePath}:$oldPath"
                    }
                    val proc = pb.start()

                    cloneProcess = proc

                    // Read output in chunks for real-time progress
                    val reader = proc.inputStream.bufferedReader()
                    val output = StringBuilder()
                    // Use a timeout: if no output for 30s, consider it stuck
                    val startTime = System.currentTimeMillis()
                    val timeout = 60000L
                    // Regexes to parse git clone progress and speed
                    val speedRegex = Regex("""\|\s*([\d.]+\s+\w+/s)""")
                    val pctRegex = Regex("""Receiving objects:\s+(\d+)%""")
                    while (isActive) {
                        if (reader.ready()) {
                            val line = reader.readLine()
                            if (line == null) break
                            output.appendLine(line)
                            if (line.isNotBlank()) {
                                cloneProgress = line
                                // Parse download speed
                                val speedMatch = speedRegex.find(line)
                                if (speedMatch != null) {
                                    cloneSpeed = speedMatch.groupValues[1]
                                }
                                // Parse progress percentage
                                val pctMatch = pctRegex.find(line)
                                if (pctMatch != null) {
                                    clonePercentage = pctMatch.groupValues[1].toFloatOrNull()?.div(100f) ?: 0f
                                }
                            }
                        } else {
                            // Check timeout
                            if (System.currentTimeMillis() - startTime > timeout) {
                                proc.destroy()
                                output.appendLine("\n[克隆超时 - 60秒无响应]")
                                break
                            }
                            // Small delay to avoid busy-waiting
                            delay(100)
                        }
                    }
                    val exitCode = proc.waitFor()
                    cloneProcess = null
                    Pair(exitCode, output.toString())
                }

                if (result.first == 0) {
                    cloneProgress = "克隆完成"
                    isCloned = true
                    currentDir = targetDir
                    refreshFiles(targetDir) { files = it }
                } else {
                    cloneError = "克隆失败:\n${result.second}"
                    cloneProgress = ""
                }
            } catch (e: Exception) {
                // Check if this was a cancellation
                if (cloneProcess == null && !isCloning) {
                    // Canceled, don't show error
                } else {
                    val msg = e.message ?: "未知错误"
                    cloneError = when {
                        msg.contains("Permission denied") || msg.contains("权限") ->
                            "克隆失败: 权限被拒绝\n\n请尝试:\n1. 使用内部存储路径（不设置本地路径）\n2. 确保存储权限已授予"
                        msg.contains("No such file") || msg.contains("Cannot run") ->
                            "克隆失败: git命令未找到\n\n请将 git 静态二进制文件放到:\n${context.filesDir.resolve("tools/bin").absolutePath}"
                        else -> "克隆失败: $msg"
                    }
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
        if (!isCloned && repoUrl.isNotBlank() && !isCloning && gitAvailable) {
            // Use the same internal storage path logic
            val autoTargetDir = if (localPath.isNotBlank()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    null // Skip auto-clone if no permission for external storage
                } else File(localPath, repoName)
            } else {
                context.filesDir.resolve("repos").resolve(repoName)
            }
            if (autoTargetDir != null && autoTargetDir.parentFile?.exists() != false) {
                startClone()
            }
        }
    }

    // File viewer
    if (selectedFile != null && fileContent != null) {
        FileViewerScreen(
            fileName = selectedFile?.name ?: "",
            content = fileContent ?: "",
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
                    contentDescription = stringResource(R.string.content_desc_menu),
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
                        contentDescription = stringResource(R.string.repo_refresh),
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
                    text = (currentDir ?: repoDir).absolutePath.removePrefix(repoDir.parentFile?.absolutePath ?: ""),
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // WiFi icon + title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "正在克隆仓库",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = repoUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress text + speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cloneProgress.ifBlank { "连接中..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (cloneSpeed.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cloneSpeed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cancel button
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
                        Text(stringResource(R.string.repo_cancel_clone))
                    }
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
                        text = cloneError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { startClone() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.repo_retry))
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

                        // Show git not available warning
                        if (!gitAvailable) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.repo_git_not_available),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "未找到 git 命令。将静态 git 二进制文件放到 tools/bin 目录，或使用「在线」模式。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { startClone() },
                            enabled = repoUrl.isNotBlank() && gitAvailable,
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

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { checkCloned() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "检测本地仓库",
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
                    contentDescription = stringResource(R.string.wizard_back),
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
