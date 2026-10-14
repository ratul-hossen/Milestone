package com.milestone.app.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.milestone.app.MilestoneApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Builds a ViewModel factory with access to the app container and the nav arguments. */
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (MilestoneApp, SavedStateHandle) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(this[APPLICATION_KEY] as MilestoneApp, createSavedStateHandle()) }
}

/** Emits the current date and again shortly after midnight, so "today" never goes stale. */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        val today = LocalDate.now()
        emit(today)
        val msToMidnight = java.time.Duration.between(
            java.time.LocalDateTime.now(), today.plusDays(1).atStartOfDay()
        ).toMillis()
        delay(msToMidnight + 1_000)
    }
}

/** Tiny SharedPreferences wrapper for the only user setting: the name in the greeting. */
class UserPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("user", Context.MODE_PRIVATE)
    var name: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) = prefs.edit().putString("name", value.trim()).apply()
}
