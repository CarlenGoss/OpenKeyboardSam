package com.openkeyboardsam.core.keyboard.layout

import com.openkeyboardsam.core.keyboard.model.KeyCode
import com.openkeyboardsam.core.keyboard.model.KeySpec
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.core.keyboard.model.KeyboardMode

object LayoutProvider {

    private val numberRow: List<KeySpec> = listOf(
        KeySpec('1'.code, "1"),
        KeySpec('2'.code, "2"),
        KeySpec('3'.code, "3"),
        KeySpec('4'.code, "4"),
        KeySpec('5'.code, "5"),
        KeySpec('6'.code, "6"),
        KeySpec('7'.code, "7"),
        KeySpec('8'.code, "8"),
        KeySpec('9'.code, "9"),
        KeySpec('0'.code, "0")
    )

    private val spanishAccents = mapOf(
        "a" to listOf("á", "à", "ä", "â", "ã", "@"),
        "e" to listOf("é", "è", "ë", "ê", "€"),
        "i" to listOf("í", "ì", "ï", "î"),
        "o" to listOf("ó", "ò", "ö", "ô", "õ"),
        "u" to listOf("ú", "ù", "ü", "û"),
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
            emptyList()
        }
        return KeySpec(
            code = ch.code,
            label = ch.toString(),
            altLabel = alt,
            altCode = alt?.firstOrNull()?.code,
            longPressChars = accents + listOfNotNull(alt)
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

        val row1 = listOf(
            charKey('q', "1", language),
            charKey('w', "2", language),
            charKey('e', "3", language),
            charKey('r', "4", language),
            charKey('t', "5", language),
            charKey('y', "6", language),
            charKey('u', "7", language),
            charKey('i', "8", language),
            charKey('o', "9", language),
            charKey('p', "0", language)
        )
        rows.add(row1)

        val row2 = mutableListOf(
            charKey('a', "@", language),
            charKey('s', "#", language),
            charKey('d', "$", language),
            charKey('f', "%", language),
            charKey('g', "&", language),
            charKey('h', "*", language),
            charKey('j', "-", language),
            charKey('k', "+", language),
            charKey('l', "(", language)
        )
        if (language == KeyboardLanguage.SPANISH) {
            row2.add(charKey('ñ', ")", language))
        }
        rows.add(row2)

        val row3 = listOf(
            KeySpec(KeyCode.SHIFT, "⇧", weight = 1.45f, isFunctional = true),
            charKey('z', "!", language),
            charKey('x', "\"", language),
            charKey('c', "'", language),
            charKey('v', ":", language),
            charKey('b', ";", language),
            charKey('n', "/", language),
            charKey('m', "?", language),
            KeySpec(KeyCode.BACKSPACE, "⌫", weight = 1.45f, isFunctional = true, isRepeatable = true)
        )
        rows.add(row3)

        val row4 = listOf(
            KeySpec(KeyCode.MODE_SYMBOLS, "!#1", weight = 1.35f, isFunctional = true),
            KeySpec(KeyCode.EMOJI, "☺", weight = 1.0f, isFunctional = true),
            KeySpec(','.code, ",", weight = 0.9f, longPressChars = listOf(";", ":")),
            KeySpec(KeyCode.SPACE, language.displayName, weight = 4.2f),
            KeySpec('.'.code, ".", weight = 0.9f, longPressChars = listOf("!", "?", "¿", "¡", ",", ":", ";", "@", "#")),
            KeySpec(KeyCode.ENTER, "↵", weight = 1.65f, isFunctional = true, isAccent = true)
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
            KeySpec('<'.code, "<"),
            KeySpec('>'.code, ">"),
            KeySpec('['.code, "["),
            KeySpec(']'.code, "]")
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
            KeySpec(KeyCode.MODE_SYMBOLS_2, "1/2", weight = 1.45f, isFunctional = true),
            KeySpec('-'.code, "-"),
            KeySpec('\''.code, "'"),
            KeySpec('"'.code, "\""),
            KeySpec(':'.code, ":"),
            KeySpec(';'.code, ";"),
            KeySpec(','.code, ","),
            KeySpec('?'.code, "?"),
            KeySpec(KeyCode.BACKSPACE, "⌫", weight = 1.45f, isFunctional = true, isRepeatable = true)
        ),
        listOf(
            KeySpec(KeyCode.MODE_ALPHA, "ABC", weight = 1.35f, isFunctional = true),
            KeySpec(KeyCode.EMOJI, "☺", weight = 1.0f, isFunctional = true),
            KeySpec('¿'.code, "¿", weight = 0.9f),
            KeySpec(KeyCode.SPACE, "Espacio", weight = 4.2f),
            KeySpec('.'.code, ".", weight = 0.9f),
            KeySpec(KeyCode.ENTER, "↵", weight = 1.65f, isFunctional = true, isAccent = true)
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
            KeySpec('€'.code, "€"),
            KeySpec('£'.code, "£"),
            KeySpec('¥'.code, "¥"),
            KeySpec('°'.code, "°")
        ),
        listOf(
            KeySpec('¡'.code, "¡"),
            KeySpec('¿'.code, "¿"),
            KeySpec('§'.code, "§"),
            KeySpec('©'.code, "©"),
            KeySpec('®'.code, "®"),
            KeySpec('™'.code, "™"),
            KeySpec('•'.code, "•"),
            KeySpec('√'.code, "√"),
            KeySpec('π'.code, "π"),
            KeySpec('∞'.code, "∞")
        ),
        listOf(
            KeySpec(KeyCode.MODE_SYMBOLS, "2/2", weight = 1.45f, isFunctional = true),
            KeySpec('«'.code, "«"),
            KeySpec('»'.code, "»"),
            KeySpec('—'.code, "—"),
            KeySpec('…'.code, "…"),
            KeySpec('≠'.code, "≠"),
            KeySpec('≤'.code, "≤"),
            KeySpec('≥'.code, "≥"),
            KeySpec(KeyCode.BACKSPACE, "⌫", weight = 1.45f, isFunctional = true, isRepeatable = true)
        ),
        listOf(
            KeySpec(KeyCode.MODE_ALPHA, "ABC", weight = 1.35f, isFunctional = true),
            KeySpec(KeyCode.EMOJI, "☺", weight = 1.0f, isFunctional = true),
            KeySpec(','.code, ",", weight = 0.9f),
            KeySpec(KeyCode.SPACE, "Espacio", weight = 4.2f),
            KeySpec('.'.code, ".", weight = 0.9f),
            KeySpec(KeyCode.ENTER, "↵", weight = 1.65f, isFunctional = true, isAccent = true)
        )
    )
}
