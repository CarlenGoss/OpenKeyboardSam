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
    SPANISH("es", "Español(ES)"),
    ENGLISH("en", "English(US)");

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
    val isDark: Boolean,
    val backgroundColor: Int,
    val toolbarBackgroundColor: Int,
    val dividerColor: Int,
    val keyColor: Int,
    val numberKeyColor: Int,
    val keyPressedColor: Int,
    val functionalKeyColor: Int,
    val accentKeyColor: Int,
    val keyTextColor: Int,
    val functionalIconColor: Int,
    val altTextColor: Int,
    val keyShadowColor: Int,
    val keyTopBorderColor: Int,
    val swipeTrailColor: Int,
    val popupBgColor: Int,
    val popupBorderColor: Int
) {
    companion object {
        /**
         * Exact Samsung Honeyboard One UI 6.1 Light palette:
         * - Soft cool gray chassis (#E5E7EC)
         * - Crisp pure white keys (#FFFFFF) with subtle drop shadow
         * - Darker cool gray functional keys (#CFD4DE)
         */
        fun oneUiLight(): KeyboardTheme = KeyboardTheme(
            isDark = false,
            backgroundColor = 0xFFE5E7EC.toInt(),
            toolbarBackgroundColor = 0xFFE5E7EC.toInt(),
            dividerColor = 0xFFD4D7DF.toInt(),
            keyColor = 0xFFFFFFFF.toInt(),
            numberKeyColor = 0xFFF7F8FA.toInt(),
            keyPressedColor = 0xFFD8DCE6.toInt(),
            functionalKeyColor = 0xFFCFD4DE.toInt(),
            accentKeyColor = 0xFF0381FE.toInt(),
            keyTextColor = 0xFF191C20.toInt(),
            functionalIconColor = 0xFF2C3137.toInt(),
            altTextColor = 0xFF7D8490.toInt(),
            keyShadowColor = 0x26000000,
            keyTopBorderColor = 0x40FFFFFF,
            swipeTrailColor = 0xB80381FE.toInt(),
            popupBgColor = 0xFFFFFFFF.toInt(),
            popupBorderColor = 0xFFD0D5DF.toInt()
        )

        /**
         * Exact Samsung Honeyboard One UI 6.1 Dark palette:
         * - Deep slate-black chassis (#17171A)
         * - Elevated charcoal keys (#2C2C30)
         * - Darker functional keys (#212125)
         */
        fun oneUiAmoledDark(): KeyboardTheme = KeyboardTheme(
            isDark = true,
            backgroundColor = 0xFF17171A.toInt(),
            toolbarBackgroundColor = 0xFF17171A.toInt(),
            dividerColor = 0xFF26262B.toInt(),
            keyColor = 0xFF2C2C30.toInt(),
            numberKeyColor = 0xFF26262A.toInt(),
            keyPressedColor = 0xFF424248.toInt(),
            functionalKeyColor = 0xFF202024.toInt(),
            accentKeyColor = 0xFF3E91FF.toInt(),
            keyTextColor = 0xFFF2F2F7.toInt(),
            functionalIconColor = 0xFFD8D8E0.toInt(),
            altTextColor = 0xFF8A8A93.toInt(),
            keyShadowColor = 0x55000000,
            keyTopBorderColor = 0x18FFFFFF,
            swipeTrailColor = 0xB83E91FF.toInt(),
            popupBgColor = 0xFF36363C.toInt(),
            popupBorderColor = 0xFF4C4C54.toInt()
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
                    swipeTrailColor = (accent and 0x00FFFFFF) or 0xB8000000.toInt()
                )
            }
            return base
        }
    }
}
