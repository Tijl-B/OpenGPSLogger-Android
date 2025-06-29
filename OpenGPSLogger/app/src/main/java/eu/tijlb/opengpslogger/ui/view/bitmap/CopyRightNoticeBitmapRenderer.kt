package eu.tijlb.opengpslogger.ui.view.bitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import eu.tijlb.opengpslogger.model.database.tileserver.TileServerDbHelper
import eu.tijlb.opengpslogger.model.dto.BBoxDto

class CopyRightNoticeBitmapRenderer(val context: Context): AbstractBitmapRenderer() {


    private var tileServerDbHelper: TileServerDbHelper = TileServerDbHelper(context)

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val copyrightBarPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100
    }

    override fun redrawOnTranslation() = false

    override suspend fun draw(
        bbox: BBoxDto,
        zoom: Int,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {
        val copyrightNotice = tileServerDbHelper.getSelectedCopyrightNotice()
        return draw(copyrightNotice, renderDimension, assignBitmap, refreshView)
    }

    private fun draw(
        copyrightNotice: String,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap? {

        val width = renderDimension.first
        val height = renderDimension.second

        if (width == 0 || height == 0) return null

        val bitmap = createBitmap(width, height)
        if (copyrightNotice.isEmpty()) {
            assignBitmap(bitmap)
            return bitmap
        }
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
        return bitmap
    }
}