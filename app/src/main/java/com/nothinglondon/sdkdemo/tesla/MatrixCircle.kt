package com.nothinglondon.sdkdemo.tesla

object MatrixCircle {

    const val LAYER_TOP_Y = 5
    const val LAYER_MID_Y = 11
    const val LAYER_LOW_Y = 17

    fun isInside(x: Int, y: Int): Boolean = MatrixLedMask.isLed(x, y)

    fun centerX(textLength: Int, glyphCell: Int = 4): Int {
        val width = textLength * glyphCell - 1
        return ((MatrixLedMask.SIZE - width) / 2).coerceAtLeast(0)
    }
}