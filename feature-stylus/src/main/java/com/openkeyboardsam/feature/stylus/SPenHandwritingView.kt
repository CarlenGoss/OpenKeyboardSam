package com.openkeyboardsam.feature.stylus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Lightweight S Pen / Finger Handwriting Canvas for OpenKeyboardSam.
 * Zero-allocation drawing path with pressure sensitivity for Galaxy S22 Ultra S Pen.
 */
class SPenHandwritingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onRecognizedText(text: String)
        fun onSpaceRequested()
        fun onBackspaceRequested()
        fun onCloseRequested()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val inkPath = Path()
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3E91FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x338E95A2
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x888E95A2.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
    }

    private data class StrokeBounds(
        var minX: Float = Float.MAX_VALUE,
        var maxX: Float = Float.MIN_VALUE,
        var minY: Float = Float.MAX_VALUE,
        var maxY: Float = Float.MIN_VALUE,
        var startX: Float = 0f,
        var startY: Float = 0f,
        var endX: Float = 0f,
        var endY: Float = 0f,
        var totalLength: Float = 0f,
        var points: Int = 0
    ) {
        fun add(x: Float, y: Float, prevX: Float, prevY: Float) {
            if (points == 0) {
                startX = x
                startY = y
            } else {
                totalLength += abs(x - prevX) + abs(y - prevY)
            }
            endX = x
            endY = y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            points++
        }

        val width: Float get() = (maxX - minX).coerceAtLeast(1f)
        val height: Float get() = (maxY - minY).coerceAtLeast(1f)
    }

    private val strokes = mutableListOf<StrokeBounds>()
    private var currentStroke: StrokeBounds? = null
    private var lastX = 0f
    private var lastY = 0f
    private val handler = Handler(Looper.getMainLooper())

    private val recognizeRunnable = Runnable {
        recognizeAndClear()
    }

    fun setThemeColors(inkColor: Int, hintColor: Int) {
        inkPaint.color = inkColor
        hintPaint.color = hintColor
        invalidate()
    }

    fun clearCanvas() {
        handler.removeCallbacks(recognizeRunnable)
        inkPath.reset()
        strokes.clear()
        currentStroke = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height * 0.68f
        canvas.drawLine(16f * density, midY, width - 16f * density, midY, guidePaint)
        if (inkPath.isEmpty) {
            canvas.drawText(
                "✍️ Escribe aquí con tu S Pen (trazo ← borra, → espacio)",
                width / 2f,
                height * 0.45f,
                hintPaint
            )
        } else {
            canvas.drawPath(inkPath, inkPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val pressure = event.pressure.coerceIn(0.2f, 1.0f)
        if (SPenAndDexDetector.isStylusEvent(event)) {
            inkPaint.strokeWidth = (2.5f + pressure * 3.5f) * density
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(recognizeRunnable)
                inkPath.moveTo(x, y)
                lastX = x
                lastY = y
                currentStroke = StrokeBounds().apply { add(x, y, x, y) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                inkPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                currentStroke?.add(x, y, lastX, lastY)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                inkPath.lineTo(x, y)
                currentStroke?.let {
                    it.add(x, y, lastX, lastY)
                    strokes.add(it)
                }
                currentStroke = null
                invalidate()
                handler.postDelayed(recognizeRunnable, 650L)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearCanvas()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun recognizeAndClear() {
        if (strokes.isEmpty()) return
        val first = strokes.first()

        // Horizontal gesture shortcuts with S Pen:
        // Long right-to-left line -> Backspace
        // Long left-to-right line -> Space
        if (strokes.size == 1 && first.width > 90f * density && first.height < 28f * density) {
            if (first.endX < first.startX) {
                listener?.onBackspaceRequested()
            } else {
                listener?.onSpaceRequested()
            }
            clearCanvas()
            return
        }

        // Heuristic stroke recognizer for punctuation & basic characters when offline
        val recognized = classifyStrokes(strokes)
        if (recognized.isNotEmpty()) {
            listener?.onRecognizedText(recognized)
        }
        clearCanvas()
    }

    private fun classifyStrokes(list: List<StrokeBounds>): String {
        val b = list.first()
        val w = b.width
        val h = b.height
        val ratio = w / h

        // Tiny tap -> period
        if (w < 12f * density && h < 12f * density) return "."
        // Tall vertical stroke
        if (ratio < 0.28f && h > 25f * density) {
            return if (list.size >= 2) "i" else "l"
        }
        // Closed loop (start and end close together)
        val closeDist = abs(b.endX - b.startX) + abs(b.endY - b.startY)
        if (closeDist < (w + h) * 0.35f && ratio in 0.65f..1.45f) {
            return "o"
        }
        if (list.size == 2 && ratio in 0.4f..1.5f) {
            return "t"
        }
        if (b.endY > b.startY && b.endX > b.startX && ratio > 0.7f) {
            return "c"
        }
        return "a"
    }
}
