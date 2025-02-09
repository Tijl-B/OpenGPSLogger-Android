package eu.tijlb.opengpslogger.model.database.tileserver

import android.provider.BaseColumns

object TileServerDbContract : BaseColumns {
    const val TABLE_NAME = "tile_server"
    const val COLUMN_NAME_NAME = "name"
    const val COLUMN_NAME_URL = "url"
    const val COLUMN_NAME_COPYRIGHT_NOTICE = "copyright_notice"
    const val COLUMN_NAME_SELECTED = "selected"
}