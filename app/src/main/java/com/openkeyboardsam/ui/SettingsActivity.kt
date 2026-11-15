package com.openkeyboardsam.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.prefs.KeyboardPreferences
import com.openkeyboardsam.prefs.ThemeMode

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences
    private var ramStatusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 48, 44, 64)
        }

        root.addView(TextView(this).apply {
            text = "OpenKeyboardSam v0.2.0"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Teclado Open-Source One UI (Galaxy S22 Ultra) • Motor C++17 mmap + Canvas"
            textSize = 13f
            setPadding(0, 4, 0, 16)
        })

        // Live RAM Monitor Card
        ramStatusText = TextView(this).apply {
            textSize = 14f
            setPadding(24, 20, 24, 20)
            setBackgroundColor(0x220381FE)
        }
        root.addView(ramStatusText)
        updateRamStats()

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
            "Español (QWERTY + Ñ + Acentos)" to KeyboardLanguage.SPANISH,
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

        // Keys Cafe Height Scale Slider
        val scaleLabel = TextView(this).apply {
            text = "Altura del teclado (Keys Cafe): ${(prefs.keyboardHeightScale * 100).toInt()}%"
            textSize = 15f
            setPadding(0, 24, 0, 8)
        }
        root.addView(scaleLabel)
        root.addView(SeekBar(this).apply {
            max = 50 // 80% to 130%
            progress = ((prefs.keyboardHeightScale - 0.80f) * 100).toInt().coerceIn(0, 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    val scale = 0.80f + (p / 100f)
                    prefs.keyboardHeightScale = scale
                    scaleLabel.text = "Altura del teclado (Keys Cafe): ${(scale * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })

        // Toggles
        fun addSwitch(title: String, initial: Boolean, onChange: (Boolean) -> Unit) {
            root.addView(Switch(this).apply {
                text = title
                isChecked = initial
                setPadding(0, 14, 0, 14)
                setOnCheckedChangeListener { _, checked -> onChange(checked) }
            })
        }

        addSwitch("Mostrar fila de números", prefs.showNumberRow) { prefs.showNumberRow = it }
        addSwitch("Mostrar caracteres alternativos", prefs.showAltLabels) { prefs.showAltLabels = it }
        addSwitch("Mayúscula automática al iniciar oración", prefs.autoCapitalize) { prefs.autoCapitalize = it }
        addSwitch("Doble espacio inserta punto (\". \")", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        addSwitch("Vibración háptica lineal (S22 Ultra)", prefs.hapticEnabled) { prefs.hapticEnabled = it }
        addSwitch("Escritura deslizada (Swipe / Glide)", prefs.swipeTypingEnabled) { prefs.swipeTypingEnabled = it }

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
            hint = "Prueba aquí OpenKeyboardSam (mantén pulsada una letra para acentos, o usa S Pen)..."
            setPadding(0, 32, 0, 16)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        updateRamStats()
    }

    private fun updateRamStats() {
        val memInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfo)
        val totalPssMb = memInfo.totalPss / 1024.0
        val nativePssMb = memInfo.nativePss / 1024.0
        val dalvikPssMb = memInfo.dalvikPss / 1024.0
        ramStatusText?.text = String.format(
            "⚡ Consumo RAM actual (PSS): %.1f MB (Nativo C++: %.1f MB | Kotlin/ART: %.1f MB) • Meta: < 45 MB",
            totalPssMb, nativePssMb, dalvikPssMb
        )
    }
}
