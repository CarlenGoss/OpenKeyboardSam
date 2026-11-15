package com.openkeyboardsam.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Debug
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

/**
 * Samsung One UI 6.1 Settings Screen with large collapsible-style title header,
 * rounded 24dp grouped cards, and live RAM profiler.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KeyboardPreferences
    private var ramStatusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isNight) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
        val cardColor = if (isNight) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val textColor = if (isNight) 0xFFF5F5F7.toInt() else 0xFF191C20.toInt()
        val subTextColor = if (isNight) 0xFF8E8E93.toInt() else 0xFF6E6E73.toInt()
        val accentColor = 0xFF0381FE.toInt()
        val d = resources.displayMetrics.density

        fun cardDrawable(color: Int = cardColor, radiusDp: Float = 22f): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusDp * d
                setColor(color)
            }
        }

        fun createSectionCard(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardDrawable()
                setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (14 * d).toInt()
                layoutParams = lp
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding((16 * d).toInt(), (36 * d).toInt(), (16 * d).toInt(), (40 * d).toInt())
        }

        // One UI Large Header
        root.addView(TextView(this).apply {
            text = "Teclado Samsung\nOpenKeyboardSam"
            textSize = 28f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(textColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, (12 * d).toInt(), 0, (6 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "Versión 0.3.0 • Edición Galaxy S22 Ultra One UI 6.1"
            textSize = 13f
            setTextColor(subTextColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, (24 * d).toInt())
        })

        // Card 1: RAM Profiler
        val ramCard = createSectionCard()
        ramStatusText = TextView(this).apply {
            textSize = 13.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(accentColor)
        }
        ramCard.addView(ramStatusText)
        root.addView(ramCard)
        updateRamStats()

        // Card 2: Quick Setup
        val setupCard = createSectionCard()
        setupCard.addView(TextView(this).apply {
            text = "Activación rápida"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (10 * d).toInt())
        })
        setupCard.addView(Button(this).apply {
            text = "1. Activar en Ajustes de Teclado"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        setupCard.addView(Button(this).apply {
            text = "2. Elegir OpenKeyboardSam por defecto"
            setOnClickListener {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        })
        setupCard.addView(EditText(this).apply {
            hint = "Toca aquí para probar el teclado One UI..."
            setTextColor(textColor)
            setHintTextColor(subTextColor)
            setPadding((8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt(), (10 * d).toInt())
        })
        root.addView(setupCard)

        // Card 3: Estilo y Diseño (Keys Cafe)
        val styleCard = createSectionCard()
        styleCard.addView(TextView(this).apply {
            text = "Estilo y Diseño (One UI & Keys Cafe)"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (8 * d).toInt())
        })

        val themeGroup = RadioGroup(this)
        val themes = listOf(
            "Automático (Claro / Oscuro según One UI)" to ThemeMode.AUTO_SYSTEM,
            "One UI Claro Original (#E5E7EC)" to ThemeMode.ONE_UI_LIGHT,
            "One UI Oscuro Original (#17171A)" to ThemeMode.ONE_UI_AMOLED_DARK,
            "Color Dinámico (Material You)" to ThemeMode.DYNAMIC
        )
        for ((label, mode) in themes) {
            themeGroup.addView(RadioButton(this).apply {
                text = label
                setTextColor(textColor)
                isChecked = prefs.themeMode == mode
                setOnClickListener { prefs.themeMode = mode }
            })
        }
        styleCard.addView(themeGroup)

        val scaleLabel = TextView(this).apply {
            text = "Altura del teclado: ${(prefs.keyboardHeightScale * 100).toInt()}%"
            textSize = 14f
            setTextColor(textColor)
            setPadding(0, (14 * d).toInt(), 0, (6 * d).toInt())
        }
        styleCard.addView(scaleLabel)
        styleCard.addView(SeekBar(this).apply {
            max = 50
            progress = ((prefs.keyboardHeightScale - 0.80f) * 100).toInt().coerceIn(0, 50)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    val scale = 0.80f + (p / 100f)
                    prefs.keyboardHeightScale = scale
                    scaleLabel.text = "Altura del teclado: ${(scale * 100).toInt()}%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        })
        root.addView(styleCard)

        // Card 4: Idiomas y Escritura Inteligente
        val typingCard = createSectionCard()
        typingCard.addView(TextView(this).apply {
            text = "Idiomas y Escritura inteligente"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (8 * d).toInt())
        })
        val langGroup = RadioGroup(this)
        val langs = listOf(
            "Español (ES) • QWERTY con Ñ" to KeyboardLanguage.SPANISH,
            "English (US) • QWERTY" to KeyboardLanguage.ENGLISH
        )
        for ((label, lang) in langs) {
            langGroup.addView(RadioButton(this).apply {
                text = label
                setTextColor(textColor)
                isChecked = prefs.language == lang
                setOnClickListener { prefs.language = lang }
            })
        }
        typingCard.addView(langGroup)

        fun addSwitch(parent: LinearLayout, title: String, initial: Boolean, onChange: (Boolean) -> Unit) {
            parent.addView(Switch(this).apply {
                text = title
                setTextColor(textColor)
                isChecked = initial
                setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
                setOnCheckedChangeListener { _, checked -> onChange(checked) }
            })
        }

        addSwitch(typingCard, "Teclas numéricas en fila superior", prefs.showNumberRow) { prefs.showNumberRow = it }
        addSwitch(typingCard, "Caracteres alternativos en las teclas", prefs.showAltLabels) { prefs.showAltLabels = it }
        addSwitch(typingCard, "Mayúsculas automáticas", prefs.autoCapitalize) { prefs.autoCapitalize = it }
        addSwitch(typingCard, "Doble pulsación en espacio para punto", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        addSwitch(typingCard, "Deslizar para escribir (Swipe)", prefs.swipeTypingEnabled) { prefs.swipeTypingEnabled = it }
        addSwitch(typingCard, "Respuesta táctil (Vibración S22 Ultra)", prefs.hapticEnabled) { prefs.hapticEnabled = it }
        root.addView(typingCard)

        // Card 5: Galaxy AI (Gemini)
        val aiCard = createSectionCard()
        aiCard.addView(TextView(this).apply {
            text = "✨ Galaxy AI Writing Assist (Gemini API)"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(textColor)
            setPadding(0, 0, 0, (6 * d).toInt())
        })
        val apiInput = EditText(this).apply {
            hint = "Ingresa tu Gemini API Key"
            setTextColor(textColor)
            setHintTextColor(subTextColor)
            setText(prefs.geminiApiKey)
        }
        aiCard.addView(apiInput)
        aiCard.addView(Button(this).apply {
            text = "Guardar clave de Gemini"
            setOnClickListener {
                prefs.geminiApiKey = apiInput.text.toString()
                Toast.makeText(this@SettingsActivity, "Clave guardada correctamente", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(aiCard)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        })
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
            "⚡ Memoria en uso (PSS): %.1f MB  •  Motor C++: %.1f MB  •  UI: %.1f MB",
            totalPssMb, nativePssMb, dalvikPssMb
        )
    }
}
