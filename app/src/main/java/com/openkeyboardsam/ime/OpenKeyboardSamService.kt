package com.openkeyboardsam.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.openkeyboardsam.core.keyboard.layout.LayoutProvider
import com.openkeyboardsam.core.keyboard.model.KeyCode
import com.openkeyboardsam.core.keyboard.model.KeySpec
import com.openkeyboardsam.core.keyboard.model.KeyboardLanguage
import com.openkeyboardsam.core.keyboard.model.KeyboardMode
import com.openkeyboardsam.core.keyboard.model.KeyboardTheme
import com.openkeyboardsam.core.keyboard.model.LayoutSizingMode
import com.openkeyboardsam.core.keyboard.model.ShiftState
import com.openkeyboardsam.core.keyboard.view.OneUiKeyboardView
import com.openkeyboardsam.core.nlp.NlpEngine
import com.openkeyboardsam.feature.ai.AiResult
import com.openkeyboardsam.feature.ai.GeminiWritingAssistant
import com.openkeyboardsam.feature.ai.WritingAction
import com.openkeyboardsam.feature.clipboard.ClipboardRepository
import com.openkeyboardsam.feature.stylus.SPenAndDexDetector
import com.openkeyboardsam.feature.stylus.SPenHandwritingView
import com.openkeyboardsam.prefs.KeyboardPreferences
import com.openkeyboardsam.prefs.ThemeMode
import com.openkeyboardsam.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Main InputMethodService for OpenKeyboardSam.
 * Features:
 * - Zero-allocation Canvas One UI keyboard + Keys Cafe height & One-handed modes
 * - Native C++17 mmap NLP dictionary (ES/EN) + inline expression calculator
 * - Interactive S Pen Handwriting Canvas (Galaxy S22 Ultra)
 * - Android 11+ Autofill Inline Suggestions (Samsung Pass / Wallet)
 * - Persistent SQLite Clipboard + Gemini AI Writing Assist
 */
class OpenKeyboardSamService : InputMethodService(), OneUiKeyboardView.Listener {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var nlpEngine: NlpEngine
    private lateinit var clipboardRepo: ClipboardRepository
    private val geminiAssistant = GeminiWritingAssistant()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootContainer: LinearLayout? = null
    private var topBarContainer: LinearLayout? = null
    private var secondaryPanelContainer: LinearLayout? = null
    private var keyboardView: OneUiKeyboardView? = null

    private var currentLanguage = KeyboardLanguage.SPANISH
    private var currentMode = KeyboardMode.ALPHA
    private var shiftState = ShiftState.ON
    private val currentWord = StringBuilder()
    private var lastSpaceTapUptimeMs = 0L

    private var clipboardManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (!clipText.isNullOrBlank()) {
            clipboardRepo.saveClip(clipText)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        currentLanguage = prefs.language
        nlpEngine = NlpEngine(this).apply {
            loadLanguage(currentLanguage.code)
        }
        clipboardRepo = ClipboardRepository(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        val density = resources.displayMetrics.density
        val theme = resolveTheme()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.backgroundColor)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (42 * density).toInt()
            )
        }

        val secondaryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (215 * density).toInt()
            )
        }

        val kbView = OneUiKeyboardView(this).apply {
            listener = this@OpenKeyboardSamService
            hapticEnabled = prefs.hapticEnabled
            showAltLabels = prefs.showAltLabels
            swipeTypingEnabled = prefs.swipeTypingEnabled
            heightScale = prefs.keyboardHeightScale
            sizingMode = prefs.layoutSizingMode
            applyTheme(theme)
        }

        root.addView(topBar)
        root.addView(secondaryPanel)
        root.addView(kbView)

        rootContainer = root
        topBarContainer = topBar
        secondaryPanelContainer = secondaryPanel
        keyboardView = kbView

        refreshKeyboardLayout()
        showToolbarActions()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        val theme = resolveTheme()
        rootContainer?.setBackgroundColor(theme.backgroundColor)
        keyboardView?.apply {
            hapticEnabled = prefs.hapticEnabled
            showAltLabels = prefs.showAltLabels
            swipeTypingEnabled = prefs.swipeTypingEnabled
            heightScale = prefs.keyboardHeightScale
            sizingMode = if (SPenAndDexDetector.isDesktopOrDexMode(this@OpenKeyboardSamService)) {
                LayoutSizingMode.DEX_COMPACT
            } else {
                prefs.layoutSizingMode
            }
            applyTheme(theme)
        }
        updateAutoCapitalization()
        refreshKeyboardLayout()
        showToolbarActions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        currentWord.clear()
        closeSecondaryPanel()
        nlpEngine.trimMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            closeSecondaryPanel()
            nlpEngine.trimMemory()
        }
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        nlpEngine.release()
        clipboardRepo.close()
        super.onDestroy()
    }

    // --- Android 11+ Autofill Inline Suggestions (Samsung Pass / Wallet / Bitwarden) ---
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        val density = resources.displayMetrics.density
        val minSize = Size((64 * density).toInt(), (36 * density).toInt())
        val maxSize = Size((220 * density).toInt(), (40 * density).toInt())
        val style = InlineSuggestionUi.newStyleBuilder().build()
        val spec = InlinePresentationSpec.Builder(minSize, maxSize)
            .setStyle(UiVersions.newStylesBuilder().addStyle(style).build())
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(3)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) return false
        val bar = topBarContainer ?: return false
        bar.removeAllViews()

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (item in suggestions) {
            item.inflate(this, Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), mainExecutor) { view ->
                if (view != null) {
                    row.addView(view)
                }
            }
        }
        scroll.addView(row)
        bar.addView(scroll)
        return true
    }

    // --- OneUiKeyboardView.Listener ---
    override fun onKeyPress(key: KeySpec, isAlt: Boolean) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            KeyCode.SHIFT -> {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.ON
                    ShiftState.ON -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                keyboardView?.updateShiftState(shiftState)
            }
            KeyCode.BACKSPACE -> {
                if (currentWord.isNotEmpty()) {
                    currentWord.deleteCharAt(currentWord.length - 1)
                }
                ic.deleteSurroundingText(1, 0)
                updateAutoCapitalization()
                updateSuggestionsBar()
            }
            KeyCode.ENTER -> {
                commitCurrentWordIfNeeded()
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                updateAutoCapitalization()
            }
            KeyCode.SPACE -> {
                val now = SystemClock.uptimeMillis()
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (prefs.doubleSpacePeriod &&
                    now - lastSpaceTapUptimeMs < 320L &&
                    before.endsWith(" ") &&
                    before.length >= 2 &&
                    before[0].isLetterOrDigit()
                ) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceTapUptimeMs = 0L
                    updateAutoCapitalization()
                } else {
                    commitCurrentWordIfNeeded()
                    ic.commitText(" ", 1)
                    lastSpaceTapUptimeMs = now
                }
            }
            KeyCode.MODE_SYMBOLS -> {
                currentMode = KeyboardMode.SYMBOLS_1
                refreshKeyboardLayout()
            }
            KeyCode.MODE_SYMBOLS_2 -> {
                currentMode = KeyboardMode.SYMBOLS_2
                refreshKeyboardLayout()
            }
            KeyCode.MODE_ALPHA -> {
                currentMode = KeyboardMode.ALPHA
                refreshKeyboardLayout()
            }
            KeyCode.EMOJI -> toggleEmojiPanel()
            KeyCode.LANGUAGE_SWITCH -> switchLanguage()
            else -> {
                val ch = key.code.toChar()
                val text = if (ch.isLetter() && shiftState != ShiftState.OFF) {
                    ch.uppercase()
                } else {
                    ch.toString()
                }
                ic.commitText(text, 1)
                if (ch.isLetter()) {
                    currentWord.append(text)
                    updateSuggestionsBar()
                } else {
                    commitCurrentWordIfNeeded()
                }
                if (shiftState == ShiftState.ON) {
                    shiftState = ShiftState.OFF
                    keyboardView?.updateShiftState(shiftState)
                } else if (ch == '.' || ch == '!' || ch == '?') {
                    updateAutoCapitalization()
                }
            }
        }
    }

    override fun onTextInput(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        if (text.length == 1 && text[0].isLetter()) {
            currentWord.append(text)
            updateSuggestionsBar()
        } else {
            commitCurrentWordIfNeeded()
        }
    }

    override fun onSpacebarSwipe(deltaChars: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (deltaChars > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(deltaChars).coerceAtMost(5)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun onLanguageSwipe() {
        switchLanguage()
    }

    override fun onBackspaceWordDelete() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        if (before.isEmpty()) return
        var count = 0
        var i = before.length - 1
        while (i >= 0 && before[i].isWhitespace()) {
            count++
            i--
        }
        while (i >= 0 && !before[i].isWhitespace()) {
            count++
            i--
        }
        if (count > 0) {
            ic.deleteSurroundingText(count, 0)
            currentWord.clear()
            updateAutoCapitalization()
            showToolbarActions()
        }
    }

    override fun onSwipeWord(swipedKeys: List<KeySpec>) {
        val seq = swipedKeys.mapNotNull {
            if (it.label.length == 1 && it.label[0].isLetter()) it.label[0] else null
        }.joinToString("")
        if (seq.length < 2) return

        val matches = nlpEngine.decodeSwipe(seq, 3)
        val best = matches.firstOrNull() ?: return
        val ic = currentInputConnection ?: return
        val out = if (shiftState != ShiftState.OFF) {
            best.replaceFirstChar { it.uppercase() }
        } else {
            best
        }
        ic.commitText("$out ", 1)
        nlpEngine.learnWord(best)
        currentWord.clear()
        if (shiftState == ShiftState.ON) {
            shiftState = ShiftState.OFF
            keyboardView?.updateShiftState(shiftState)
        }
        showToolbarActions()
    }

    private fun updateAutoCapitalization() {
        if (!prefs.autoCapitalize || shiftState == ShiftState.CAPS_LOCK) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(3, 0)?.toString().orEmpty()
        val shouldCap = before.isEmpty() ||
            before.endsWith(". ") ||
            before.endsWith("! ") ||
            before.endsWith("? ") ||
            before.endsWith("\n")
        val nextState = if (shouldCap) ShiftState.ON else ShiftState.OFF
        if (shiftState != nextState) {
            shiftState = nextState
            keyboardView?.updateShiftState(shiftState)
        }
    }

    private fun commitCurrentWordIfNeeded() {
        if (currentWord.isNotEmpty()) {
            nlpEngine.learnWord(currentWord.toString())
            currentWord.clear()
        }
        showToolbarActions()
    }

    private fun switchLanguage() {
        currentLanguage = currentLanguage.next()
        prefs.language = currentLanguage
        nlpEngine.loadLanguage(currentLanguage.code)
        refreshKeyboardLayout()
        showToolbarActions()
        Toast.makeText(this, currentLanguage.displayName, Toast.LENGTH_SHORT).show()
    }

    private fun refreshKeyboardLayout() {
        val rows = LayoutProvider.getRows(currentLanguage, currentMode, prefs.showNumberRow)
        keyboardView?.setKeyboard(rows, shiftState)
    }

    private fun updateSuggestionsBar() {
        val prefix = currentWord.toString()
        if (prefix.isEmpty()) {
            showToolbarActions()
            return
        }
        val bar = topBarContainer ?: return
        bar.removeAllViews()
        val theme = resolveTheme()
        val suggestions = nlpEngine.predict(prefix, 3)

        if (suggestions.isEmpty()) {
            showToolbarActions()
            return
        }

        for (word in suggestions) {
            val formatted = if (prefix[0].isUpperCase()) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word
            }
            val btn = TextView(this).apply {
                text = formatted
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(theme.keyTextColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    val ic = currentInputConnection ?: return@setOnClickListener
                    ic.deleteSurroundingText(prefix.length, 0)
                    ic.commitText("$formatted ", 1)
                    nlpEngine.learnWord(word)
                    currentWord.clear()
                    showToolbarActions()
                }
            }
            bar.addView(btn)
        }
    }

    private fun showToolbarActions() {
        val bar = topBarContainer ?: return
        bar.removeAllViews()
        val theme = resolveTheme()

        fun actionButton(label: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(theme.keyTextColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { onClick() }
            }
        }

        bar.addView(actionButton("✨ IA") { toggleAiPanel() })
        bar.addView(actionButton("✍️ S Pen") { toggleSPenPanel() })
        bar.addView(actionButton("📋") { toggleClipboardPanel() })
        bar.addView(actionButton("☺") { toggleEmojiPanel() })
        bar.addView(actionButton("🎤") { triggerVoiceInput() })
        bar.addView(actionButton("📐") { cycleOneHandedMode() })
        bar.addView(actionButton(currentLanguage.code.uppercase()) { switchLanguage() })
        bar.addView(actionButton("⚙") {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        })
    }

    private fun cycleOneHandedMode() {
        val next = when (prefs.layoutSizingMode) {
            LayoutSizingMode.FULL -> LayoutSizingMode.ONE_HANDED_RIGHT
            LayoutSizingMode.ONE_HANDED_RIGHT -> LayoutSizingMode.ONE_HANDED_LEFT
            LayoutSizingMode.ONE_HANDED_LEFT -> LayoutSizingMode.FULL
            LayoutSizingMode.DEX_COMPACT -> LayoutSizingMode.FULL
        }
        prefs.layoutSizingMode = next
        keyboardView?.sizingMode = next
    }

    private fun toggleSPenPanel() {
        val panel = secondaryPanelContainer ?: return
        if (panel.visibility == View.VISIBLE && panel.tag == "spen") {
            closeSecondaryPanel()
            return
        }
        panel.removeAllViews()
        panel.tag = "spen"
        panel.visibility = View.VISIBLE
        keyboardView?.visibility = View.GONE

        val theme = resolveTheme()
        val density = resources.displayMetrics.density
        val pad = SPenHandwritingView(this).apply {
            setThemeColors(theme.accentKeyColor, theme.altTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (165 * density).toInt()
            )
            listener = object : SPenHandwritingView.Listener {
                override fun onRecognizedText(text: String) {
                    currentInputConnection?.commitText(text, 1)
                }

                override fun onSpaceRequested() {
                    currentInputConnection?.commitText(" ", 1)
                }

                override fun onBackspaceRequested() {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }

                override fun onCloseRequested() {
                    closeSecondaryPanel()
                }
            }
        }
        panel.addView(pad)
        panel.addView(Button(this).apply {
            text = "Volver al teclado QWERTY"
            setOnClickListener { closeSecondaryPanel() }
        })
    }

    private fun toggleEmojiPanel() {
        val panel = secondaryPanelContainer ?: return
        if (panel.visibility == View.VISIBLE && panel.tag == "emoji") {
            closeSecondaryPanel()
            return
        }
        panel.removeAllViews()
        panel.tag = "emoji"
        panel.visibility = View.VISIBLE
        keyboardView?.visibility = View.GONE

        val emojis = listOf(
            "😀", "😂", "🥹", "😍", "🥰", "😎", "🤔", "🙌",
            "👍", "👏", "🔥", "✨", "❤️", "💯", "🎉", "🙏",
            "😅", "😭", "🤯", "🫡", "🤝", "⚡", "🚀", "✅",
            "🇲🇽", "🇪🇸", "🇦🇷", "🇨🇴", "🇺🇲", "📱", "💻", "🎧"
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }
        val theme = resolveTheme()
        for (chunk in emojis.chunked(8)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for (emoji in chunk) {
                val tv = TextView(this).apply {
                    text = emoji
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setPadding(6, 12, 6, 12)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { currentInputConnection?.commitText(emoji, 1) }
                }
                row.addView(tv)
            }
            grid.addView(row)
        }
        val backBtn = Button(this).apply {
            text = "Volver al teclado"
            setTextColor(theme.keyTextColor)
            setOnClickListener { closeSecondaryPanel() }
        }
        grid.addView(backBtn)
        panel.addView(ScrollView(this).apply { addView(grid) })
    }

    private fun toggleClipboardPanel() {
        val panel = secondaryPanelContainer ?: return
        if (panel.visibility == View.VISIBLE && panel.tag == "clipboard") {
            closeSecondaryPanel()
            return
        }
        panel.removeAllViews()
        panel.tag = "clipboard"
        panel.visibility = View.VISIBLE
        keyboardView?.visibility = View.GONE

        val theme = resolveTheme()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }
        val clips = clipboardRepo.getRecentClips(20)
        if (clips.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Portapapeles vacío (copia cualquier texto para guardarlo aquí)"
                setTextColor(theme.altTextColor)
                setPadding(12, 24, 12, 24)
            })
        } else {
            for (clip in clips) {
                val row = TextView(this).apply {
                    text = (if (clip.isPinned) "📌 " else "") + clip.text.take(90)
                    setTextColor(theme.keyTextColor)
                    textSize = 14f
                    setPadding(16, 18, 16, 18)
                    setOnClickListener {
                        currentInputConnection?.commitText(clip.text, 1)
                        closeSecondaryPanel()
                    }
                    setOnLongClickListener {
                        clipboardRepo.togglePin(clip.id)
                        toggleClipboardPanel()
                        true
                    }
                }
                list.addView(row)
            }
        }
        val backBtn = Button(this).apply {
            text = "Cerrar Portapapeles"
            setOnClickListener { closeSecondaryPanel() }
        }
        list.addView(backBtn)
        panel.addView(ScrollView(this).apply { addView(list) })
    }

    private fun toggleAiPanel() {
        val panel = secondaryPanelContainer ?: return
        if (panel.visibility == View.VISIBLE && panel.tag == "ai") {
            closeSecondaryPanel()
            return
        }
        panel.removeAllViews()
        panel.tag = "ai"
        panel.visibility = View.VISIBLE
        keyboardView?.visibility = View.GONE

        val theme = resolveTheme()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        val statusView = TextView(this).apply {
            text = "✨ Gemini Writing Assist (S22 Ultra)"
            setTextColor(theme.keyTextColor)
            textSize = 14f
            setPadding(8, 8, 8, 12)
        }
        container.addView(statusView)

        val actions = listOf(
            "Corregir ortografía y gramática" to WritingAction.FIX_GRAMMAR,
            "Tono Profesional" to WritingAction.TONE_PROFESSIONAL,
            "Tono Casual" to WritingAction.TONE_CASUAL,
            "Tono Redes Sociales" to WritingAction.TONE_SOCIAL,
            "Traducir a Inglés" to WritingAction.TRANSLATE_TO_EN,
            "Traducir a Español" to WritingAction.TRANSLATE_TO_ES
        )

        for ((title, action) in actions) {
            val btn = Button(this).apply {
                text = title
                setOnClickListener {
                    val ic = currentInputConnection ?: return@setOnClickListener
                    val textBefore = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
                    if (textBefore.isBlank()) {
                        statusView.text = "Escribe algo primero para procesarlo con Gemini."
                        return@setOnClickListener
                    }
                    statusView.text = "Procesando con Gemini..."
                    serviceScope.launch {
                        when (val res = geminiAssistant.transformText(prefs.geminiApiKey, textBefore, action)) {
                            is AiResult.Success -> {
                                ic.deleteSurroundingText(textBefore.length, 0)
                                ic.commitText(res.text, 1)
                                closeSecondaryPanel()
                            }
                            is AiResult.Error -> {
                                statusView.text = res.message
                            }
                        }
                    }
                }
            }
            container.addView(btn)
        }

        container.addView(Button(this).apply {
            text = "Volver al teclado"
            setOnClickListener { closeSecondaryPanel() }
        })
        panel.addView(ScrollView(this).apply { addView(container) })
    }

    private fun closeSecondaryPanel() {
        secondaryPanelContainer?.apply {
            removeAllViews()
            visibility = View.GONE
            tag = null
        }
        keyboardView?.visibility = View.VISIBLE
    }

    private fun triggerVoiceInput() {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLanguage == KeyboardLanguage.SPANISH) "es-ES" else "en-US")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, "Entrada de voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveTheme(): KeyboardTheme {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return when (prefs.themeMode) {
            ThemeMode.ONE_UI_LIGHT -> KeyboardTheme.oneUiLight()
            ThemeMode.ONE_UI_AMOLED_DARK -> KeyboardTheme.oneUiAmoledDark()
            ThemeMode.DYNAMIC -> KeyboardTheme.oneUiDynamic(this, isNight)
            ThemeMode.AUTO_SYSTEM -> if (isNight) KeyboardTheme.oneUiAmoledDark() else KeyboardTheme.oneUiLight()
        }
    }
}
