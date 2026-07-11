package com.chatroom.app.terminal

import android.util.Log
import com.chatroom.app.data.api.ChatApiService
import com.chatroom.app.data.model.ApiAccount
import com.chatroom.app.data.model.ChatMessage
import com.chatroom.app.data.model.ChatRequest
import com.chatroom.app.data.model.ExecutionStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Provides 5 dev assistant tools that can be invoked from the coding assistant.
 * Each tool gathers context, constructs a specialized prompt, calls the AI, and returns results.
 */
class DevToolExecutor(
    private val repoDir: File,
    private val apiAccount: ApiAccount
) {
    companion object {
        private const val TAG = "DevToolExecutor"
    }

    enum class ToolType {
        CODE_SEARCH,
        CODE_EXPLAIN,
        PERF_ANALYSIS,
        SECURITY_AUDIT,
        API_DEBUG
    }

    data class ToolResult(
        val toolType: ToolType,
        val summary: String,
        val details: String,
        val executionSteps: List<ExecutionStep> = emptyList(),
        val success: Boolean = true,
        val error: String = ""
    )

    private val codeSearch = CodeSearch(repoDir)

    /**
     * Run a code search in the repo.
     */
    suspend fun searchCode(query: String, glob: String? = null): ToolResult {
        val steps = mutableListOf<ExecutionStep>()
        steps.add(ExecutionStep(1, "search", "Searching for: $query"))

        return withContext(Dispatchers.IO) {
            val result = codeSearch.search(query, glob)
            steps[0] = steps[0].copy(success = true)

            if (result.matches.isEmpty()) {
                ToolResult(
                    toolType = ToolType.CODE_SEARCH,
                    summary = "No results found for: $query",
                    details = "Searched ${result.filesSearched} files in ${result.durationMs}ms. No matches found.",
                    executionSteps = steps
                )
            } else {
                val details = buildString {
                    appendLine("Found ${result.matches.size} matches in ${result.filesSearched} files (${result.durationMs}ms):")
                    appendLine()
                    result.matches.groupBy { it.filePath }.forEach { (filePath, matches) ->
                        appendLine("📄 $filePath (${matches.size} matches)")
                        matches.take(5).forEach { match ->
                            appendLine("  ${match.lineNumber}: ${match.lineContent.take(120)}")
                        }
                        if (matches.size > 5) {
                            appendLine("  ... and ${matches.size - 5} more")
                        }
                        appendLine()
                    }
                }
                ToolResult(
                    toolType = ToolType.CODE_SEARCH,
                    summary = "Found ${result.matches.size} matches for: $query",
                    details = details,
                    executionSteps = steps
                )
            }
        }
    }

    /**
     * Explain a piece of code using AI.
     */
    suspend fun explainCode(code: String, fileName: String, language: String = ""): ToolResult {
        val steps = mutableListOf<ExecutionStep>()
        steps.add(ExecutionStep(1, "ai", "Requesting code explanation"))
        steps.add(ExecutionStep(2, "analyze", "Analyzing code context"))

        return withContext(Dispatchers.IO) {
            val prompt = buildString {
                appendLine("You are a senior developer explaining code. Be clear and thorough.")
                appendLine()
                appendLine("Explain the following code from file `$fileName` (${language.ifBlank { "unknown" }}):")
                appendLine()
                appendLine("```${language}")
                appendLine(code)
                appendLine("```")
                appendLine()
                appendLine("Please explain:")
                appendLine("1. What this code does at a high level")
                appendLine("2. Key components and their purposes")
                appendLine("3. How it fits into a typical project structure")
                appendLine("4. Any notable patterns or techniques used")
                appendLine("5. Potential improvements or considerations")
            }

            val aiResponse = callAi(prompt)
            steps[0] = steps[0].copy(success = true)

            if (aiResponse != null) {
                ToolResult(
                    toolType = ToolType.CODE_EXPLAIN,
                    summary = "Explained ${if (fileName.isNotBlank()) fileName else "code snippet"}",
                    details = aiResponse,
                    executionSteps = steps
                )
            } else {
                ToolResult(
                    toolType = ToolType.CODE_EXPLAIN,
                    summary = "Failed to get explanation",
                    details = "AI call failed",
                    success = false,
                    error = "AI response was null",
                    executionSteps = steps
                )
            }
        }
    }

    /**
     * Analyze code for performance issues using AI.
     */
    suspend fun analyzePerformance(filePath: String, code: String): ToolResult {
        val steps = mutableListOf<ExecutionStep>()
        steps.add(ExecutionStep(1, "analyze", "Reading file: $filePath"))
        steps.add(ExecutionStep(2, "ai", "Running performance analysis"))

        return withContext(Dispatchers.IO) {
            // Try to read file if only path given
            val fullCode = if (code.isNotBlank()) code else {
                val file = repoDir.resolve(filePath)
                if (file.exists()) file.readText() else ""
            }

            val lang = filePath.substringAfterLast('.', "")

            val prompt = buildString {
                appendLine("You are a performance engineering expert. Analyze the following code for performance issues.")
                appendLine()
                appendLine("File: `$filePath`")
                appendLine()
                appendLine("```$lang")
                appendLine(fullCode)
                appendLine("```")
                appendLine()
                appendLine("Analyze for:")
                appendLine("1. Performance bottlenecks and inefficiencies")
                appendLine("2. Memory usage concerns")
                appendLine("3. Unnecessary computations or redundant operations")
                appendLine("4. I/O and network performance issues")
                appendLine("5. Scalability concerns")
                appendLine("6. Specific, actionable optimization suggestions with code examples")
                appendLine()
                appendLine("For each issue, rate severity: 🔴 Critical / 🟡 Moderate / 🟢 Minor")
            }

            steps[0] = steps[0].copy(success = true)
            val aiResponse = callAi(prompt)
            steps[1] = steps[1].copy(success = aiResponse != null)

            if (aiResponse != null) {
                ToolResult(
                    toolType = ToolType.PERF_ANALYSIS,
                    summary = "Performance analysis complete for $filePath",
                    details = aiResponse,
                    executionSteps = steps
                )
            } else {
                ToolResult(
                    toolType = ToolType.PERF_ANALYSIS,
                    summary = "Analysis failed",
                    details = "AI call failed",
                    success = false,
                    error = "AI response was null",
                    executionSteps = steps
                )
            }
        }
    }

    /**
     * Audit code for security vulnerabilities using AI.
     */
    suspend fun auditSecurity(filePath: String, code: String): ToolResult {
        val steps = mutableListOf<ExecutionStep>()
        steps.add(ExecutionStep(1, "audit", "Starting security audit"))
        steps.add(ExecutionStep(2, "ai", "Analyzing security posture"))

        return withContext(Dispatchers.IO) {
            val fullCode = if (code.isNotBlank()) code else {
                val file = repoDir.resolve(filePath)
                if (file.exists()) file.readText() else ""
            }

            val prompt = buildString {
                appendLine("You are a cybersecurity expert. Perform a thorough security audit of the following code.")
                appendLine()
                appendLine("File: `$filePath`")
                appendLine()
                appendLine("```")
                appendLine(fullCode.take(15000))
                appendLine("```")
                appendLine()
                appendLine("Check for:")
                appendLine("1. Injection vulnerabilities (SQL, XSS, command injection)")
                appendLine("2. Authentication and authorization issues")
                appendLine("3. Sensitive data exposure / secrets in code")
                appendLine("4. Input validation weaknesses")
                appendLine("5. Insecure direct object references")
                appendLine("6. Security misconfiguration")
                appendLine("7. Known vulnerable dependencies or patterns")
                appendLine("8. OWASP Top 10 concerns")
                appendLine()
                appendLine("For each finding, rate severity: 🔴 Critical / 🟡 High / 🟠 Medium / 🟢 Low")
                appendLine("Provide specific remediation code or configuration changes.")
            }

            steps[0] = steps[0].copy(success = true)
            val aiResponse = callAi(prompt)
            steps[1] = steps[1].copy(success = aiResponse != null)

            if (aiResponse != null) {
                ToolResult(
                    toolType = ToolType.SECURITY_AUDIT,
                    summary = "Security audit complete for $filePath",
                    details = aiResponse,
                    executionSteps = steps
                )
            } else {
                ToolResult(
                    toolType = ToolType.SECURITY_AUDIT,
                    summary = "Audit failed",
                    details = "AI call failed",
                    success = false,
                    error = "AI response was null",
                    executionSteps = steps
                )
            }
        }
    }

    /**
     * Debug an API request/response using AI.
     */
    suspend fun debugApi(
        requestMethod: String,
        requestUrl: String,
        requestHeaders: String = "",
        requestBody: String = "",
        responseStatus: Int = 0,
        responseHeaders: String = "",
        responseBody: String = "",
        errorMessage: String = ""
    ): ToolResult {
        val steps = mutableListOf<ExecutionStep>()
        steps.add(ExecutionStep(1, "analyze", "Analyzing API request/response"))

        return withContext(Dispatchers.IO) {
            val prompt = buildString {
                appendLine("You are an API debugging expert. Analyze this API call and help debug the issue.")
                appendLine()
                appendLine("## Request")
                appendLine("Method: $requestMethod")
                appendLine("URL: $requestUrl")
                if (requestHeaders.isNotBlank()) {
                    appendLine("Headers:")
                    appendLine(requestHeaders)
                }
                if (requestBody.isNotBlank()) {
                    appendLine("Body:")
                    appendLine(requestBody)
                }
                appendLine()
                appendLine("## Response")
                appendLine("Status: $responseStatus")
                if (responseHeaders.isNotBlank()) {
                    appendLine("Headers:")
                    appendLine(responseHeaders)
                }
                if (responseBody.isNotBlank()) {
                    appendLine("Body:")
                    appendLine(responseBody)
                }
                if (errorMessage.isNotBlank()) {
                    appendLine("Error:")
                    appendLine(errorMessage)
                }
                appendLine()
                appendLine("Please analyze:")
                appendLine("1. What might be causing the issue")
                appendLine("2. Whether the request is correctly formed")
                appendLine("3. Whether the response indicates server or client error")
                appendLine("4. Specific fixes for the request or handling code")
                appendLine("5. Alternative approaches if the API design is problematic")
            }

            val aiResponse = callAi(prompt)
            steps[0] = steps[0].copy(success = aiResponse != null)

            if (aiResponse != null) {
                ToolResult(
                    toolType = ToolType.API_DEBUG,
                    summary = "API debug analysis for $requestMethod $requestUrl",
                    details = aiResponse,
                    executionSteps = steps
                )
            } else {
                ToolResult(
                    toolType = ToolType.API_DEBUG,
                    summary = "Debug analysis failed",
                    details = "AI call failed",
                    success = false,
                    error = "AI response was null",
                    executionSteps = steps
                )
            }
        }
    }

    private suspend fun callAi(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val api = ChatApiService.create(apiAccount.apiBaseUrl)
                val messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                )
                val request = ChatRequest(
                    model = apiAccount.model,
                    messages = messages,
                    maxTokens = 4096,
                    stream = false
                )
                val response = api.chatCompletion(
                    authorization = "Bearer ${apiAccount.apiKey}",
                    request = request
                )
                if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content
                } else {
                    Log.e(TAG, "AI call failed: ${response.code()} ${response.errorBody()?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI call exception", e)
                null
            }
        }
    }
}
