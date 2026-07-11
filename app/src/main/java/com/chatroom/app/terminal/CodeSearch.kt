package com.chatroom.app.terminal

import android.util.Log
import java.io.File

/**
 * Search within repository files for text patterns.
 * Supports plain text search and regex search.
 */
class CodeSearch(
    private val repoDir: File
) {
    companion object {
        private const val TAG = "CodeSearch"
        private const val MAX_RESULTS = 50
        private const val MAX_FILE_SIZE = 1024 * 1024 // 1MB
    }

    data class SearchMatch(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String,
        val matchStart: Int = 0,
        val matchEnd: Int = 0
    )

    data class SearchResult(
        val query: String,
        val matches: List<SearchMatch>,
        val filesSearched: Int,
        val durationMs: Long
    )

    /**
     * Search for a text pattern in all files within the repo.
     */
    fun search(query: String, glob: String? = null, isRegex: Boolean = false): SearchResult {
        val startTime = System.currentTimeMillis()
        val matches = mutableListOf<SearchMatch>()
        var filesSearched = 0

        val pattern = if (isRegex) query else Regex.escape(query)
        val regex = try {
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            Log.e(TAG, "Invalid regex: $query", e)
            return SearchResult(query, emptyList(), 0, System.currentTimeMillis() - startTime)
        }

        searchInDir(repoDir, regex, matches, glob) { filesSearched++ }

        return SearchResult(
            query = query,
            matches = matches.take(MAX_RESULTS),
            filesSearched = filesSearched,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Find files by extension or name pattern.
     */
    fun findFiles(pattern: String): List<File> {
        val results = mutableListOf<File>()
        val regex = try {
            Regex(pattern, setOf(RegexOption.IGNORE_CASE))
        } catch (e: Exception) {
            Regex(Regex.escape(pattern), setOf(RegexOption.IGNORE_CASE))
        }

        findFilesInDir(repoDir, regex, results)
        return results
    }

    /**
     * Get directory tree as a formatted string (up to a depth).
     */
    fun getDirectoryTree(maxDepth: Int = 3): String {
        return buildTree(repoDir, "", 0, maxDepth)
    }

    private fun searchInDir(
        dir: File,
        regex: Regex,
        results: MutableList<SearchMatch>,
        glob: String?,
        onFileSearched: () -> Unit
    ) {
        if (results.size >= MAX_RESULTS) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (results.size >= MAX_RESULTS) break
            if (file.isDirectory) {
                if (shouldSkipDir(file.name)) continue
                searchInDir(file, regex, results, glob, onFileSearched)
            } else if (file.isFile && file.length() <= MAX_FILE_SIZE) {
                if (glob != null && !file.name.contains(glob, ignoreCase = true)) continue
                if (shouldSkipFile(file.name)) continue
                onFileSearched()
                searchInFile(file, regex, results)
            }
        }
    }

    private fun searchInFile(file: File, regex: Regex, results: MutableList<SearchMatch>) {
        try {
            val relPath = file.relativeTo(repoDir).path
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val matchResult = regex.find(line)
                    if (matchResult != null) {
                        results.add(SearchMatch(
                            filePath = relPath,
                            lineNumber = index + 1,
                            lineContent = line.trim(),
                            matchStart = matchResult.range.first,
                            matchEnd = matchResult.range.last
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read file: ${file.path}", e)
        }
    }

    private fun findFilesInDir(dir: File, regex: Regex, results: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (shouldSkipDir(file.name)) continue
                findFilesInDir(file, regex, results)
            } else if (file.isFile) {
                if (shouldSkipFile(file.name)) continue
                if (regex.containsMatchIn(file.name)) {
                    results.add(file)
                }
            }
        }
    }

    private fun buildTree(dir: File, prefix: String, depth: Int, maxDepth: Int): String {
        if (depth > maxDepth) return "${prefix}└── ...\n"
        val sb = StringBuilder()
        val files = dir.listFiles()?.filter { !shouldSkipDir(it.name) && !shouldSkipFile(it.name) }
            ?.sortedWith(compareBy({ it.isDirectory }, { it.name.lowercase() }))
            ?.take(30) ?: return sb.toString()

        files.forEachIndexed { index, file ->
            val isLast = index == files.size - 1
            val connector = if (isLast) "└── " else "├── "
            sb.append("$prefix$connector${file.name}")
            if (file.isDirectory) {
                sb.appendLine("/")
                val ext = if (isLast) "    " else "│   "
                sb.append(buildTree(file, "$prefix$ext", depth + 1, maxDepth))
            } else {
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun shouldSkipDir(name: String): Boolean {
        return name in listOf(".git", "node_modules", ".gradle", "build", "target",
            "dist", ".next", "venv", "__pycache__", ".cache", ".idea", "bin", "obj")
    }

    private fun shouldSkipFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "")
        return ext in listOf("png", "jpg", "jpeg", "gif", "bmp", "ico", "svg",
            "woff", "woff2", "ttf", "eot", "o", "so", "dylib", "class",
            "jar", "zip", "tar", "gz", "bz2", "7z", "rar")
    }
}
