package com.example

import android.content.Context
import android.content.SharedPreferences
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        val stackTrace = sw.toString()

        val prefs: SharedPreferences = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_crash", stackTrace).commit()

        defaultHandler?.uncaughtException(t, e)
    }
}
