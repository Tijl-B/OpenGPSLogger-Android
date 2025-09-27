package eu.tijlb.opengpslogger.ui.util

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.util.UUID
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
        atomicJob.getAndSet(currentJob)?.cancelAndJoin()
        if (!currentJob.isActive) {
            Log.d(TAG, "CurrentJob is not active, continuing action")
            return null
        }

        return this.lockWithTimeout(10.seconds) {
            if (atomicJob.get() != currentJob || !currentJob.isActive) {
                Log.d(TAG, "Got lock but job is not active, not continuing action")
                return@lockWithTimeout null
            }
            Log.d(TAG, "Start action")
            val result = block()
            Log.d(TAG, "Done action")
            currentJob.complete()
            result
        }
    }

    suspend fun <T> Mutex.lockWithTimeout(duration: Duration, block: suspend () -> T): T? {
        val lockOwner = UUID.randomUUID()
        Log.d(TAG, "Trying to get lock by owner $lockOwner")
        return try {
            withTimeout(duration) {
                lock(lockOwner)
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(
                TAG,
                "Waiting for or executing lock timed out after a duration of $duration for owner $lockOwner",
                e
            )
            return null
        } catch (e: CancellationException) {
            Log.d(
                TAG,
                "Waiting for or executing lock got canceled for owner $lockOwner",
                e
            )
            return null
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unexpected exception while waiting for or executing lock by owner $lockOwner",
                e
            )
            return null
        } finally {
            try {
                unlock(lockOwner)
                Log.d(TAG, "Releasing lock by owner $lockOwner")
            } catch (e: Exception) {
                Log.d(TAG, "Failed releasing lock by owner $lockOwner", e)
            }
        }
    }
}