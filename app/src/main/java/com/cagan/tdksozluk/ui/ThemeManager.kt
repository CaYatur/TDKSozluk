package com.cagan.tdksozluk.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFS = "ui"
    private const val KEY_THEME = "theme_mode"

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    fun apply(context: Context) {
        val mode = current(context)
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    fun current(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, MODE_SYSTEM)

    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, mode).apply()
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode))
    }

    private fun toNightMode(mode: Int): Int = when (mode) {
        MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}
