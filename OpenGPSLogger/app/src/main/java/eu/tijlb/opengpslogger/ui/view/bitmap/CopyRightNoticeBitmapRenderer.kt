package eu.tijlb.opengpslogger.ui.view.bitmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

class CopyRightNoticeBitmapRenderer {

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val copyrightBarPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100
    }

    fun draw(
        copyrightNotice: String,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap?) -> Unit,
        refreshView: () -> Any
    ) {
        if (copyrightNotice.isEmpty()) {
            assignBitmap(null)
            return
        }

        val width = renderDimension.first
        val height = renderDimension.second

        if (width == 0 || height == 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        assignBitmap(bitmap)

        val textBounds = Rect()
        textPaint.getTextBounds(copyrightNotice, 0, copyrightNotice.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()

        val barHeight = textHeight * 1.2F
        val barWidth = textWidth + 16

        val barLeft = width - barWidth
        val barTop = height - barHeight
        canvas.drawRect(barLeft, barTop, width.toFloat(), height.toFloat(), copyrightBarPaint)

        val textX = barLeft + 8
        val textY = height - textHeight * 0.2F
        canvas.drawText(copyrightNotice, textX, textY, textPaint)
        refreshView()
    }
}