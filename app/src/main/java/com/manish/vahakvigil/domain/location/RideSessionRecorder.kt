package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationPoint
import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns one local ride session and prevents point leakage across session boundaries. */
class RideSessionRecorder(
    private val repository: RideSessionRepository,
    val pathTracker: RidePathTracker = RidePathTracker(),
    private val timeProvider: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private var activeSessionId: String? = null

    suspend fun startOrResume(): String? = mutex.withLock { startOrResumeLocked() }

    private suspend fun startOrResumeLocked(): String? {
        activeSessionId?.let { return it }
        pathTracker.reset()
        val session = safely { repository.startOrResume(timeProvider()) } ?: return null
        activeSessionId = session.id
        safely {
            repository.getPoints(session.id).forEach { point ->
                pathTracker.addPoint(
                    RideLocationPoint(point.latitude, point.longitude, point.accuracyMeters, point.timestampEpochMillis),
                    currentTimeMs = point.timestampEpochMillis
                )
            }
        }
        return session.id
    }

    suspend fun record(location: RideLocationState.Available): Boolean = mutex.withLock {
        val sessionId = activeSessionId ?: startOrResumeLocked() ?: return@withLock false
        if (!pathTracker.addPoint(
                RideLocationPoint(location.latitude, location.longitude, location.accuracyMeters, location.timestampMs),
                currentTimeMs = timeProvider()
            )
        ) return@withLock false
        val saved = safely {
            repository.appendPoint(
                sessionId,
                RideSessionPoint(location.latitude, location.longitude, location.timestampMs, location.accuracyMeters, location.speedMps)
            )
        } ?: false
        if (!saved) pathTracker.removeLastIfMatches(location)
        saved
    }

    suspend fun finish(interrupted: Boolean = false): Boolean = mutex.withLock {
        val id = activeSessionId ?: return@withLock false
        val finished = safely { repository.finish(id, timeProvider(), interrupted) } ?: false
        activeSessionId = null
        pathTracker.clear()
        finished
    }

    suspend fun markOrphanedSessionInterrupted(): Boolean = mutex.withLock {
        if (activeSessionId != null) return@withLock false
        val session = safely { repository.getActiveSession() } ?: return@withLock false
        safely { repository.finish(session.id, timeProvider(), interrupted = true) } ?: false
    }

    private fun RidePathTracker.removeLastIfMatches(location: RideLocationState.Available) {
        removeLast(RideLocationPoint(location.latitude, location.longitude, location.accuracyMeters, location.timestampMs))
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }
}
