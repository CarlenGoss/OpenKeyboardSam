package com.openkeyboardsam.core.keyboard.layout

import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.core.keyboard.model.KeyboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutProviderTest {

    @Test
    fun spanishAlphaIncludesEnyeAndNumberRow() {
        val rows = LayoutProvider.getRows(
            language = KeyboardLanguage.SPANISH,
            mode = KeyboardMode.ALPHA,
            showNumberRow = true
        )
        assertEquals(5, rows.size)
        val row2Labels = rows[2].map { it.label }
        assertTrue(row2Labels.contains("ñ"))
    }

    @Test
    fun englishAlphaOmitsEnyeAndHidesNumberRowWhenDisabled() {
        val rows = LayoutProvider.getRows(
            language = KeyboardLanguage.ENGLISH,
            mode = KeyboardMode.ALPHA,
            showNumberRow = false
        )
        assertEquals(4, rows.size)
        val row1Labels = rows[1].map { it.label }
        assertTrue(!row1Labels.contains("ñ"))
    }
}
