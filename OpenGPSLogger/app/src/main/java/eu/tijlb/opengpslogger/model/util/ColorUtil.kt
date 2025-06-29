package eu.tijlb.opengpslogger.model.util

import android.graphics.Color
import androidx.core.graphics.alpha
import kotlin.math.ln

private const val DENSITY_HUE_MIN = 45F
private const val DENSITY_HUE_MAX = 360F
private const val DENSITY_VALUE_MIN = 0.6F
private const val DENSITY_MIN_ALPHA = 0.2F

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

        val hue = DENSITY_HUE_MIN + (DENSITY_HUE_MAX - DENSITY_HUE_MIN) * t.toFloat()
        val saturation = 1f
        val value = DENSITY_VALUE_MIN + (1F - DENSITY_VALUE_MIN) * t.toFloat()
        val alpha = ((DENSITY_MIN_ALPHA + (1F - DENSITY_MIN_ALPHA) * amount / 10F) * 255).toInt()
            .coerceIn(1, 255)

        return Color.HSVToColor(alpha, floatArrayOf(hue, saturation, value))
    }

}