package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import kotlinx.coroutines.flow.Flow

interface RideSessionRepository {
    fun observeSessions(): Flow<List<RideSession>>
    fun observeSession(id: String): Flow<RideSession?>
    suspend fun getSession(id: String): RideSession?
    suspend fun getActiveSession(): RideSession?
    suspend fun startOrResume(startedAtEpochMillis: Long): RideSession
    suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean
    suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean = false): Boolean
    suspend fun getPoints(sessionId: String): List<RideSessionPoint>
    suspend fun deleteSession(sessionId: String)
}
