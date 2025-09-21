package eu.tijlb.opengpslogger.model.parser

import eu.tijlb.opengpslogger.ui.fragment.ImportFragment
import java.io.InputStream

interface ParserInterface {
    fun parse(
        inputStream: InputStream,
        save: (ImportFragment.Point, importStart: Long, source: String) -> Unit
    )
}