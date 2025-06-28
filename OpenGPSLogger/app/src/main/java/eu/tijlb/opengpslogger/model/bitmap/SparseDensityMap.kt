package eu.tijlb.opengpslogger.model.bitmap

import androidx.collection.MutableLongLongMap

class SparseDensityMap(val width: Int, val height: Int) {
    private val data = MutableLongLongMap()

    fun put(x: Long, y: Long, amount: Long) {
        data.put(key(x, y), amount)
    }

    fun get(x: Long, y: Long): Long = data.getOrElse(key(x, y)) { 0L }

    private fun key(x: Long, y: Long): Long {
        return (x shl 32) or (y and 0xFFFFFFFFL)
    }
}
