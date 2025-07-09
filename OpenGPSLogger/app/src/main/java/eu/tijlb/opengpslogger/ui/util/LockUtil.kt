package eu.tijlb.opengpslogger.ui.util

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

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

        return this.withLock {
            if (atomicJob.get() != currentJob || !currentJob.isActive) {
                Log.d(TAG, "Got lock but job is not active, not continuing action")
                return null
            }
            Log.d(TAG, "Start action")
            val result = block()
            currentJob.complete()
            result
        }
    }
}