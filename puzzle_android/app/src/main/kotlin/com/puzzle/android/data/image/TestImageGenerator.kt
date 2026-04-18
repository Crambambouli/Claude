package com.puzzle.android.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient

object TestImageGenerator {

    fun create(cols: Int, rows: Int): Bitmap {
        val cellPx = 100
        val w = cols * cellPx
        val h = rows * cellPx
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val radialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.35f, h * 0.35f, maxOf(w, h) * 0.9f,
                intArrayOf(
                    Color.rgb(255, 220, 230),
                    Color.rgb(240, 100, 150),
                    Color.rgb(160, 40, 100)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), radialPaint)

        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                w * 0.65f, h * 0.65f,
                intArrayOf(
                    Color.argb(90, 255, 200, 50),
                    Color.argb(90, 80, 180, 255),
                    Color.argb(90, 200, 80, 200),
                    Color.argb(90, 255, 200, 50)
                ),
                null
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sweepPaint)

        return bitmap
    }
}
