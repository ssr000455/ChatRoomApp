package com.chatroom.app.terminal

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ExtensionPackage(
    val name: String,            // binary name: "git"
    val displayName: String,     // "Git"
    val description: String,     // "版本控制工具"
    val binaryName: String,      // output filename: "git"
    val version: String,         // "2.45.2"
    val urls: Map<String, String>, // arch -> download URL
    val isZip: Boolean = false,  // whether to unzip
    val extractPath: String? = null, // path inside zip to extract (null=first binary)
)

data class ToolchainStatus(
    val hasBusybox: Boolean,
    val shellPath: String,
    val binDir: String
)

class ToolchainInstaller(private val baseDir: File) {

    val binDir: File get() = File(baseDir, "tools/bin")
    val toolsHome: File get() = File(baseDir, "tools/home")
    private val toolsDir: File get() = File(baseDir, "tools")

    private fun binFile(name: String): File = File(binDir, name)

    fun checkStatus(): ToolchainStatus {
        val bbInstalled = isBusyboxInstalled()
        return ToolchainStatus(
            hasBusybox = bbInstalled,
            shellPath = if (bbInstalled) binFile("sh").absolutePath else "sh",
            binDir = if (bbInstalled) binDir.absolutePath else ""
        )
    }

    companion object {
        private const val TAG = "ToolchainInstaller"
        private val BUSYBOX_APPLETS = listOf(
            "sh", "ash", "bash", "ls", "cp", "mv", "rm", "mkdir", "rmdir",
            "cat", "echo", "grep", "find", "sed", "awk", "head", "tail",
            "sort", "uniq", "wc", "cut", "tr", "od", "xxd",
            "chmod", "chown", "touch", "ln", "readlink", "stat",
            "ps", "kill", "pkill", "pgrep", "top", "nice", "renice",
            "tar", "gzip", "gunzip", "bzip2", "unzip",
            "diff", "patch", "cmp",
            "ping", "wget", "tftp", "ftpget", "ftpput",
            "df", "du", "mount", "umount",
            "date", "cal", "sleep", "time", "watch", "yes",
            "env", "printenv", "which", "xargs", "nohup",
            "expr", "test", "[", "true", "false",
            "basename", "dirname", "realpath", "mktemp",
            "seq", "shuf", "factor", "expand", "unexpand",
            "dmesg", "free", "uname", "uptime", "sync",
            "vi", "less", "more", "logger", "logread",
            "nslookup", "hostname", "dnsdomainname",
            "pidof", "fuser", "lsof",
            "id", "whoami", "who", "users", "logname",
            "clear", "reset", "stty", "tset",
            "crontab", "at", "timeout",
            "base64", "md5sum", "sha1sum", "sha256sum", "sha512sum",
            "cmp", "comm", "fold", "fmt", "pr",
        )

        /** All available extension packages */
        fun getAvailablePackages(): List<ExtensionPackage> = listOf(
            ExtensionPackage(
                name = "git", displayName = "Git",
                description = "分布式版本控制系统",
                binaryName = "git", version = "2.45.2",
                urls = mapOf(
                    "aarch64" to "https://github.com/termux/termux-packages/files/14964480/git.aarch64.zip",
                    "armv7l" to "https://github.com/termux/termux-packages/files/14964480/git.arm.zip",
                ),
                isZip = true,
            ),
            ExtensionPackage(
                name = "curl", displayName = "cURL",
                description = "网络数据传输工具",
                binaryName = "curl", version = "8.7.1",
                urls = mapOf(
                    "aarch64" to "https://github.com/termux/termux-packages/files/14964481/curl.aarch64.zip",
                    "armv7l" to "https://github.com/termux/termux-packages/files/14964481/curl.arm.zip",
                ),
                isZip = true,
            ),
            ExtensionPackage(
                name = "node", displayName = "Node.js",
                description = "JavaScript 运行时环境",
                binaryName = "node", version = "22.2.0",
                urls = mapOf(
                    "aarch64" to "https://github.com/termux/termux-packages/files/14964482/node.aarch64.zip",
                    "armv7l" to "https://github.com/termux/termux-packages/files/14964482/node.arm.zip",
                ),
                isZip = true,
            ),
            ExtensionPackage(
                name = "python", displayName = "Python",
                description = "Python 编程语言",
                binaryName = "python3", version = "3.12.3",
                urls = mapOf(
                    "aarch64" to "https://github.com/termux/termux-packages/files/14964483/python.aarch64.zip",
                    "armv7l" to "https://github.com/termux/termux-packages/files/14964483/python.arm.zip",
                ),
                isZip = true,
            ),
        )
    }

    fun isPackageInstalled(pkg: ExtensionPackage): Boolean {
        return binFile(pkg.binaryName).isFile && binFile(pkg.binaryName).canExecute()
    }

    fun isBusyboxInstalled(): Boolean {
        return binFile("busybox").isFile && binFile("busybox").canExecute()
    }

    fun archSuffix(): String = when (Build.CPU_ABI) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a", "armeabi" -> "armv7l"
        "x86_64" -> "x86_64"
        "x86" -> "i686"
        else -> "aarch64"
    }

    private suspend fun reportProgress(onProgress: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) { onProgress(message) }
    }

    suspend fun installBusybox(onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isBusyboxInstalled()) {
            reportProgress(onProgress, "BusyBox 已安装")
            return@withContext true
        }
        binDir.mkdirs()
        toolsHome.mkdirs()
        val arch = archSuffix()
        val url = "https://busybox.net/downloads/binaries/1.35.0/busybox-$arch"
        reportProgress(onProgress, "正在下载 BusyBox...")
        try {
            downloadFile(url, binFile("busybox"))
            binFile("busybox").setExecutable(true)
            createAppletSymlinks()
            Log.d(TAG, "BusyBox installed")
            reportProgress(onProgress, "BusyBox 安装完成")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "BusyBox download failed: ${e.message}")
            reportProgress(onProgress, "BusyBox 下载失败: ${e.message}")
            return@withContext false
        }
    }

    suspend fun installPackage(pkg: ExtensionPackage, onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isPackageInstalled(pkg)) {
            reportProgress(onProgress, "${pkg.displayName} 已安装")
            return@withContext true
        }
        binDir.mkdirs()
        toolsHome.mkdirs()

        val arch = archSuffix()
        val url = pkg.urls[arch] ?: run {
            reportProgress(onProgress, "${pkg.displayName}: 不支持当前架构 ($arch)")
            return@withContext false
        }

        reportProgress(onProgress, "正在下载 ${pkg.displayName} ${pkg.version}...")
        try {
            if (pkg.isZip) {
                val zipFile = File(toolsDir, "${pkg.name}.zip")
                downloadFile(url, zipFile)
                reportProgress(onProgress, "正在解压 ${pkg.displayName}...")
                unzipPackage(zipFile, binDir, pkg)
                zipFile.delete()
            } else {
                downloadFile(url, binFile(pkg.binaryName))
            }

            val target = binFile(pkg.binaryName)
            if (target.isFile) {
                target.setExecutable(true)
                Log.d(TAG, "${pkg.displayName} installed: ${target.absolutePath}")
                reportProgress(onProgress, "${pkg.displayName} ${pkg.version} 安装完成")
                // Verify
                try {
                    val proc = ProcessBuilder(target.absolutePath, "--version")
                        .redirectErrorStream(true)
                        .start()
                    val verOut = proc.inputStream.bufferedReader().readText().take(100)
                    proc.waitFor()
                    reportProgress(onProgress, "${pkg.displayName} ${pkg.version} 安装完成: $verOut")
                } catch (_: Exception) {}
                return@withContext true
            } else {
                reportProgress(onProgress, "${pkg.displayName}: 解压后未找到二进制文件")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "${pkg.displayName} install failed: ${e.message}")
            reportProgress(onProgress, "${pkg.displayName} 安装失败: ${e.message}")
            return@withContext false
        }
    }

    private fun unzipPackage(zipFile: File, destDir: File, pkg: ExtensionPackage) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name
                    // Check if this entry matches the target binary
                    val shouldExtract = when {
                        pkg.extractPath != null -> entryName == pkg.extractPath
                        pkg.binaryName == entryName -> true
                        entryName.endsWith("/${pkg.binaryName}") -> true
                        entryName.endsWith("\\${pkg.binaryName}") -> true
                        entryName == pkg.name || entryName.endsWith("/${pkg.name}") -> true
                        else -> false
                    }
                    if (shouldExtract) {
                        val outFile = File(destDir, pkg.binaryName)
                        outFile.outputStream().use { fos -> zis.copyTo(fos) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun createAppletSymlinks() {
        if (!binFile("busybox").isFile) return
        val bbPath = binFile("busybox").absolutePath
        for (applet in BUSYBOX_APPLETS) {
            val link = File(binDir, applet)
            if (!link.exists()) {
                try {
                    ProcessBuilder("ln", "-sf", bbPath, link.absolutePath)
                        .redirectErrorStream(true).start().waitFor()
                } catch (_: Exception) {
                    try { bbPath.let { binFile("busybox").copyTo(link, overwrite = true) } } catch (_: Exception) {}
                }
            }
        }
    }

    private fun downloadFile(urlStr: String, targetFile: File, maxRetries: Int = 3) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.instanceFollowRedirects = true
                try {
                    conn.connect()
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                        throw Exception("HTTP ${conn.responseCode}")
                    }
                    conn.inputStream.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                            }
                        }
                    }
                } finally { conn.disconnect() }
                return
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Download attempt $attempt/$maxRetries: ${e.message}")
                if (attempt < maxRetries) Thread.sleep(1000)
            }
        }
        throw lastException ?: Exception("Download failed")
    }
}
