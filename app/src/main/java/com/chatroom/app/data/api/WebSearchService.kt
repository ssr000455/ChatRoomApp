package com.chatroom.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WebSearchService {

    private val client: OkHttpClient = run {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Perform a web search.
     * @param endpoint The search API endpoint URL. Use {query} as placeholder for the search term.
     * @param apiKey Optional API key (appended as Authorization header if present)
     * @param query The search query string
     * @return Formatted search result text
     */
    suspend fun search(
        endpoint: String,
        apiKey: String?,
        query: String
    ): String {
        val url = endpoint.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .get()

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
            requestBuilder.header("X-API-Key", apiKey)
        }

        val response = withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute()
        }

        if (!response.isSuccessful) {
            return "Search failed: HTTP ${response.code}"
        }

        val body = response.body?.string() ?: return "No search results"
        return formatSearchResults(body)
    }

    private fun formatSearchResults(jsonBody: String): String {
        // Try to parse as DuckDuckGo Instant Answer format
        return try {
            val gson = com.google.gson.Gson()
            val map = gson.fromJson(jsonBody, Map::class.java) as? Map<*, *>
            if (map != null) {
                formatDuckDuckGo(map)
            } else {
                // Return raw text if we can't parse
                if (jsonBody.length > 3000) jsonBody.take(3000) + "…" else jsonBody
            }
        } catch (e: Exception) {
            // If not JSON, return raw text truncated
            if (jsonBody.length > 3000) jsonBody.take(3000) + "…" else jsonBody
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun formatDuckDuckGo(map: Map<*, *>): String {
        val parts = mutableListOf<String>()

        // Abstract
        val abstractText = map["AbstractText"] as? String
        val abstractSource = map["AbstractSource"] as? String
        if (!abstractText.isNullOrBlank()) {
            parts.add("Abstract: $abstractText")
            if (!abstractSource.isNullOrBlank()) {
                parts.add("Source: $abstractSource")
            }
            val abstractUrl = map["AbstractURL"] as? String
            if (!abstractUrl.isNullOrBlank()) {
                parts.add("URL: $abstractUrl")
            }
        }

        // Answer
        val answer = map["Answer"] as? String
        if (!answer.isNullOrBlank()) {
            parts.add("Answer: $answer")
        }

        // Related topics
        val relatedTopics = map["RelatedTopics"] as? List<*>
        if (relatedTopics != null) {
            val results = mutableListOf<String>()
            for (topic in relatedTopics) {
                when (topic) {
                    is Map<*, *> -> {
                        val text = topic["Text"] as? String
                        val firstUrl = topic["FirstURL"] as? String
                        if (text != null) {
                            val entry = if (firstUrl != null) "$text ($firstUrl)" else text
                            results.add(entry)
                        }
                        // Handle nested topics (Name + Topics)
                        val topics = topic["Topics"] as? List<*>
                        if (topics != null) {
                            for (sub in topics) {
                                if (sub is Map<*, *>) {
                                    val subText = sub["Text"] as? String
                                    val subUrl = sub["FirstURL"] as? String
                                    if (subText != null) {
                                        results.add(if (subUrl != null) "$subText ($subUrl)" else subText)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (results.isNotEmpty()) {
                parts.add("")
                parts.add("Related results:")
                results.take(8).forEachIndexed { i, r ->
                    parts.add("${i + 1}. $r")
                }
            }
        }

        // Results
        val results = map["Results"] as? List<*>
        if (results != null && results.isNotEmpty()) {
            parts.add("")
            parts.add("Search results:")
            for ((i, result) in results.withIndex()) {
                if (result is Map<*, *>) {
                    val text = result["Text"] as? String
                    val url = result["FirstURL"] as? String
                    if (text != null) {
                        parts.add("${i + 1}. $text${if (url != null) " ($url)" else ""}")
                    }
                }
                if (i >= 8) break
            }
        }

        return if (parts.isEmpty()) {
            "No relevant search results found."
        } else {
            parts.joinToString("\n")
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.duckduckgo.com/?q={query}&format=json&no_html=1&skip_disambig=1"

        fun createDefault(): WebSearchService = WebSearchService()
    }
}
