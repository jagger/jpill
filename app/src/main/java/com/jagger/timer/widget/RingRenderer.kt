package com.jagger.timer.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Draws the thin progress ring. Widgets can't draw arcs themselves, so it is rendered to a bitmap. */
object RingRenderer {
    private const val SIZE = 144
    private const val STROKE = 9f

    fun render(progress: Float, arcColor: Int, trackColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val inset = STROKE / 2f + 2f
        val box = RectF(inset, inset, SIZE - inset, SIZE - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STROKE
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = trackColor
        canvas.drawArc(box, 0f, 360f, false, paint)
        if (progress > 0f) {
            paint.color = arcColor
            canvas.drawArc(box, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
        }
        return bmp
    }
}
