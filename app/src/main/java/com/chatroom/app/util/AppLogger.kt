package com.chatroom.app.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    @Volatile
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
            if (!dir.exists()) dir.mkdirs()
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
            logFile = File(dir, "chatroom_${sdf.format(Date())}.log")
            i("AppLogger", "Log file: ${logFile?.absolutePath}")
        }
    }

    fun d(tag: String, message: String) {
        android.util.Log.d(tag, message)
        writeToFile("D/$tag", message)
    }

    fun i(tag: String, message: String) {
        android.util.Log.i(tag, message)
        writeToFile("I/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        android.util.Log.e(tag, message, throwable)
        val stack = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        writeToFile("E/$tag", "$message$stack")
    }

    private fun writeToFile(tag: String, message: String) {
        val file = logFile ?: return
        val line: String
        synchronized(dateFormat) {
            line = "${dateFormat.format(Date())} [$tag] $message"
        }
        try {
            FileWriter(file, true).use { writer ->
                writer.appendLine(line)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppLogger", "write failed: ${e.message}")
        }
    }
}
