package eu.tijlb.opengpslogger.util

import java.time.Instant

class TimeUtil {
    companion object {
        fun iso8601ToUnixMillis(isoDate: String): Long {
            return Instant.parse(isoDate).toEpochMilli()
        }
    }
}