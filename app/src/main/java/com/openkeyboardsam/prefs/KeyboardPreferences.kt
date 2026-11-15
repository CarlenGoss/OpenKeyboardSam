package com.openkeyboardsam.prefs

import android.content.Context
import android.content.SharedPreferences
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.core.keyboard.model.LayoutSizingMode

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

    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE, value).apply()

    var autoCapitalize: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAP, value).apply()

    var keyboardHeightScale: Float
        get() = prefs.getFloat(KEY_HEIGHT_SCALE, 1.0f).coerceIn(0.80f, 1.30f)
        set(value) = prefs.edit().putFloat(KEY_HEIGHT_SCALE, value.coerceIn(0.80f, 1.30f)).apply()

    var layoutSizingMode: LayoutSizingMode
        get() {
            val name = prefs.getString(KEY_SIZING_MODE, LayoutSizingMode.FULL.name)
            return LayoutSizingMode.entries.firstOrNull { it.name == name } ?: LayoutSizingMode.FULL
        }
        set(value) = prefs.edit().putString(KEY_SIZING_MODE, value.name).apply()

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
        private const val KEY_DOUBLE_SPACE = "double_space_period"
        private const val KEY_AUTO_CAP = "auto_cap"
        private const val KEY_HEIGHT_SCALE = "height_scale"
        private const val KEY_SIZING_MODE = "sizing_mode"
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
