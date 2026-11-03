package com.manish.vahakvigil.data.repository

import androidx.room.withTransaction
import com.manish.vahakvigil.data.local.db.AppDatabase
import com.manish.vahakvigil.data.local.db.dao.RideSessionDao
import com.manish.vahakvigil.data.local.db.entity.RideSessionEntity
import com.manish.vahakvigil.data.local.db.entity.RideSessionPointEntity
import com.manish.vahakvigil.domain.location.RidePathTracker
import com.manish.vahakvigil.domain.location.RideSessionMetricsCalculator
import com.manish.vahakvigil.domain.model.EmergencyLocation
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RideSessionRepositoryImpl(
    private val database: AppDatabase,
    private val dao: RideSessionDao
) : RideSessionRepository {
    override fun observeSessions(): Flow<List<RideSession>> = dao.observeSessions().map { rows -> rows.map { row -> row.toDomain() } }

    override fun observeSession(id: String): Flow<RideSession?> = dao.observeSession(id).map { it?.toDomain() }

    override suspend fun getSession(id: String): RideSession? = dao.getSession(id)?.toDomain()

    override suspend fun getActiveSession(): RideSession? = dao.getActiveSession()?.toDomain()

    override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession =
        dao.ensureActiveSession(RideSessionEntity(UUID.randomUUID().toString(), startedAtEpochMillis)).toDomain()

    override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean {
        if (!EmergencyLocation.isValidCoordinate(point.latitude, point.longitude) || point.timestampEpochMillis <= 0L) return false
        if (point.accuracyMeters?.let { !it.isFinite() || it < 0f || it > RidePathTracker.MAX_ACCEPTABLE_ACCURACY_METERS } == true) return false
        if (point.speedMetersPerSecond?.let { !it.isFinite() || it < 0f || it > RidePathTracker.MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND } == true) return false
        return database.withTransaction {
            val session = dao.getSession(sessionId) ?: return@withTransaction false
            if (session.status != RideSessionStatus.ACTIVE.name) return@withTransaction false
            val lastPoint = dao.getLastPoint(sessionId)
            if (lastPoint != null) {
                if (point.timestampEpochMillis <= lastPoint.timestampEpochMillis) return@withTransaction false
                val elapsedSeconds = (point.timestampEpochMillis - lastPoint.timestampEpochMillis) / 1000.0
                val distanceMeters = RidePathTracker.calculateDistanceMeters(
                    lastPoint.latitude, lastPoint.longitude, point.latitude, point.longitude
                )
                if (distanceMeters / elapsedSeconds > RidePathTracker.MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND) return@withTransaction false
            }
            dao.insertPoint(point.toEntity(sessionId))
            dao.updateObservedEndpoint(sessionId, point.latitude, point.longitude)
            true
        }
    }

    override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean = database.withTransaction {
        val session = dao.getSession(sessionId) ?: return@withTransaction false
        if (session.status != RideSessionStatus.ACTIVE.name) return@withTransaction false
        val points = dao.getPoints(sessionId)
        val duration = (endedAtEpochMillis - session.startedAtEpochMillis).coerceAtLeast(0L)
        val metrics = RideSessionMetricsCalculator.calculate(points.map { it.toDomain() }, duration)
        val status = when {
            interrupted -> RideSessionStatus.INTERRUPTED
            points.size < 2 -> RideSessionStatus.INCOMPLETE
            else -> RideSessionStatus.COMPLETED
        }
        dao.finalizeActiveSession(
            sessionId = sessionId,
            endedAt = endedAtEpochMillis,
            duration = duration,
            distance = metrics.distanceMeters,
            averageSpeed = metrics.averageSpeedMetersPerSecond,
            maximumSpeed = metrics.maximumSpeedMetersPerSecond,
            status = status.name
        ) == 1
    }

    override suspend fun getPoints(sessionId: String): List<RideSessionPoint> = dao.getPoints(sessionId).map { row -> row.toDomain() }

    override suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    private fun RideSessionEntity.toDomain() = RideSession(
        id, startedAtEpochMillis, endedAtEpochMillis, startLatitude, startLongitude,
        endLatitude, endLongitude, durationMillis, distanceMeters,
        averageSpeedMetersPerSecond, maximumSpeedMetersPerSecond,
        runCatching { RideSessionStatus.valueOf(status) }.getOrDefault(RideSessionStatus.INCOMPLETE)
    )

    private fun RideSessionPoint.toEntity(sessionId: String) = RideSessionPointEntity(
        sessionId = sessionId,
        latitude = latitude,
        longitude = longitude,
        timestampEpochMillis = timestampEpochMillis,
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond
    )

    private fun RideSessionPointEntity.toDomain() = RideSessionPoint(
        latitude, longitude, timestampEpochMillis, accuracyMeters, speedMetersPerSecond
    )

}
