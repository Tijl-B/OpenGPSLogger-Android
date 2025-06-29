package eu.tijlb.opengpslogger.model.bitmap

import androidx.collection.MutableLongLongMap

class SparseDensityMap(val width: Int, val height: Int) {
    private val data = MutableLongLongMap()

    fun put(x: Long, y: Long, amount: Long) {
        data.put(key(x, y), amount)
    }

    fun get(x: Long, y: Long): Long = data.getOrElse(key(x, y)) { 0L }

    fun merge(factor: Int): SparseDensityMap {
        require(factor >= 1) { "Merge factor must be >= 1" }

        val newWidth = (width / factor).coerceAtLeast(1)
        val newHeight = (height / factor).coerceAtLeast(1)
        val merged = SparseDensityMap(newWidth, newHeight)

        data.forEach { k, v ->
            val x = (k shr 32).toInt()
            val y = k.toInt()

            val mergedX = x / factor
            val mergedY = y / factor

            val existing = merged.get(mergedX.toLong(), mergedY.toLong())
            merged.put(mergedX.toLong(), mergedY.toLong(), existing + v)
        }

        return merged
    }


    private fun key(x: Long, y: Long): Long {
        return (x shl 32) or (y and 0xFFFFFFFFL)
    }

}
