package com.openkeyboardsam.core.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.openkeyboardsam.core.keyboard.model.KeyboardTheme

/**
 * Authentic Samsung One UI 6.1 Top Toolbar & Candidate Strip View.
 * Renders either:
 * 1. The 3-slot predictive text bar with thin vertical dividers (left | center bold accent | right), or
 * 2. The Samsung Honeyboard quick-action icon bar (Galaxy AI ✨, Emoji 😊, Clipboard 📋, S Pen ✍️, Voice 🎤, One-Handed 📐, Settings ⚙️).
 */
class OneUiToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Action {
        GALAXY_AI,
        EMOJI,
        CLIPBOARD,
        SPEN,
        VOICE,
        ONE_HANDED,
        SETTINGS
    }

    interface Listener {
        fun onActionClicked(action: Action)
        fun onSuggestionClicked(word: String)
    }

    var listener: Listener? = null

    private var theme: KeyboardTheme = KeyboardTheme.oneUiLight()
    private var suggestions: List<String> = emptyList()
    private var activeAction: Action? = null
    private var pressedIndex: Int = -1

    private val density = resources.displayMetrics.density
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f * density
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.75f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val suggestionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val primarySuggestionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val path = Path()
    private val rect = RectF()

    private val actions = Action.entries

    init {
        applyTheme(theme)
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        bgPaint.color = theme.toolbarBackgroundColor
        dividerPaint.color = theme.dividerColor
        pillPaint.color = theme.keyPressedColor
        invalidate()
    }

    fun setSuggestions(words: List<String>) {
        suggestions = words.take(3)
        invalidate()
    }

    fun setActiveAction(action: Action?) {
        activeAction = action
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (43f * density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        // Subtle bottom separator line like Samsung Honeyboard
        canvas.drawLine(0f, h - 0.8f * density, w, h - 0.8f * density, dividerPaint)

        if (suggestions.isNotEmpty()) {
            drawSuggestionsStrip(canvas, w, h)
        } else {
            drawIconToolbar(canvas, w, h)
        }
    }

    private fun drawSuggestionsStrip(canvas: Canvas, w: Float, h: Float) {
        // Samsung layout: [Left: #2] | [Center Primary: #1] | [Right: #3]
        val slotW = w / 3f
        val ordered = when (suggestions.size) {
            1 -> listOf("", suggestions[0], "")
            2 -> listOf(suggestions[1], suggestions[0], "")
            else -> listOf(suggestions[1], suggestions[0], suggestions[2])
        }

        suggestionPaint.textSize = 15.5f * density
        suggestionPaint.color = theme.keyTextColor
        primarySuggestionPaint.textSize = 16.0f * density
        primarySuggestionPaint.color = theme.accentKeyColor

        for (i in 0..2) {
            val word = ordered[i]
            val left = i * slotW
            val cx = left + slotW / 2f
            val cy = h / 2f

            if (pressedIndex == i && word.isNotEmpty()) {
                rect.set(left + 6f * density, 5f * density, left + slotW - 6f * density, h - 5f * density)
                canvas.drawRoundRect(rect, 8f * density, 8f * density, pillPaint)
            }

            if (word.isNotEmpty()) {
                val p = if (i == 1) primarySuggestionPaint else suggestionPaint
                val ty = cy - (p.descent() + p.ascent()) / 2f
                canvas.drawText(word, cx, ty, p)
            }

            if (i < 2) {
                val divX = (i + 1) * slotW
                canvas.drawLine(divX, h * 0.25f, divX, h * 0.75f, dividerPaint)
            }
        }
    }

    private fun drawIconToolbar(canvas: Canvas, w: Float, h: Float) {
        val slotW = w / actions.size
        val cy = h / 2f
        val iconSize = 19f * density

        for (i in actions.indices) {
            val action = actions[i]
            val cx = (i + 0.5f) * slotW
            val isSelected = activeAction == action || pressedIndex == i

            if (isSelected) {
                val pillR = 16f * density
                rect.set(cx - pillR, cy - pillR, cx + pillR, cy + pillR)
                canvas.drawRoundRect(rect, pillR, pillR, pillPaint)
            }

            val color = when {
                action == Action.GALAXY_AI -> theme.accentKeyColor
                activeAction == action -> theme.accentKeyColor
                else -> theme.functionalIconColor
            }
            iconPaint.color = color
            fillIconPaint.color = color

            drawToolbarVectorIcon(canvas, action, cx, cy, iconSize)
        }
    }

    private fun drawToolbarVectorIcon(
        canvas: Canvas,
        action: Action,
        cx: Float,
        cy: Float,
        size: Float
    ) {
        val r = size * 0.44f
        when (action) {
            Action.GALAXY_AI -> {
                // Four-point Galaxy AI sparkle star
                path.reset()
                path.moveTo(cx, cy - r * 1.1f)
                path.quadTo(cx, cy, cx + r * 1.1f, cy)
                path.quadTo(cx, cy, cx, cy + r * 1.1f)
                path.quadTo(cx, cy, cx - r * 1.1f, cy)
                path.quadTo(cx, cy, cx, cy - r * 1.1f)
                path.close()
                canvas.drawPath(path, fillIconPaint)
                // Mini companion star
                val sx = cx + r * 0.85f
                val sy = cy - r * 0.85f
                val sr = r * 0.42f
                path.reset()
                path.moveTo(sx, sy - sr)
                path.lineTo(sx + sr * 0.3f, sy - sr * 0.3f)
                path.lineTo(sx + sr, sy)
                path.lineTo(sx + sr * 0.3f, sy + sr * 0.3f)
                path.lineTo(sx, sy + sr)
                path.lineTo(sx - sr * 0.3f, sy + sr * 0.3f)
                path.lineTo(sx - sr, sy)
                path.lineTo(sx - sr * 0.3f, sy - sr * 0.3f)
                path.close()
                canvas.drawPath(path, fillIconPaint)
            }

            Action.EMOJI -> {
                canvas.drawCircle(cx, cy, r, iconPaint)
                canvas.drawCircle(cx - r * 0.34f, cy - r * 0.18f, r * 0.11f, fillIconPaint)
                canvas.drawCircle(cx + r * 0.34f, cy - r * 0.18f, r * 0.11f, fillIconPaint)
                rect.set(cx - r * 0.48f, cy - r * 0.35f, cx + r * 0.48f, cy + r * 0.52f)
                canvas.drawArc(rect, 20f, 140f, false, iconPaint)
            }

            Action.CLIPBOARD -> {
                rect.set(cx - r * 0.72f, cy - r * 0.75f, cx + r * 0.72f, cy + r * 0.95f)
                canvas.drawRoundRect(rect, r * 0.2f, r * 0.2f, iconPaint)
                rect.set(cx - r * 0.36f, cy - r * 0.98f, cx + r * 0.36f, cy - r * 0.55f)
                canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, fillIconPaint)
                canvas.drawLine(cx - r * 0.38f, cy - r * 0.1f, cx + r * 0.38f, cy - r * 0.1f, iconPaint)
                canvas.drawLine(cx - r * 0.38f, cy + r * 0.35f, cx + r * 0.22f, cy + r * 0.35f, iconPaint)
            }

            Action.SPEN -> {
                // Tilted S Pen stylus icon
                path.reset()
                path.moveTo(cx - r * 0.75f, cy + r * 0.75f)
                path.lineTo(cx - r * 0.35f, cy + r * 0.65f)
                path.lineTo(cx + r * 0.75f, cy - r * 0.45f)
                path.lineTo(cx + r * 0.45f, cy - r * 0.75f)
                path.lineTo(cx - r * 0.65f, cy + r * 0.35f)
                path.close()
                canvas.drawPath(path, iconPaint)
            }

            Action.VOICE -> {
                rect.set(cx - r * 0.36f, cy - r * 0.92f, cx + r * 0.36f, cy + r * 0.18f)
                canvas.drawRoundRect(rect, r * 0.36f, r * 0.36f, iconPaint)
                rect.set(cx - r * 0.68f, cy - r * 0.45f, cx + r * 0.68f, cy + r * 0.52f)
                canvas.drawArc(rect, 0f, 180f, false, iconPaint)
                canvas.drawLine(cx, cy + r * 0.52f, cx, cy + r * 0.92f, iconPaint)
            }

            Action.ONE_HANDED -> {
                rect.set(cx - r * 0.85f, cy - r * 0.68f, cx + r * 0.85f, cy + r * 0.68f)
                canvas.drawRoundRect(rect, r * 0.2f, r * 0.2f, iconPaint)
                rect.set(cx - r * 0.1f, cy - r * 0.42f, cx + r * 0.65f, cy + r * 0.42f)
                canvas.drawRoundRect(rect, r * 0.12f, r * 0.12f, fillIconPaint)
            }

            Action.SETTINGS -> {
                canvas.drawCircle(cx, cy, r * 0.42f, iconPaint)
                canvas.drawCircle(cx, cy, r * 0.82f, iconPaint)
                canvas.drawLine(cx, cy - r * 1.02f, cx, cy - r * 0.72f, iconPaint)
                canvas.drawLine(cx, cy + r * 0.72f, cx, cy + r * 1.02f, iconPaint)
                canvas.drawLine(cx - r * 1.02f, cy, cx - r * 0.72f, cy, iconPaint)
                canvas.drawLine(cx + r * 0.72f, cy, cx + r * 1.02f, cy, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hitIndex(x)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val idx = hitIndex(x)
                if (idx == pressedIndex && idx >= 0) {
                    if (suggestions.isNotEmpty()) {
                        val ordered = when (suggestions.size) {
                            1 -> listOf("", suggestions[0], "")
                            2 -> listOf(suggestions[1], suggestions[0], "")
                            else -> listOf(suggestions[1], suggestions[0], suggestions[2])
                        }
                        val word = ordered.getOrNull(idx).orEmpty()
                        if (word.isNotEmpty()) {
                            listener?.onSuggestionClicked(word)
                        }
                    } else {
                        val action = actions.getOrNull(idx)
                        if (action != null) {
                            listener?.onActionClicked(action)
                        }
                    }
                }
                pressedIndex = -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitIndex(x: Float): Int {
        if (width <= 0) return -1
        val count = if (suggestions.isNotEmpty()) 3 else actions.size
        val slotW = width.toFloat() / count
        return (x / slotW).toInt().coerceIn(0, count - 1)
    }
}
