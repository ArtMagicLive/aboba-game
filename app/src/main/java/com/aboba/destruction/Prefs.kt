package com.aboba.destruction

import android.content.Context

/**
 * Простое хранилище прогресса: монеты, рекорды, уровни улучшений, звук.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("aboba_prefs", Context.MODE_PRIVATE)

    var bank: Int
        get() = sp.getInt("bank", 0)
        set(value) {
            sp.edit().putInt("bank", value).apply()
        }

    var bestWave: Int
        get() = sp.getInt("bestWave", 0)
        set(value) {
            sp.edit().putInt("bestWave", value).apply()
        }

    var bestScore: Int
        get() = sp.getInt("bestScore", 0)
        set(value) {
            sp.edit().putInt("bestScore", value).apply()
        }

    var totalKills: Int
        get() = sp.getInt("totalKills", 0)
        set(value) {
            sp.edit().putInt("totalKills", value).apply()
        }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(value) {
            sp.edit().putBoolean("sound", value).apply()
        }

    fun upgrade(index: Int): Int = sp.getInt("up_$index", 0)

    fun setUpgrade(index: Int, level: Int) {
        sp.edit().putInt("up_$index", level).apply()
    }
}
