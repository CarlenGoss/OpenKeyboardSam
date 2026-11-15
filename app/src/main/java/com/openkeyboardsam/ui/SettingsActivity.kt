package com.openkeyboardsam.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.prefs.KeyboardPreferences
import com.openkeyboardsam.prefs.ThemeMode

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
        }

        root.addView(TextView(this).apply {
            text = "OpenKeyboardSam"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Teclado Open-Source estilo One UI (Galaxy S22 Ultra) • Ultra-bajo consumo de RAM"
            textSize = 13f
            setPadding(0, 4, 0, 24)
        })

        // Step 1 & 2: Enable & Select IME
        root.addView(Button(this).apply {
            text = "1. Activar OpenKeyboardSam en Ajustes"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "2. Seleccionar como teclado predeterminado"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        })

        // Theme selection
        root.addView(TextView(this).apply {
            text = "Tema One UI"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })
        val themeGroup = RadioGroup(this)
        val themes = listOf(
            "Automático (Sistema)" to ThemeMode.AUTO_SYSTEM,
            "One UI Claro" to ThemeMode.ONE_UI_LIGHT,
            "One UI AMOLED Oscuro" to ThemeMode.ONE_UI_AMOLED_DARK,
            "Dynamic Color (Material You)" to ThemeMode.DYNAMIC
        )
        for ((label, mode) in themes) {
            val rb = RadioButton(this).apply {
                text = label
                isChecked = prefs.themeMode == mode
                setOnClickListener { prefs.themeMode = mode }
            }
            themeGroup.addView(rb)
        }
        root.addView(themeGroup)

        // Language selection
        root.addView(TextView(this).apply {
            text = "Idioma principal"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })
        val langGroup = RadioGroup(this)
        val langs = listOf(
            "Español (QWERTY + Ñ)" to KeyboardLanguage.SPANISH,
            "English (QWERTY)" to KeyboardLanguage.ENGLISH
        )
        for ((label, lang) in langs) {
            val rb = RadioButton(this).apply {
                text = label
                isChecked = prefs.language == lang
                setOnClickListener { prefs.language = lang }
            }
            langGroup.addView(rb)
        }
        root.addView(langGroup)

        // Toggles
        fun addSwitch(title: String, initial: Boolean, onChange: (Boolean) -> Unit) {
            root.addView(Switch(this).apply {
                text = title
                isChecked = initial
                setPadding(0, 16, 0, 16)
                setOnCheckedChangeListener { _, checked -> onChange(checked) }
            })
        }

        addSwitch("Mostrar fila de números", prefs.showNumberRow) { prefs.showNumberRow = it }
        addSwitch("Mostrar caracteres alternativos", prefs.showAltLabels) { prefs.showAltLabels = it }
        addSwitch("Vibración háptica (S22 Ultra)", prefs.hapticEnabled) { prefs.hapticEnabled = it }
        addSwitch("Escritura deslizada (Swipe)", prefs.swipeTypingEnabled) { prefs.swipeTypingEnabled = it }

        // Gemini API Key
        root.addView(TextView(this).apply {
            text = "Gemini AI Writing Assist (API Key opcional)"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        })
        val apiInput = EditText(this).apply {
            hint = "Pega tu Gemini API Key (BYOK)"
            setText(prefs.geminiApiKey)
        }
        root.addView(apiInput)
        root.addView(Button(this).apply {
            text = "Guardar Gemini API Key"
            setOnClickListener {
                prefs.geminiApiKey = apiInput.text.toString()
                Toast.makeText(this@SettingsActivity, "API Key guardada", Toast.LENGTH_SHORT).show()
            }
        })

        // Test box
        root.addView(EditText(this).apply {
            hint = "Prueba aquí OpenKeyboardSam..."
            setPadding(0, 32, 0, 16)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
