package com.openkeyboardsam.prefs

import android.content.Context
import android.content.SharedPreferences
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage

enum class ThemeMode(val id: String) {
    AUTO_SYSTEM("auto"),
    ONE_UI_LIGHT("light"),
    ONE_UI_AMOLED_DARK("dark"),
    DYNAMIC("dynamic")
}

class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("openkeyboardsam_prefs", Context.MODE_PRIVATE)

    var language: KeyboardLanguage
        get() = if (prefs.getString(KEY_LANG, "es") == "en") KeyboardLanguage.ENGLISH else KeyboardLanguage.SPANISH
        set(value) = prefs.edit().putString(KEY_LANG, value.code).apply()

    var themeMode: ThemeMode
        get() {
            val id = prefs.getString(KEY_THEME, ThemeMode.AUTO_SYSTEM.id)
            return ThemeMode.entries.firstOrNull { it.id == id } ?: ThemeMode.AUTO_SYSTEM
        }
        set(value) = prefs.edit().putString(KEY_THEME, value.id).apply()

    var showNumberRow: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW, true)
        set(value) = prefs.edit().putBoolean(KEY_NUMBER_ROW, value).apply()

    var showAltLabels: Boolean
        get() = prefs.getBoolean(KEY_ALT_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_ALT_LABELS, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var swipeTypingEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API, value.trim()).apply()

    companion object {
        private const val KEY_LANG = "lang"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_ALT_LABELS = "alt_labels"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_SWIPE = "swipe_typing"
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
