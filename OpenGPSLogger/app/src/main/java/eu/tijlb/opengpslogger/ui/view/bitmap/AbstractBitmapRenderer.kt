package eu.tijlb.opengpslogger.ui.view.bitmap

import android.graphics.Bitmap
import eu.tijlb.opengpslogger.model.dto.BBoxDto

abstract class AbstractBitmapRenderer {

    abstract suspend fun draw(
        bbox: BBoxDto,
        zoom: Double,
        renderDimension: Pair<Int, Int>,
        assignBitmap: (Bitmap) -> Unit,
        refreshView: () -> Any
    ): Bitmap?

    open fun redrawOnTranslation() = true

}