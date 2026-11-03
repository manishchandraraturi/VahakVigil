package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationState
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideSessionRecorderTest {
    @Test fun `repeated starts resume one session`() = runTest {
        val store = FakeRideStore()
        val recorder = RideSessionRecorder(store, timeProvider = { 1000L })
        val first = recorder.startOrResume()
        val second = recorder.startOrResume()
        assertEquals(first, second)
        assertEquals(1, store.sessions.size)
    }

    @Test fun `accepted GPS points belong to active session and duplicate movement is filtered`() = runTest {
        val store = FakeRideStore()
        val recorder = RideSessionRecorder(store, timeProvider = { 5000L })
        val sessionId = recorder.startOrResume()!!
        val first = RideLocationState.Available(12.0, 77.0, 5f, 1000L, 2f)
        val duplicate = first.copy(timestampMs = 2000L)
        val moved = RideLocationState.Available(12.0001, 77.0, 5f, 3000L, 4f)
        assertTrue(recorder.record(first))
        assertFalse(recorder.record(duplicate))
        assertTrue(recorder.record(moved))
        assertEquals(listOf(sessionId), store.points.keys.toList())
        assertEquals(2, store.points.getValue(sessionId).size)
    }

    @Test fun `repeated finish is idempotent and next ride gets a clean session`() = runTest {
        val store = FakeRideStore()
        var now = 10_000L
        val recorder = RideSessionRecorder(store, timeProvider = { now })
        val first = recorder.startOrResume()!!
        assertTrue(recorder.finish())
        assertFalse(recorder.finish())
        now += 60_000L
        val next = recorder.startOrResume()!!
        assertTrue(next != first)
        assertNull(store.points[next])
        assertEquals(RideSessionStatus.INCOMPLETE, store.sessions.getValue(first).status)
    }

    @Test fun `session and movement path recover after recorder recreation`() = runTest {
        val store = FakeRideStore()
        val recorder = RideSessionRecorder(store, timeProvider = { 10_000L })
        val id = recorder.startOrResume()!!
        recorder.record(RideLocationState.Available(12.0, 77.0, 5f, 1000L))
        recorder.record(RideLocationState.Available(12.0001, 77.0, 5f, 2000L))
        val recovered = RideSessionRecorder(store, timeProvider = { 20_000L })
        assertEquals(id, recovered.startOrResume())
        assertEquals(2, recovered.pathTracker.getCurrentPath().size)
    }

    @Test fun `orphaned active session is marked interrupted`() = runTest {
        val store = FakeRideStore()
        store.startOrResume(1000L)
        val recorder = RideSessionRecorder(store, timeProvider = { 5000L })
        assertTrue(recorder.markOrphanedSessionInterrupted())
        assertEquals(RideSessionStatus.INTERRUPTED, store.sessions.values.single().status)
    }

    private class FakeRideStore : RideSessionRepository {
        val sessions = linkedMapOf<String, RideSession>()
        val points = linkedMapOf<String, MutableList<RideSessionPoint>>()
        private val observed = MutableStateFlow<List<RideSession>>(emptyList())
        private var nextId = 0

        override fun observeSessions(): Flow<List<RideSession>> = observed
        override fun observeSession(id: String): Flow<RideSession?> = MutableStateFlow(sessions[id])
        override suspend fun getSession(id: String): RideSession? = sessions[id]
        override suspend fun getActiveSession() = sessions.values.firstOrNull { it.status == RideSessionStatus.ACTIVE }
        override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession {
            getActiveSession()?.let { return it }
            val session = RideSession("ride-${++nextId}", startedAtEpochMillis, null, null, null, null, null, 0, null, null, null, RideSessionStatus.ACTIVE)
            sessions[session.id] = session
            publish()
            return session
        }
        override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean {
            if (sessions[sessionId]?.status != RideSessionStatus.ACTIVE) return false
            points.getOrPut(sessionId) { mutableListOf() }.add(point)
            return true
        }
        override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean {
            val session = sessions[sessionId] ?: return false
            if (session.status != RideSessionStatus.ACTIVE) return false
            val status = if (interrupted) RideSessionStatus.INTERRUPTED else if (points[sessionId].orEmpty().size < 2) RideSessionStatus.INCOMPLETE else RideSessionStatus.COMPLETED
            sessions[sessionId] = session.copy(endedAtEpochMillis = endedAtEpochMillis, durationMillis = endedAtEpochMillis - session.startedAtEpochMillis, status = status)
            publish()
            return true
        }
        override suspend fun getPoints(sessionId: String) = points[sessionId].orEmpty().toList()
        override suspend fun deleteSession(sessionId: String) { sessions.remove(sessionId); points.remove(sessionId); publish() }
        private fun publish() { observed.value = sessions.values.sortedByDescending { it.startedAtEpochMillis } }
    }
}
