package eu.tijlb.opengpslogger.model.bitmap

import android.graphics.Canvas
import android.graphics.Paint

class SparseDensityMap(val width: Int, val height: Int) {
    val data = mutableMapOf<Pair<Float, Float>, Int>()

    fun put(x: Float, y: Float, color: Int) {
        data[Pair(x, y)] = color
    }

    fun draw(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = false
            strokeWidth = 1f
            style = Paint.Style.FILL
        }
        for ((pos, color) in data) {
            paint.color = color
            canvas.drawPoint(pos.first, pos.second, paint)
        }
    }
}