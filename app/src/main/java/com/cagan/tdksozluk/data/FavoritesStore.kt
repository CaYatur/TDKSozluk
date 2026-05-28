package com.cagan.tdksozluk.data

import android.content.Context

class FavoritesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun isFavorite(madde: String): Boolean = prefs.getBoolean(key(madde), false)

    fun toggle(madde: String): Boolean {
        val now = !isFavorite(madde)
        prefs.edit().apply {
            if (now) {
                putBoolean(key(madde), true)
                putLong(timeKey(madde), System.currentTimeMillis())
            } else {
                remove(key(madde))
                remove(timeKey(madde))
            }
        }.apply()
        return now
    }

    fun all(): List<String> {
        return prefs.all.entries
            .filter { it.key.startsWith(KEY_PREFIX) && it.value == true }
            .sortedByDescending { prefs.getLong(TIME_PREFIX + it.key.substring(KEY_PREFIX.length), 0L) }
            .map { it.key.substring(KEY_PREFIX.length) }
    }

    private fun key(madde: String) = KEY_PREFIX + madde
    private fun timeKey(madde: String) = TIME_PREFIX + madde

    companion object {
        private const val KEY_PREFIX = "fav:"
        private const val TIME_PREFIX = "favt:"
    }
}
