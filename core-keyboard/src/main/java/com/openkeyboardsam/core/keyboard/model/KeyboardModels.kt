package com.openkeyboardsam.core.keyboard.model

import android.content.Context
import android.os.Build

object KeyCode {
    const val SHIFT = -1
    const val BACKSPACE = -2
    const val ENTER = -3
    const val SPACE = 32
    const val MODE_SYMBOLS = -4
    const val MODE_ALPHA = -5
    const val MODE_SYMBOLS_2 = -6
    const val LANGUAGE_SWITCH = -7
    const val EMOJI = -8
    const val SETTINGS = -9
}

enum class KeyboardLanguage(val code: String, val displayName: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English");

    fun next(): KeyboardLanguage = if (this == SPANISH) ENGLISH else SPANISH
}

enum class KeyboardMode {
    ALPHA,
    SYMBOLS_1,
    SYMBOLS_2
}

enum class ShiftState {
    OFF,
    ON,
    CAPS_LOCK
}

data class KeySpec(
    val code: Int,
    val label: String,
    val altLabel: String? = null,
    val altCode: Int? = null,
    val weight: Float = 1.0f,
    val isFunctional: Boolean = false,
    val isAccent: Boolean = false,
    val isRepeatable: Boolean = false,
    val longPressChars: List<String> = emptyList()
)

data class KeyboardTheme(
    val backgroundColor: Int,
    val keyColor: Int,
    val keyPressedColor: Int,
    val functionalKeyColor: Int,
    val accentKeyColor: Int,
    val keyTextColor: Int,
    val altTextColor: Int,
    val keyShadowColor: Int,
    val swipeTrailColor: Int,
    val popupBgColor: Int
) {
    companion object {
        fun oneUiLight(): KeyboardTheme = KeyboardTheme(
            backgroundColor = 0xFFECEEF2.toInt(),
            keyColor = 0xFFFFFFFF.toInt(),
            keyPressedColor = 0xFFD9DCE2.toInt(),
            functionalKeyColor = 0xFFD6DBE3.toInt(),
            accentKeyColor = 0xFF0381FE.toInt(),
            keyTextColor = 0xFF1C1E21.toInt(),
            altTextColor = 0xFF6B7280.toInt(),
            keyShadowColor = 0x22000000,
            swipeTrailColor = 0xAA0381FE.toInt(),
            popupBgColor = 0xFFFFFFFF.toInt()
        )

        fun oneUiAmoledDark(): KeyboardTheme = KeyboardTheme(
            backgroundColor = 0xFF0A0A0C.toInt(),
            keyColor = 0xFF23252A.toInt(),
            keyPressedColor = 0xFF363940.toInt(),
            functionalKeyColor = 0xFF17181C.toInt(),
            accentKeyColor = 0xFF3E91FF.toInt(),
            keyTextColor = 0xFFF5F6F8.toInt(),
            altTextColor = 0xFF8E95A2.toInt(),
            keyShadowColor = 0x44000000,
            swipeTrailColor = 0xAA3E91FF.toInt(),
            popupBgColor = 0xFF2D3036.toInt()
        )

        fun oneUiDynamic(context: Context, isNightMode: Boolean): KeyboardTheme {
            val base = if (isNightMode) oneUiAmoledDark() else oneUiLight()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val accent = context.getColor(
                    if (isNightMode) android.R.color.system_accent1_400
                    else android.R.color.system_accent1_600
                )
                return base.copy(
                    accentKeyColor = accent,
                    swipeTrailColor = (accent and 0x00FFFFFF) or 0xAA000000.toInt()
                )
            }
            return base
        }
    }
}
