package eu.tijlb.opengpslogger.model.util

import android.graphics.Color

object ColorUtil {
    fun generateColor(seed: Long, opacity: Int): Int {
        val random = kotlin.random.Random(seed)
        val hue = random.nextInt(0, 360)
        val saturation = 0.7f + random.nextFloat() * 0.3f
        val value = 0.8f + random.nextFloat() * 0.2f

        val hsvColor = Color.HSVToColor(opacity, floatArrayOf(hue.toFloat(), saturation, value))
        return hsvColor
    }
}