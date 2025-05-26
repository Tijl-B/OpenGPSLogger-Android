package eu.tijlb.opengpslogger.model.util

import android.graphics.Color
import kotlin.math.ln
import kotlin.math.pow

object ColorUtil {
    fun generateColor(seed: Long, opacity: Int): Int {
        val random = kotlin.random.Random(seed)
        val hue = random.nextInt(0, 360)
        val saturation = 0.7f + random.nextFloat() * 0.3f
        val value = 0.8f + random.nextFloat() * 0.2f

        val hsvColor = Color.HSVToColor(opacity, floatArrayOf(hue.toFloat(), saturation, value))
        return hsvColor
    }

    fun toDensityColor(amount: Long, maxAmount: Long): Int {
        if (amount <= 0) return Color.TRANSPARENT

        val logAmount = ln(amount.toDouble())
        val logMax = ln(maxAmount.toDouble())

        val t = (ln(logAmount + 1) / ln(logMax + 1)).coerceIn(0.0, 1.0)

        val hue = 60f + (270f - 60f) * t.toFloat()
        val saturation = 1f
        val value = 0.6f + 0.4f * t.toFloat()

        return Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

}