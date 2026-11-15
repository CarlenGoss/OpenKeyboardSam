package com.openkeyboardsam.core.keyboard.layout

import com.openkeyboardsam.core.keyboard.model.KeyCode
import com.openkeyboardsam.core.keyboard.model.KeySpec
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.core.keyboard.model.KeyboardMode

object LayoutProvider {

    private val numberRow: List<KeySpec> = listOf(
        KeySpec('1'.code, "1", longPressChars = listOf("¹", "½", "⅓", "¼", "⅛")),
        KeySpec('2'.code, "2", longPressChars = listOf("²", "⅔")),
        KeySpec('3'.code, "3", longPressChars = listOf("³", "¾", "⅜")),
        KeySpec('4'.code, "4", longPressChars = listOf("⁴")),
        KeySpec('5'.code, "5", longPressChars = listOf("⁵", "⅝")),
        KeySpec('6'.code, "6", longPressChars = listOf("⁶")),
        KeySpec('7'.code, "7", longPressChars = listOf("⁷", "⅞")),
        KeySpec('8'.code, "8", longPressChars = listOf("⁸")),
        KeySpec('9'.code, "9", longPressChars = listOf("⁹")),
        KeySpec('0'.code, "0", longPressChars = listOf("⁰", "∅"))
    )

    private val spanishAccents = mapOf(
        "a" to listOf("á", "à", "ä", "â", "ã", "ª"),
        "e" to listOf("é", "è", "ë", "ê", "€"),
        "i" to listOf("í", "ì", "ï", "î"),
        "o" to listOf("ó", "ò", "ö", "ô", "õ", "º"),
        "u" to listOf("ú", "ü", "ù", "û"),
        "n" to listOf("ñ"),
        "c" to listOf("ç"),
        "s" to listOf("ß", "$")
    )

    private val englishAccents = mapOf(
        "a" to listOf("á", "à", "â", "ä", "æ", "ã"),
        "e" to listOf("é", "è", "ê", "ë", "€"),
        "i" to listOf("í", "î", "ï", "ì"),
        "o" to listOf("ó", "ô", "ö", "ò", "œ", "õ"),
        "u" to listOf("ú", "ü", "ù", "û"),
        "n" to listOf("ñ"),
        "c" to listOf("ç")
    )

    private fun charKey(
        ch: Char,
        alt: String? = null,
        language: KeyboardLanguage = KeyboardLanguage.SPANISH
    ): KeySpec {
        val lower = ch.lowercaseChar().toString()
        val accents = if (language == KeyboardLanguage.SPANISH) {
            spanishAccents[lower].orEmpty()
        } else {
            englishAccents[lower].orEmpty()
        }
        return KeySpec(
            code = ch.code,
            label = ch.toString(),
            altLabel = alt,
            altCode = alt?.firstOrNull()?.code,
            longPressChars = (listOfNotNull(alt) + accents).distinct()
        )
    }

    fun getRows(
        language: KeyboardLanguage,
        mode: KeyboardMode,
        showNumberRow: Boolean
    ): List<List<KeySpec>> {
        return when (mode) {
            KeyboardMode.ALPHA -> buildAlphaRows(language, showNumberRow)
            KeyboardMode.SYMBOLS_1 -> buildSymbols1Rows()
            KeyboardMode.SYMBOLS_2 -> buildSymbols2Rows()
        }
    }

    private fun buildAlphaRows(
        language: KeyboardLanguage,
        showNumberRow: Boolean
    ): List<List<KeySpec>> {
        val rows = mutableListOf<List<KeySpec>>()
        if (showNumberRow) {
            rows.add(numberRow)
        }

        // Exact Samsung One UI secondary symbols per QWERTY row
        val row1 = listOf(
            charKey('q', if (showNumberRow) "+" else "1", language),
            charKey('w', if (showNumberRow) "×" else "2", language),
            charKey('e', if (showNumberRow) "÷" else "3", language),
            charKey('r', if (showNumberRow) "=" else "4", language),
            charKey('t', if (showNumberRow) "/" else "5", language),
            charKey('y', if (showNumberRow) "_" else "6", language),
            charKey('u', if (showNumberRow) "<" else "7", language),
            charKey('i', if (showNumberRow) ">" else "8", language),
            charKey('o', if (showNumberRow) "[" else "9", language),
            charKey('p', if (showNumberRow) "]" else "0", language)
        )
        rows.add(row1)

        val row2 = if (language == KeyboardLanguage.SPANISH) {
            listOf(
                charKey('a', "!", language),
                charKey('s', "@", language),
                charKey('d', "#", language),
                charKey('f', "$", language),
                charKey('g', "%", language),
                charKey('h', "&", language),
                charKey('j', "*", language),
                charKey('k', "(", language),
                charKey('l', ")", language),
                charKey('ñ', "-", language)
            )
        } else {
            listOf(
                charKey('a', "!", language),
                charKey('s', "@", language),
                charKey('d', "#", language),
                charKey('f', "$", language),
                charKey('g', "%", language),
                charKey('h', "^", language),
                charKey('j', "&", language),
                charKey('k', "*", language),
                charKey('l', "(", language)
            )
        }
        rows.add(row2)

        val row3 = listOf(
            KeySpec(KeyCode.SHIFT, "SHIFT", weight = 1.48f, isFunctional = true),
            charKey('z', "'", language),
            charKey('x', "\"", language),
            charKey('c', ":", language),
            charKey('v', ";", language),
            charKey('b', ",", language),
            charKey('n', "?", language),
            charKey('m', "¡", language),
            KeySpec(KeyCode.BACKSPACE, "DEL", weight = 1.48f, isFunctional = true, isRepeatable = true)
        )
        rows.add(row3)

        // Exact Samsung Honeyboard bottom row: [!#1] [🌐] [,] [   Español(ES)   ] [.] [↵]
        val row4 = listOf(
            KeySpec(KeyCode.MODE_SYMBOLS, "!#1", weight = 1.38f, isFunctional = true),
            KeySpec(KeyCode.LANGUAGE_SWITCH, "🌐", weight = 1.02f, isFunctional = true),
            KeySpec(','.code, ",", weight = 0.95f, longPressChars = listOf(";", ":", "¡", "¿")),
            KeySpec(KeyCode.SPACE, language.displayName, weight = 4.10f),
            KeySpec('.'.code, ".", weight = 0.95f, longPressChars = listOf("?", "!", "¿", "¡", ",", ":", ";", "/", "@")),
            KeySpec(KeyCode.ENTER, "ENTER", weight = 1.60f, isFunctional = true, isAccent = true)
        )
        rows.add(row4)

        return rows
    }

    private fun buildSymbols1Rows(): List<List<KeySpec>> = listOf(
        numberRow,
        listOf(
            KeySpec('+'.code, "+"),
            KeySpec('×'.code, "×"),
            KeySpec('÷'.code, "÷"),
            KeySpec('='.code, "="),
            KeySpec('/'.code, "/"),
            KeySpec('_'.code, "_"),
            KeySpec('€'.code, "€"),
            KeySpec('£'.code, "£"),
            KeySpec('¥'.code, "¥"),
            KeySpec('₩'.code, "₩")
        ),
        listOf(
            KeySpec('!'.code, "!"),
            KeySpec('@'.code, "@"),
            KeySpec('#'.code, "#"),
            KeySpec('$'.code, "$"),
            KeySpec('%'.code, "%"),
            KeySpec('^'.code, "^"),
            KeySpec('&'.code, "&"),
            KeySpec('*'.code, "*"),
            KeySpec('('.code, "("),
            KeySpec(')'.code, ")")
        ),
        listOf(
            KeySpec(KeyCode.MODE_SYMBOLS_2, "1/2", weight = 1.48f, isFunctional = true),
            KeySpec('-'.code, "-"),
            KeySpec('\''.code, "'"),
            KeySpec('"'.code, "\""),
            KeySpec(':'.code, ":"),
            KeySpec(';'.code, ";"),
            KeySpec('¿'.code, "¿"),
            KeySpec('?'.code, "?"),
            KeySpec(KeyCode.BACKSPACE, "DEL", weight = 1.48f, isFunctional = true, isRepeatable = true)
        ),
        listOf(
            KeySpec(KeyCode.MODE_ALPHA, "ABC", weight = 1.38f, isFunctional = true),
            KeySpec(KeyCode.LANGUAGE_SWITCH, "🌐", weight = 1.02f, isFunctional = true),
            KeySpec(','.code, ",", weight = 0.95f),
            KeySpec(KeyCode.SPACE, " ", weight = 4.10f),
            KeySpec('.'.code, ".", weight = 0.95f),
            KeySpec(KeyCode.ENTER, "ENTER", weight = 1.60f, isFunctional = true, isAccent = true)
        )
    )

    private fun buildSymbols2Rows(): List<List<KeySpec>> = listOf(
        numberRow,
        listOf(
            KeySpec('`'.code, "`"),
            KeySpec('~'.code, "~"),
            KeySpec('\\'.code, "\\"),
            KeySpec('|'.code, "|"),
            KeySpec('{'.code, "{"),
            KeySpec('}'.code, "}"),
            KeySpec('['.code, "["),
            KeySpec(']'.code, "]"),
            KeySpec('<'.code, "<"),
            KeySpec('>'.code, ">")
        ),
        listOf(
            KeySpec('¡'.code, "¡"),
            KeySpec('¿'.code, "¿"),
            KeySpec('°'.code, "°"),
            KeySpec('•'.code, "•"),
            KeySpec('©'.code, "©"),
            KeySpec('®'.code, "®"),
            KeySpec('™'.code, "™"),
            KeySpec('✓'.code, "✓"),
            KeySpec('«'.code, "«"),
            KeySpec('»'.code, "»")
        ),
        listOf(
            KeySpec(KeyCode.MODE_SYMBOLS, "2/2", weight = 1.48f, isFunctional = true),
            KeySpec('—'.code, "—"),
            KeySpec('…'.code, "…"),
            KeySpec('±'.code, "±"),
            KeySpec('≠'.code, "≠"),
            KeySpec('≤'.code, "≤"),
            KeySpec('≥'.code, "≥"),
            KeySpec('∞'.code, "∞"),
            KeySpec(KeyCode.BACKSPACE, "DEL", weight = 1.48f, isFunctional = true, isRepeatable = true)
        ),
        listOf(
            KeySpec(KeyCode.MODE_ALPHA, "ABC", weight = 1.38f, isFunctional = true),
            KeySpec(KeyCode.LANGUAGE_SWITCH, "🌐", weight = 1.02f, isFunctional = true),
            KeySpec(','.code, ",", weight = 0.95f),
            KeySpec(KeyCode.SPACE, " ", weight = 4.10f),
            KeySpec('.'.code, ".", weight = 0.95f),
            KeySpec(KeyCode.ENTER, "ENTER", weight = 1.60f, isFunctional = true, isAccent = true)
        )
    )
}
