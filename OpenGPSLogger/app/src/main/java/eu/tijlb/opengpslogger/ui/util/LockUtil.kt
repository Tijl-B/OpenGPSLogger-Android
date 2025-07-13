package eu.tijlb.opengpslogger.ui.util

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object LockUtil {
    private const val TAG = "ogl-lockutil"

    fun <T> Mutex.tryLockOrSkip(block: () -> T): T? {
        return if (tryLock()) {
            try {
                block()
            } finally {
                unlock()
            }
        } else null
    }

    suspend fun <T> Mutex.runIfLast(atomicJob: AtomicReference<Job?>, block: suspend () -> T): T? {
        val currentJob = Job()
        atomicJob.getAndSet(currentJob)?.cancel()
        if (!currentJob.isActive) {
            Log.d(TAG, "CurrentJob is not active, continuing action")
            return null
        }

        return this.withTimeoutLock(30.seconds) {
            if (atomicJob.get() != currentJob || !currentJob.isActive) {
                Log.d(TAG, "Got lock but job is not active, not continuing action")
                return@withTimeoutLock null
            }
            Log.d(TAG, "Start action")
            val result = block()
            currentJob.complete()
            result
        }
    }

    suspend fun <T> Mutex.withTimeoutLock(duration: Duration, block: suspend () -> T): T? {
        try {
            return withTimeout(duration) {
                withLock {
                    return@withTimeout block.invoke()
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Waiting for lock timed out after a duration of $duration", e)
            return null
        }
    }
}