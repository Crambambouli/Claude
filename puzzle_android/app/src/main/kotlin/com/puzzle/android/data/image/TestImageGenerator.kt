package com.puzzle.android.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object TestImageGenerator {

    /** Creates a (cols×100) × (rows×100) bitmap with colour-coded numbered cells. */
    fun create(cols: Int, rows: Int): Bitmap {
        val cellPx = 100
        val w = cols * cellPx
        val h = rows * cellPx
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = cellPx * 0.35f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 0, 0, 0)
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        val total = cols * rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                // Hue cycles over the whole image
                val hue = idx.toFloat() / total * 360f
                fillPaint.color = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.88f))

                val left   = (c * cellPx).toFloat()
                val top    = (r * cellPx).toFloat()
                val right  = left + cellPx
                val bottom = top + cellPx

                canvas.drawRect(left, top, right, bottom, fillPaint)
                canvas.drawRect(left, top, right, bottom, linePaint)
                canvas.drawText(
                    "${idx + 1}",
                    left + cellPx / 2f,
                    top + cellPx * 0.62f,
                    textPaint
                )
            }
        }
        return bitmap
    }
}
