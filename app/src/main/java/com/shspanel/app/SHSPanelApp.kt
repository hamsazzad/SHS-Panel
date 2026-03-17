package com.shspanel.app

import android.app.Application
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SHSPanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val report = """
SHS Panel Crash Report — $timestamp
Thread: ${thread.name}
Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
App Version: 1.0.2

$stackTrace
""".trimIndent()
                val logFile = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "SHSPanel_crash_$timestamp.txt"
                )
                logFile.writeText(report)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
