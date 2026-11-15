package com.openkeyboardsam.core.keyboard.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.openkeyboardsam.core.keyboard.model.KeyCode
import com.openkeyboardsam.core.keyboard.model.KeySpec
import com.openkeyboardsam.core.keyboard.model.KeyboardTheme
import com.openkeyboardsam.core.keyboard.model.ShiftState
import kotlin.math.abs

/**
 * High-speed, zero-allocation Canvas-based keyboard view styled after Samsung One UI.
 * Avoids heavy View hierarchies to keep RAM usage minimal and frame times < 8ms.
 */
class OneUiKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onKeyPress(key: KeySpec, isAlt: Boolean = false)
        fun onTextInput(text: String)
        fun onSpacebarSwipe(deltaChars: Int)
        fun onLanguageSwipe()
        fun onBackspaceWordDelete()
        fun onSwipeWord(swipedKeys: List<KeySpec>)
    }

    private data class KeyRect(
        val key: KeySpec,
        val rect: RectF
    )

    var listener: Listener? = null
    var hapticEnabled: Boolean = true
    var showAltLabels: Boolean = true
    var swipeTypingEnabled: Boolean = true

    private var theme: KeyboardTheme = KeyboardTheme.oneUiLight()
    private var rows: List<List<KeySpec>> = emptyList()
    private var shiftState: ShiftState = ShiftState.OFF
    private val keyRects = mutableListOf<KeyRect>()

    // Pre-allocated Paints & Paths (zero allocation in onDraw)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val altTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swipePath = Path()

    private val density = resources.displayMetrics.density
    private val keyCornerRadius = 6.5f * density
    private val keyGapX = 4.5f * density
    private val keyGapY = 7.5f * density
    private val rowHeightPx = 50f * density
    private val paddingTopPx = 6f * density
    private val paddingBottomPx = 8f * density
    private val paddingSidePx = 4f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Active touch & gesture state
    private var activeKey: KeyRect? = null
    private var downX = 0f
    private var downY = 0f
    private var lastSpaceX = 0f
    private var isSpaceSwiping = false
    private var isSwipeGesture = false
    private var isLongPressed = false
    private val swipedKeys = mutableListOf<KeySpec>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            val current = activeKey ?: return
            if (current.key.isRepeatable) {
                performHapticTick()
                listener?.onKeyPress(current.key)
                mainHandler.postDelayed(this, 55L)
            }
        }
    }

    private val longPressRunnable = Runnable {
        val current = activeKey ?: return@Runnable
        if (!isSpaceSwiping && !isSwipeGesture) {
            isLongPressed = true
            performHapticClick()
            val alt = current.key.longPressChars.firstOrNull()
            if (alt != null) {
                listener?.onTextInput(alt)
                activeKey = null
                invalidate()
            } else if (current.key.isRepeatable) {
                mainHandler.post(repeatRunnable)
            }
        }
    }

    init {
        applyTheme(theme)
    }

    fun setKeyboard(newRows: List<List<KeySpec>>, newShiftState: ShiftState) {
        rows = newRows
        shiftState = newShiftState
        recomputeKeyRects(width, height)
        requestLayout()
        invalidate()
    }

    fun updateShiftState(newShiftState: ShiftState) {
        shiftState = newShiftState
        invalidate()
    }

    fun applyTheme(newTheme: KeyboardTheme) {
        theme = newTheme
        bgPaint.color = theme.backgroundColor
        shadowPaint.color = theme.keyShadowColor
        swipePaint.color = theme.swipeTrailColor
        swipePaint.strokeWidth = 4.5f * density
        popupPaint.color = theme.popupBgColor
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (rows.size * rowHeightPx + paddingTopPx + paddingBottomPx).toInt()
            .coerceAtLeast((210 * density).toInt())
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyRects(w, h)
    }

    private fun recomputeKeyRects(totalWidth: Int, totalHeight: Int) {
        keyRects.clear()
        if (totalWidth <= 0 || rows.isEmpty()) return

        val usableWidth = totalWidth - paddingSidePx * 2f
        val rowCount = rows.size
        val availableHeight = (totalHeight - paddingTopPx - paddingBottomPx).coerceAtLeast(rowCount * rowHeightPx)
        val actualRowHeight = availableHeight / rowCount

        for (rIndex in rows.indices) {
            val row = rows[rIndex]
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            val totalGaps = (row.size - 1) * keyGapX
            val unitWidth = (usableWidth - totalGaps) / totalWeight

            var currentX = paddingSidePx
            val top = paddingTopPx + rIndex * actualRowHeight + keyGapY * 0.5f
            val bottom = paddingTopPx + (rIndex + 1) * actualRowHeight - keyGapY * 0.5f

            for (key in row) {
                val keyW = unitWidth * key.weight
                val rect = RectF(currentX, top, currentX + keyW, bottom)
                keyRects.add(KeyRect(key, rect))
                currentX += keyW + keyGapX
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        textPaint.textSize = 19f * density
        altTextPaint.textSize = 10f * density

        for (item in keyRects) {
            val key = item.key
            val rect = item.rect
            val isPressed = activeKey == item

            // Subtle One UI key shadow
            canvas.drawRoundRect(
                rect.left,
                rect.top + 1.2f * density,
                rect.right,
                rect.bottom + 1.2f * density,
                keyCornerRadius,
                keyCornerRadius,
                shadowPaint
            )

            keyPaint.color = when {
                isPressed -> theme.keyPressedColor
                key.isAccent -> theme.accentKeyColor
                key.isFunctional -> theme.functionalKeyColor
                else -> theme.keyColor
            }
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)

            // Main label
            textPaint.color = if (key.isAccent) 0xFFFFFFFF.toInt() else theme.keyTextColor
            val displayLabel = formatLabel(key)
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(displayLabel, rect.centerX(), textY, textPaint)

            // Top-right alternative character hint (One UI signature look)
            if (showAltLabels && key.altLabel != null) {
                altTextPaint.color = theme.altTextColor
                canvas.drawText(
                    key.altLabel,
                    rect.right - 4.5f * density,
                    rect.top + 12f * density,
                    altTextPaint
                )
            }
        }

        // Draw active key popup preview
        val pressed = activeKey
        if (pressed != null && !pressed.key.isFunctional && pressed.key.code != KeyCode.SPACE && !isSwipeGesture) {
            val r = pressed.rect
            val popW = r.width() * 1.2f
            val popH = r.height() * 1.15f
            val popLeft = (r.centerX() - popW / 2f).coerceIn(2f, width - popW - 2f)
            val popTop = (r.top - popH - 6f * density).coerceAtLeast(2f)
            canvas.drawRoundRect(
                popLeft, popTop, popLeft + popW, popTop + popH,
                keyCornerRadius * 1.3f, keyCornerRadius * 1.3f, shadowPaint
            )
            canvas.drawRoundRect(
                popLeft, popTop, popLeft + popW, popTop + popH,
                keyCornerRadius * 1.3f, keyCornerRadius * 1.3f, popupPaint
            )
            textPaint.color = theme.keyTextColor
            textPaint.textSize = 24f * density
            val popTextY = popTop + popH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(formatLabel(pressed.key), popLeft + popW / 2f, popTextY, textPaint)
        }

        // Draw swipe trail
        if (isSwipeGesture && !swipePath.isEmpty) {
            canvas.drawPath(swipePath, swipePaint)
        }
    }

    private fun formatLabel(key: KeySpec): String {
        if (key.code == KeyCode.SHIFT) {
            return when (shiftState) {
                ShiftState.OFF -> "⇧"
                ShiftState.ON -> "⬆"
                ShiftState.CAPS_LOCK -> "⇪"
            }
        }
        if (key.label.length == 1 && key.label[0].isLetter()) {
            return if (shiftState == ShiftState.OFF) key.label.lowercase() else key.label.uppercase()
        }
        return key.label
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingCallbacks()
                downX = x
                downY = y
                lastSpaceX = x
                isSpaceSwiping = false
                isSwipeGesture = false
                isLongPressed = false
                swipedKeys.clear()
                swipePath.reset()

                val hit = findKeyAt(x, y)
                activeKey = hit
                if (hit != null) {
                    performHapticTick()
                    if (hit.key.code != KeyCode.SPACE) {
                        swipePath.moveTo(x, y)
                        swipedKeys.add(hit.key)
                    }
                    mainHandler.postDelayed(longPressRunnable, longPressTimeout)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val initial = activeKey
                if (initial != null && initial.key.code == KeyCode.SPACE) {
                    val dx = x - lastSpaceX
                    val step = 18f * density
                    if (abs(dx) >= step) {
                        isSpaceSwiping = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        val delta = (dx / step).toInt()
                        if (delta != 0) {
                            listener?.onSpacebarSwipe(delta)
                            lastSpaceX = x
                        }
                    }
                    return true
                }

                // Backspace quick swipe-left to delete whole word
                if (initial != null && initial.key.code == KeyCode.BACKSPACE && (downX - x) > 48f * density) {
                    cancelPendingCallbacks()
                    performHapticClick()
                    listener?.onBackspaceWordDelete()
                    activeKey = null
                    invalidate()
                    return true
                }

                // Glide/Swipe typing across alpha keys
                if (swipeTypingEnabled && initial != null && !initial.key.isFunctional) {
                    val dist = abs(x - downX) + abs(y - downY)
                    if (dist > touchSlop * 2.2f) {
                        isSwipeGesture = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        swipePath.lineTo(x, y)
                        val hit = findKeyAt(x, y)
                        if (hit != null && !hit.key.isFunctional && swipedKeys.lastOrNull() != hit.key) {
                            swipedKeys.add(hit.key)
                        }
                        invalidate()
                        return true
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelPendingCallbacks()
                if (isSwipeGesture && swipedKeys.size >= 2) {
                    listener?.onSwipeWord(swipedKeys.toList())
                } else if (!isSpaceSwiping && !isLongPressed) {
                    val hit = findKeyAt(x, y) ?: activeKey
                    if (hit != null) {
                        listener?.onKeyPress(hit.key)
                    }
                }
                resetTouchState()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelPendingCallbacks()
                resetTouchState()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKeyAt(x: Float, y: Float): KeyRect? {
        for (item in keyRects) {
            if (item.rect.contains(x, y)) return item
        }
        return null
    }

    private fun cancelPendingCallbacks() {
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.removeCallbacks(repeatRunnable)
    }

    private fun resetTouchState() {
        activeKey = null
        isSpaceSwiping = false
        isSwipeGesture = false
        isLongPressed = false
        swipedKeys.clear()
        swipePath.reset()
    }

    private fun performHapticTick() {
        if (!hapticEnabled) return
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(12L)
        }
    }

    private fun performHapticClick() {
        if (!hapticEnabled) return
        val vib = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(20L)
        }
    }
}
