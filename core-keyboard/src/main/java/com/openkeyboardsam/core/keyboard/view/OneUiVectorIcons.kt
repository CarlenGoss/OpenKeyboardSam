package com.openkeyboardsam.core.keyboard.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Crisp geometric vector icons for Samsung One UI functional keys & toolbar,
 * avoiding blurry Unicode text characters.
 */
object OneUiVectorIcons {

    private val path = Path()
    private val oval = RectF()

    fun drawShiftIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint,
        filled: Boolean,
        capsLock: Boolean
    ) {
        val half = size * 0.48f
        val stemW = size * 0.22f
        val arrowBottom = cy + half * 0.08f
        val stemBottom = if (capsLock) cy + half * 0.52f else cy + half * 0.78f

        path.reset()
        // Arrow head + stem
        path.moveTo(cx, cy - half * 0.85f)
        path.lineTo(cx + half * 0.82f, arrowBottom)
        path.lineTo(cx + stemW, arrowBottom)
        path.lineTo(cx + stemW, stemBottom)
        path.lineTo(cx - stemW, stemBottom)
        path.lineTo(cx - stemW, arrowBottom)
        path.lineTo(cx - half * 0.82f, arrowBottom)
        path.close()

        val prevStyle = paint.style
        paint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        canvas.drawPath(path, paint)

        if (capsLock) {
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(
                cx - stemW * 1.15f,
                cy + half * 0.68f,
                cx + stemW * 1.15f,
                cy + half * 0.90f,
                size * 0.05f,
                size * 0.05f,
                paint
            )
        }
        paint.style = prevStyle
    }

    fun drawBackspaceIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint
    ) {
        val w = size * 0.52f
        val h = size * 0.34f
        val leftPoint = cx - w * 0.95f
        val bodyLeft = cx - w * 0.32f
        val rightEdge = cx + w * 0.88f

        path.reset()
        path.moveTo(leftPoint, cy)
        path.lineTo(bodyLeft, cy - h)
        path.lineTo(rightEdge, cy - h)
        path.lineTo(rightEdge, cy + h)
        path.lineTo(bodyLeft, cy + h)
        path.close()

        val prevStyle = paint.style
        paint.style = Paint.Style.STROKE
        canvas.drawPath(path, paint)

        // Inner 'X'
        val xCenter = (bodyLeft + rightEdge) * 0.52f
        val xHalf = h * 0.42f
        canvas.drawLine(xCenter - xHalf, cy - xHalf, xCenter + xHalf, cy + xHalf, paint)
        canvas.drawLine(xCenter + xHalf, cy - xHalf, xCenter - xHalf, cy + xHalf, paint)
        paint.style = prevStyle
    }

    fun drawEnterIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint
    ) {
        val w = size * 0.44f
        val h = size * 0.34f
        val prevStyle = paint.style
        paint.style = Paint.Style.STROKE

        path.reset()
        path.moveTo(cx + w * 0.65f, cy - h * 0.85f)
        path.lineTo(cx + w * 0.65f, cy + h * 0.25f)
        path.lineTo(cx - w * 0.65f, cy + h * 0.25f)
        canvas.drawPath(path, paint)

        // Left arrow tip
        val tipX = cx - w * 0.65f
        val tipY = cy + h * 0.25f
        val a = size * 0.16f
        canvas.drawLine(tipX, tipY, tipX + a, tipY - a, paint)
        canvas.drawLine(tipX, tipY, tipX + a, tipY + a, paint)
        paint.style = prevStyle
    }

    fun drawGlobeIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        paint: Paint
    ) {
        val r = size * 0.42f
        val prevStyle = paint.style
        paint.style = Paint.Style.STROKE

        canvas.drawCircle(cx, cy, r, paint)
        oval.set(cx - r * 0.48f, cy - r, cx + r * 0.48f, cy + r)
        canvas.drawOval(oval, paint)
        canvas.drawLine(cx - r, cy, cx + r, cy, paint)
        paint.style = prevStyle
    }
}
