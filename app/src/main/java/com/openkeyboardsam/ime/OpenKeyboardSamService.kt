package com.openkeyboardsam.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.openkeyboardsam.core.keyboard.view.OneUiToolbarView
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
 * Main InputMethodService for OpenKeyboardSam (One UI 6.1 Edition).
 */
class OpenKeyboardSamService :
    InputMethodService(),
    OneUiKeyboardView.Listener,
    OneUiToolbarView.Listener {

    private lateinit var prefs: KeyboardPreferences
    private lateinit var nlpEngine: NlpEngine
    private lateinit var clipboardRepo: ClipboardRepository
    private val geminiAssistant = GeminiWritingAssistant()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootContainer: LinearLayout? = null
    private var autofillStripContainer: LinearLayout? = null
    private var toolbarView: OneUiToolbarView? = null
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

        val autofillStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            gravity = Gravity.CENTER_VERTICAL
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (40 * density).toInt()
            )
        }

        val tbView = OneUiToolbarView(this).apply {
            listener = this@OpenKeyboardSamService
            applyTheme(theme)
        }

        val secondaryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (235 * density).toInt()
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

        root.addView(autofillStrip)
        root.addView(tbView)
        root.addView(secondaryPanel)
        root.addView(kbView)

        rootContainer = root
        autofillStripContainer = autofillStrip
        toolbarView = tbView
        secondaryPanelContainer = secondaryPanel
        keyboardView = kbView

        refreshKeyboardLayout()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        val theme = resolveTheme()
        rootContainer?.setBackgroundColor(theme.backgroundColor)
        autofillStripContainer?.visibility = View.GONE
        toolbarView?.apply {
            visibility = View.VISIBLE
            applyTheme(theme)
            setSuggestions(emptyList())
            setActiveAction(null)
        }
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

    // --- Android 11+ Autofill Inline Suggestions (Samsung Pass / Wallet) ---
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        val density = resources.displayMetrics.density
        val minSize = Size((64 * density).toInt(), (34 * density).toInt())
        val maxSize = Size((220 * density).toInt(), (38 * density).toInt())
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
        val strip = autofillStripContainer ?: return false
        if (suggestions.isEmpty()) {
            strip.visibility = View.GONE
            toolbarView?.visibility = View.VISIBLE
            return false
        }
        strip.removeAllViews()
        strip.visibility = View.VISIBLE
        toolbarView?.visibility = View.GONE

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
        strip.addView(scroll)
        return true
    }

    // --- OneUiToolbarView.Listener ---
    override fun onActionClicked(action: OneUiToolbarView.Action) {
        when (action) {
            OneUiToolbarView.Action.GALAXY_AI -> toggleAiPanel()
            OneUiToolbarView.Action.EMOJI -> toggleEmojiPanel()
            OneUiToolbarView.Action.CLIPBOARD -> toggleClipboardPanel()
            OneUiToolbarView.Action.SPEN -> toggleSPenPanel()
            OneUiToolbarView.Action.VOICE -> triggerVoiceInput()
            OneUiToolbarView.Action.ONE_HANDED -> cycleOneHandedMode()
            OneUiToolbarView.Action.SETTINGS -> {
                val intent = Intent(this, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    override fun onSuggestionClicked(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord.toString()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }
        ic.commitText("$word ", 1)
        nlpEngine.learnWord(word)
        currentWord.clear()
        toolbarView?.setSuggestions(emptyList())
        updateAutoCapitalization()
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
            toolbarView?.setSuggestions(emptyList())
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
        toolbarView?.setSuggestions(emptyList())
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
        toolbarView?.setSuggestions(emptyList())
    }

    private fun switchLanguage() {
        currentLanguage = currentLanguage.next()
        prefs.language = currentLanguage
        nlpEngine.loadLanguage(currentLanguage.code)
        refreshKeyboardLayout()
    }

    private fun refreshKeyboardLayout() {
        val rows = LayoutProvider.getRows(currentLanguage, currentMode, prefs.showNumberRow)
        keyboardView?.setKeyboard(rows, shiftState)
    }

    private fun updateSuggestionsBar() {
        val prefix = currentWord.toString()
        if (prefix.isEmpty()) {
            toolbarView?.setSuggestions(emptyList())
            return
        }
        val raw = nlpEngine.predict(prefix, 3)
        val formatted = raw.map { word ->
            if (prefix[0].isUpperCase()) word.replaceFirstChar { it.uppercase() } else word
        }
        toolbarView?.setSuggestions(formatted)
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

    // --- Styled One UI Panels ---

    private fun createRoundedCard(bgColor: Int, cornerDp: Float = 14f, strokeColor: Int? = null): GradientDrawable {
        val d = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerDp * d
            setColor(bgColor)
            if (strokeColor != null) {
                setStroke((1f * d).toInt(), strokeColor)
            }
        }
    }

    private fun createPanelHeader(title: String, theme: KeyboardTheme): View {
        val d = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())

            addView(TextView(context).apply {
                text = title
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(theme.keyTextColor)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(context).apply {
                text = "⌨️ Teclado"
                textSize = 12.5f
                setTextColor(theme.accentKeyColor)
                background = createRoundedCard(theme.keyColor, 16f)
                setPadding((12 * d).toInt(), (5 * d).toInt(), (12 * d).toInt(), (5 * d).toInt())
                setOnClickListener { closeSecondaryPanel() }
            })
        }
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
        toolbarView?.setActiveAction(OneUiToolbarView.Action.SPEN)

        val theme = resolveTheme()
        val d = resources.displayMetrics.density
        panel.addView(createPanelHeader("✍️ Escritura a mano con S Pen", theme))

        val padContainer = LinearLayout(this).apply {
            background = createRoundedCard(theme.keyColor, 16f, theme.popupBorderColor)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (175 * d).toInt()
            )
            lp.setMargins((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            layoutParams = lp
        }

        val pad = SPenHandwritingView(this).apply {
            setThemeColors(theme.accentKeyColor, theme.altTextColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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
        padContainer.addView(pad)
        panel.addView(padContainer)
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
        toolbarView?.setActiveAction(OneUiToolbarView.Action.EMOJI)

        val theme = resolveTheme()
        val d = resources.displayMetrics.density
        panel.addView(createPanelHeader("😊 Emojis y Símbolos", theme))

        val emojis = listOf(
            "😀", "😂", "🤣", "🥹", "😍", "🥰", "😘", "😎",
            "🤔", "🫡", "🙌", "👍", "👏", "🙏", "🔥", "✨",
            "❤️", "🩷", "💯", "🎉", "🥳", "🤩", "😅", "😭",
            "🤯", "😡", "🤝", "💪", "⚡", "🚀", "✅", "❌",
            "🌟", "💎", "🎵", "📸", "📱", "💻", "⌚", "☕",
            "🇲🇽", "🇪🇸", "🇦🇷", "🇨🇴", "🇨🇱", "🇵🇪", "🇻🇪", "🇺🇸"
        )
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
        }
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
                    textSize = 23f
                    gravity = Gravity.CENTER
                    setPadding(4, (8 * d).toInt(), 4, (8 * d).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { currentInputConnection?.commitText(emoji, 1) }
                }
                row.addView(tv)
            }
            grid.addView(row)
        }
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
        toolbarView?.setActiveAction(OneUiToolbarView.Action.CLIPBOARD)

        val theme = resolveTheme()
        val d = resources.displayMetrics.density
        panel.addView(createPanelHeader("📋 Portapapeles One UI (mantén para fijar 📌)", theme))

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
        }
        val clips = clipboardRepo.getRecentClips(24)
        if (clips.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "El portapapeles está vacío.\nLos textos que copies aparecerán aquí como tarjetas."
                gravity = Gravity.CENTER
                setTextColor(theme.altTextColor)
                textSize = 13.5f
                setPadding(24, (36 * d).toInt(), 24, (36 * d).toInt())
            })
        } else {
            for (clip in clips) {
                val card = TextView(this).apply {
                    text = (if (clip.isPinned) "📌 " else "") + clip.text.take(110)
                    setTextColor(theme.keyTextColor)
                    textSize = 13.5f
                    background = createRoundedCard(theme.keyColor, 12f, theme.popupBorderColor)
                    setPadding((14 * d).toInt(), (11 * d).toInt(), (14 * d).toInt(), (11 * d).toInt())
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = (7 * d).toInt()
                    layoutParams = lp
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
                list.addView(card)
            }
        }
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
        toolbarView?.setActiveAction(OneUiToolbarView.Action.GALAXY_AI)

        val theme = resolveTheme()
        val d = resources.displayMetrics.density
        panel.addView(createPanelHeader("✨ Galaxy AI • Asistente de Escritura (Gemini)", theme))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (4 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
        }

        val statusView = TextView(this).apply {
            text = "Selecciona una acción para transformar el texto que estás escribiendo:"
            setTextColor(theme.altTextColor)
            textSize = 12.5f
            setPadding((4 * d).toInt(), 0, (4 * d).toInt(), (8 * d).toInt())
        }
        container.addView(statusView)

        val actions = listOf(
            "✓  Ortografía y gramática" to WritingAction.FIX_GRAMMAR,
            "👔  Tono Profesional" to WritingAction.TONE_PROFESSIONAL,
            "😊  Tono Casual y Cercano" to WritingAction.TONE_CASUAL,
            "🔥  Tono para Redes Sociales" to WritingAction.TONE_SOCIAL,
            "🇺🇸  Traducir al Inglés" to WritingAction.TRANSLATE_TO_EN,
            "🇪🇸  Traducir al Español" to WritingAction.TRANSLATE_TO_ES
        )

        for (pair in actions.chunked(2)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for ((title, action) in pair) {
                val pill = TextView(this).apply {
                    text = title
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(theme.keyTextColor)
                    background = createRoundedCard(theme.keyColor, 12f, theme.popupBorderColor)
                    setPadding((10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt())
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                    layoutParams = lp
                    setOnClickListener {
                        val ic = currentInputConnection ?: return@setOnClickListener
                        val textBefore = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
                        if (textBefore.isBlank()) {
                            statusView.text = "⚠️ Escribe una frase primero para que Galaxy AI la procese."
                            return@setOnClickListener
                        }
                        statusView.text = "✨ Procesando con Gemini AI..."
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
                row.addView(pill)
            }
            container.addView(row)
        }

        panel.addView(ScrollView(this).apply { addView(container) })
    }

    private fun closeSecondaryPanel() {
        secondaryPanelContainer?.apply {
            removeAllViews()
            visibility = View.GONE
            tag = null
        }
        toolbarView?.setActiveAction(null)
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
            Toast.makeText(this, "Entrada de voz no disponible", Toast.LENGTH_SHORT).show()
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
