package com.milestone.app.util

import android.util.Log
import com.milestone.app.BuildConfig

/**
 * Single entry point for logging, so Logcat can be filtered per area during a demo:
 *
 *   adb logcat -s Milestone.DB Milestone.ML Milestone.Timer Milestone.UI
 *
 * or in Android Studio's Logcat: `tag:Milestone`.
 */
object AppLog {
    const val DB = "Milestone.DB"
    const val ML = "Milestone.ML"
    const val TIMER = "Milestone.Timer"
    const val UI = "Milestone.UI"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) = Log.i(tag, message)

    fun w(tag: String, message: String, error: Throwable? = null) = Log.w(tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = Log.e(tag, message, error)
}
