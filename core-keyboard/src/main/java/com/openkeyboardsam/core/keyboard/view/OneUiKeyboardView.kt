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
import com.openkeyboardsam.core.keyboard.model.LayoutSizingMode
import com.openkeyboardsam.core.keyboard.model.ShiftState
import kotlin.math.abs

/**
 * Authentic Samsung One UI 6.1 (Honeyboard) Canvas Renderer.
 * - Row 2 automatic horizontal inset (when 9 keys are present)
 * - Compact number row height (82% of alpha row)
 * - Crisp vector icons for Shift, Backspace, Enter, and Globe
 * - Authentic One UI spacebar with side chevrons (< Español(ES) >)
 * - Balloon key-press preview & multi-character accent strip
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
        val rowIndex: Int,
        val isNumberRow: Boolean,
        val rect: RectF
    )

    var listener: Listener? = null
    var hapticEnabled: Boolean = true
    var showAltLabels: Boolean = true
    var swipeTypingEnabled: Boolean = true

    var heightScale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.80f, 1.30f)
            requestLayout()
            invalidate()
        }

    var sizingMode: LayoutSizingMode = LayoutSizingMode.FULL
        set(value) {
            field = value
            recomputeKeyRects(width, height)
            invalidate()
        }

    private var theme: KeyboardTheme = KeyboardTheme.oneUiLight()
    private var rows: List<List<KeySpec>> = emptyList()
    private var shiftState: ShiftState = ShiftState.OFF
    private val keyRects = mutableListOf<KeyRect>()

    // Pre-allocated Paints & Paths (zero allocation in onDraw)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val functionalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val altTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val popupHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swipePath = Path()

    private val density = resources.displayMetrics.density
    private val keyCornerRadius = 8.5f * density
    private val keyGapX = 5.2f * density
    private val keyGapY = 7.6f * density
    private val baseAlphaRowHeightPx = 52f * density
    private val baseNumRowHeightPx = 41f * density
    private val paddingTopPx = 5f * density
    private val paddingBottomPx = 10f * density
    private val paddingSidePx = 5f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Active touch & gesture state
    private var activeKey: KeyRect? = null
    private var downX = 0f
    private var downY = 0f
    private var lastSpaceX = 0f
    private var isSpaceSwiping = false
    private var isSwipeGesture = false
    private var isLongPressed = false
    private var popupOptions: List<String> = emptyList()
    private var selectedPopupIndex: Int = 0
    private val popupBounds = RectF()
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
            if (current.key.longPressChars.isNotEmpty()) {
                popupOptions = current.key.longPressChars
                selectedPopupIndex = 0
                invalidate()
            } else if (current.key.isRepeatable) {
                mainHandler.post(repeatRunnable)
            }
        }
    }

    init {
        iconPaint.strokeWidth = 1.9f * density
        topHighlightPaint.strokeWidth = 0.8f * density
        popupBorderPaint.strokeWidth = 1.0f * density
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
        topHighlightPaint.color = theme.keyTopBorderColor
        swipePaint.color = theme.swipeTrailColor
        swipePaint.strokeWidth = 4.8f * density
        popupPaint.color = theme.popupBgColor
        popupBorderPaint.color = theme.popupBorderColor
        popupHighlightPaint.color = theme.accentKeyColor
        invalidate()
    }

    private fun hasCompactNumberRow(): Boolean {
        return rows.size >= 5 && rows.first().all { it.label.length == 1 && it.label[0].isDigit() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val hasNum = hasCompactNumberRow()
        val numH = if (hasNum) baseNumRowHeightPx * heightScale else 0f
        val alphaCount = if (hasNum) rows.size - 1 else rows.size
        val alphaH = alphaCount * baseAlphaRowHeightPx * heightScale
        val desiredHeight = (numH + alphaH + paddingTopPx + paddingBottomPx).toInt()
            .coerceAtLeast((190 * density).toInt())
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeKeyRects(w, h)
    }

    private fun recomputeKeyRects(totalWidth: Int, totalHeight: Int) {
        keyRects.clear()
        if (totalWidth <= 0 || rows.isEmpty()) return

        val effectiveWidth = totalWidth * sizingMode.scaleWidth
        val startOffsetX = if (sizingMode.alignRight) totalWidth - effectiveWidth else 0f
        val usableWidth = effectiveWidth - paddingSidePx * 2f

        val hasNum = hasCompactNumberRow()
        val numRowH = if (hasNum) baseNumRowHeightPx * heightScale else 0f
        val alphaRowsCount = (if (hasNum) rows.size - 1 else rows.size).coerceAtLeast(1)
        val remainingH = (totalHeight - paddingTopPx - paddingBottomPx - numRowH)
            .coerceAtLeast(alphaRowsCount * baseAlphaRowHeightPx * heightScale)
        val alphaRowH = remainingH / alphaRowsCount

        var currentTopY = paddingTopPx

        for (rIndex in rows.indices) {
            val row = rows[rIndex]
            val isNumRow = hasNum && rIndex == 0
            val thisRowH = if (isNumRow) numRowH else alphaRowH

            // Authentic Samsung QWERTY inset for 9-key middle row (English A..L)
            val isNineKeyMiddleRow = !isNumRow && row.size == 9 && row.none { it.isFunctional }
            val sideInset = if (isNineKeyMiddleRow) usableWidth * 0.048f else 0f
            val rowUsableW = usableWidth - sideInset * 2f

            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
            val totalGaps = (row.size - 1) * keyGapX
            val unitWidth = (rowUsableW - totalGaps) / totalWeight

            var currentX = startOffsetX + paddingSidePx + sideInset
            val vGap = if (isNumRow) keyGapY * 0.42f else keyGapY * 0.5f
            val top = currentTopY + vGap
            val bottom = currentTopY + thisRowH - vGap

            for (key in row) {
                val keyW = unitWidth * key.weight
                val rect = RectF(currentX, top, currentX + keyW, bottom)
                keyRects.add(KeyRect(key, rIndex, isNumRow, rect))
                currentX += keyW + keyGapX
            }
            currentTopY += thisRowH
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        for (item in keyRects) {
            val key = item.key
            val rect = item.rect
            val isPressed = activeKey == item

            // 1. Samsung One UI key drop shadow
            canvas.drawRoundRect(
                rect.left,
                rect.top + 1.35f * density,
                rect.right,
                rect.bottom + 1.35f * density,
                keyCornerRadius,
                keyCornerRadius,
                shadowPaint
            )

            // 2. Key surface fill
            val isShiftActive = key.code == KeyCode.SHIFT && shiftState != ShiftState.OFF
            keyPaint.color = when {
                isPressed -> theme.keyPressedColor
                key.isAccent -> theme.accentKeyColor
                isShiftActive && !theme.isDark -> 0xFFFFFFFF.toInt()
                key.isFunctional -> theme.functionalKeyColor
                item.isNumberRow -> theme.numberKeyColor
                else -> theme.keyColor
            }
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, keyPaint)

            // 3. Subtle top rim highlight (One UI depth effect)
            canvas.drawRoundRect(rect, keyCornerRadius, keyCornerRadius, topHighlightPaint)

            // 4. Key foreground (Vector icon or typography)
            drawKeyForeground(canvas, item)
        }

        val pressed = activeKey
        if (pressed != null && !isSwipeGesture) {
            if (isLongPressed && popupOptions.isNotEmpty()) {
                drawMultiCharPopup(canvas, pressed)
            } else if (!pressed.key.isFunctional && pressed.key.code != KeyCode.SPACE) {
                drawSingleKeyPopup(canvas, pressed)
            }
        }

        if (isSwipeGesture && !swipePath.isEmpty) {
            canvas.drawPath(swipePath, swipePaint)
        }
    }

    private fun drawKeyForeground(canvas: Canvas, item: KeyRect) {
        val key = item.key
        val rect = item.rect
        val cx = rect.centerX()
        val cy = rect.centerY()

        when (key.code) {
            KeyCode.SHIFT -> {
                iconPaint.color = if (shiftState != ShiftState.OFF) {
                    theme.accentKeyColor
                } else {
                    theme.functionalIconColor
                }
                OneUiVectorIcons.drawShiftIcon(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    size = 20f * density,
                    paint = iconPaint,
                    filled = shiftState != ShiftState.OFF,
                    capsLock = shiftState == ShiftState.CAPS_LOCK
                )
                return
            }

            KeyCode.BACKSPACE -> {
                iconPaint.color = theme.functionalIconColor
                OneUiVectorIcons.drawBackspaceIcon(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    size = 21f * density,
                    paint = iconPaint
                )
                return
            }

            KeyCode.ENTER -> {
                iconPaint.color = 0xFFFFFFFF.toInt()
                OneUiVectorIcons.drawEnterIcon(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    size = 21f * density,
                    paint = iconPaint
                )
                return
            }

            KeyCode.LANGUAGE_SWITCH -> {
                iconPaint.color = theme.functionalIconColor
                OneUiVectorIcons.drawGlobeIcon(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    size = 20f * density,
                    paint = iconPaint
                )
                return
            }

            KeyCode.SPACE -> {
                // Authentic Samsung One UI Spacebar with subtle language label & side chevrons
                functionalTextPaint.textSize = 12.5f * density
                functionalTextPaint.color = theme.altTextColor
                val ty = cy - (functionalTextPaint.descent() + functionalTextPaint.ascent()) / 2f
                val textWithChevrons = if (key.label.isNotBlank()) "‹  ${key.label}  ›" else ""
                canvas.drawText(textWithChevrons, cx, ty, functionalTextPaint)
                return
            }
        }

        if (key.isFunctional) {
            functionalTextPaint.textSize = 13.5f * density
            functionalTextPaint.color = theme.functionalIconColor
            val ty = cy - (functionalTextPaint.descent() + functionalTextPaint.ascent()) / 2f
            canvas.drawText(key.label, cx, ty, functionalTextPaint)
            return
        }

        // Standard character / number key
        textPaint.color = theme.keyTextColor
        textPaint.textSize = (if (item.isNumberRow) 17.5f else 20.5f) * density
        val displayLabel = formatLabel(key)
        val verticalOffset = if (showAltLabels && key.altLabel != null) 1.8f * density else 0f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f + verticalOffset
        canvas.drawText(displayLabel, cx, textY, textPaint)

        // Top-right secondary symbol (One UI signature look)
        if (showAltLabels && key.altLabel != null) {
            altTextPaint.color = theme.altTextColor
            altTextPaint.textSize = 9.5f * density
            canvas.drawText(
                key.altLabel,
                rect.right - 5.0f * density,
                rect.top + 11.0f * density,
                altTextPaint
            )
        }
    }

    private fun drawSingleKeyPopup(canvas: Canvas, pressed: KeyRect) {
        val r = pressed.rect
        val popW = (r.width() * 1.28f).coerceAtLeast(42f * density)
        val popH = r.height() * 1.22f
        val popLeft = (r.centerX() - popW / 2f).coerceIn(3f * density, width - popW - 3f * density)
        val popTop = (r.top - popH - 6f * density).coerceAtLeast(2f * density)
        val radius = keyCornerRadius * 1.35f

        canvas.drawRoundRect(
            popLeft, popTop + 2f * density, popLeft + popW, popTop + popH + 2f * density,
            radius, radius, shadowPaint
        )
        canvas.drawRoundRect(popLeft, popTop, popLeft + popW, popTop + popH, radius, radius, popupPaint)
        canvas.drawRoundRect(popLeft, popTop, popLeft + popW, popTop + popH, radius, radius, popupBorderPaint)

        textPaint.color = theme.keyTextColor
        textPaint.textSize = 25f * density
        val popTextY = popTop + popH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(formatLabel(pressed.key), popLeft + popW / 2f, popTextY, textPaint)
    }

    private fun drawMultiCharPopup(canvas: Canvas, pressed: KeyRect) {
        val r = pressed.rect
        val cellW = 37f * density
        val popW = cellW * popupOptions.size
        val popH = r.height() * 1.18f
        val popLeft = (r.centerX() - popW / 2f).coerceIn(4f * density, width - popW - 4f * density)
        val popTop = (r.top - popH - 8f * density).coerceAtLeast(2f * density)
        popupBounds.set(popLeft, popTop, popLeft + popW, popTop + popH)
        val radius = keyCornerRadius * 1.4f

        canvas.drawRoundRect(
            popupBounds.left, popupBounds.top + 2f * density,
            popupBounds.right, popupBounds.bottom + 2f * density,
            radius, radius, shadowPaint
        )
        canvas.drawRoundRect(popupBounds, radius, radius, popupPaint)
        canvas.drawRoundRect(popupBounds, radius, radius, popupBorderPaint)

        textPaint.textSize = 18.5f * density
        for (i in popupOptions.indices) {
            val cellLeft = popLeft + i * cellW
            if (i == selectedPopupIndex) {
                canvas.drawRoundRect(
                    cellLeft + 2.5f * density,
                    popTop + 2.5f * density,
                    cellLeft + cellW - 2.5f * density,
                    popTop + popH - 2.5f * density,
                    keyCornerRadius,
                    keyCornerRadius,
                    popupHighlightPaint
                )
                textPaint.color = 0xFFFFFFFF.toInt()
            } else {
                textPaint.color = theme.keyTextColor
            }
            val ch = if (shiftState != ShiftState.OFF) popupOptions[i].uppercase() else popupOptions[i]
            val ty = popTop + popH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(ch, cellLeft + cellW / 2f, ty, textPaint)
        }
    }

    private fun formatLabel(key: KeySpec): String {
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
                popupOptions = emptyList()
                selectedPopupIndex = 0
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
                if (isLongPressed && popupOptions.isNotEmpty() && popupBounds.width() > 0) {
                    val relX = (x - popupBounds.left).coerceIn(0f, popupBounds.width() - 1f)
                    val cellW = popupBounds.width() / popupOptions.size
                    val newIdx = (relX / cellW).toInt().coerceIn(0, popupOptions.lastIndex)
                    if (newIdx != selectedPopupIndex) {
                        selectedPopupIndex = newIdx
                        performHapticTick()
                        invalidate()
                    }
                    return true
                }

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

                if (initial != null && initial.key.code == KeyCode.BACKSPACE && (downX - x) > 48f * density) {
                    cancelPendingCallbacks()
                    performHapticClick()
                    listener?.onBackspaceWordDelete()
                    activeKey = null
                    invalidate()
                    return true
                }

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
                if (isLongPressed && popupOptions.isNotEmpty()) {
                    val raw = popupOptions[selectedPopupIndex.coerceIn(0, popupOptions.lastIndex)]
                    val chosen = if (shiftState != ShiftState.OFF && raw.length == 1 && raw[0].isLetter()) {
                        raw.uppercase()
                    } else {
                        raw
                    }
                    listener?.onTextInput(chosen)
                    if (shiftState == ShiftState.ON) {
                        shiftState = ShiftState.OFF
                    }
                } else if (isSwipeGesture && swipedKeys.size >= 2) {
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
        popupOptions = emptyList()
        selectedPopupIndex = 0
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
