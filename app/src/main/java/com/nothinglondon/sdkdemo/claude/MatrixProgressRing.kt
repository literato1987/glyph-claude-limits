package com.nothinglondon.sdkdemo.claude

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import com.nothing.ketchum.GlyphMatrixUtils
import com.nothinglondon.sdkdemo.tesla.MatrixLedMask
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

object MatrixProgressRing {

    private const val CANVAS_SIZE = MatrixLedMask.SIZE
    private const val CENTER = 12.0
    private const val RING_INNER = 9.2
    private const val RING_OUTER = 11.3
    private const val ICON_SIZE = 11

    private val ringLeds: List<Pair<Int, Int>> by lazy {
        val leds = mutableListOf<Triple<Double, Int, Int>>()
        for (y in 0 until CANVAS_SIZE) {
            for (x in 0 until CANVAS_SIZE) {
                if (!MatrixLedMask.isLed(x, y)) continue
                val dx = x - CENTER
                val dy = y - CENTER
                val dist = hypot(dx, dy)
                if (dist !in RING_INNER..RING_OUTER) continue
                val angle = (atan2(dy, dx) + PI / 2 + 2 * PI) % (2 * PI)
                leds.add(Triple(angle, x, y))
            }
        }
        leds.sortedBy { it.first }.map { it.second to it.third }
    }

    fun progressBitmap(
        context: Context,
        iconResId: Int,
        usedPercentage: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_SIZE, CANVAS_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = false
        }

        val progress = (usedPercentage.coerceIn(0, 100) / 100f)
        val litCount = (ringLeds.size * progress).toInt().coerceIn(0, ringLeds.size)
        ringLeds.take(litCount).forEach { (x, y) ->
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }

        drawCenterIcon(context, canvas, iconResId)
        return GlyphMatrixUtils.drawableToBitmap(BitmapDrawable(context.resources, bitmap))
    }

    private fun drawCenterIcon(context: Context, canvas: Canvas, iconResId: Int) {
        val source = BitmapFactory.decodeResource(context.resources, iconResId) ?: return
        val left = ((CANVAS_SIZE - ICON_SIZE) / 2f).toInt()
        val top = ((CANVAS_SIZE - ICON_SIZE) / 2f).toInt()
        val dest = Rect(left, top, left + ICON_SIZE, top + ICON_SIZE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, dest, paint)
        source.recycle()
    }
}