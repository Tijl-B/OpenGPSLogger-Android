package eu.tijlb.opengpslogger.model.util

import java.time.Instant

object TimeUtil {
    fun iso8601ToUnixMillis(isoDate: String): Long {
        return Instant.parse(isoDate).toEpochMilli()
    }

}