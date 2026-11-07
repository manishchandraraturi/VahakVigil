package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorSessionRecorderTest {
    @Test
    fun `collects actual events and persists full batches in arrival order`() = runTest {
        val source = FakeRawSensorEventSource()
        val repository = FakeSensorEventRepository()
        val recorder = recorder(source, repository, batchSize = 2)

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        source.emit(event(20L))
        source.emit(event(10L, RawSensorType.GYROSCOPE))
        runCurrent()

        assertEquals(listOf(20L, 10L), repository.events.getValue("ride-1").map { it.event.timestampNanos })
        assertEquals(2L, recorder.diagnostics.value.receivedEventCount)
        assertEquals(2L, recorder.diagnostics.value.persistedEventCount)
        assertEquals(0, recorder.diagnostics.value.pendingEventCount)
        recorder.stopAndFlush()
    }

    @Test
    fun `duplicate starts keep one collector and same session`() = runTest {
        val source = FakeRawSensorEventSource()
        val recorder = recorder(source, FakeSensorEventRepository())

        assertTrue(recorder.start("ride-1"))
        assertTrue(recorder.start("ride-1"))
        runCurrent()
        source.emit(event(1L))
        runCurrent()

        assertEquals(1, source.collectorStartCount)
        assertEquals("ride-1", recorder.diagnostics.value.activeSessionId)
        recorder.stopAndFlush()
    }

    @Test
    fun `session startup diagnostic does not overwrite an active application recorder`() = runTest {
        val source = FakeRawSensorEventSource()
        val recorder = recorder(source, FakeSensorEventRepository())

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        recorder.reportUnavailable("recreated service could not refresh session state")

        assertEquals(SensorRecordingStatus.RECORDING, recorder.diagnostics.value.status)
        assertEquals("ride-1", recorder.diagnostics.value.activeSessionId)
        recorder.stopAndFlush()
    }

    @Test
    fun `normal stop flushes a partial batch before returning`() = runTest {
        val source = FakeRawSensorEventSource()
        val repository = FakeSensorEventRepository()
        val recorder = recorder(source, repository, batchSize = 16)

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        source.emit(event(1L))
        source.emit(event(2L, RawSensorType.GYROSCOPE))
        runCurrent()
        assertTrue(repository.events["ride-1"].isNullOrEmpty())

        recorder.stopAndFlush()

        assertEquals(listOf(1L, 2L), repository.events.getValue("ride-1").map { it.event.timestampNanos })
        assertEquals(SensorRecordingStatus.IDLE, recorder.diagnostics.value.status)
        assertEquals(0, recorder.diagnostics.value.pendingEventCount)
    }

    @Test
    fun `empty stop does not insert an empty batch`() = runTest {
        val repository = FakeSensorEventRepository()
        val recorder = recorder(FakeRawSensorEventSource(), repository)

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        recorder.stopAndFlush()

        assertTrue(repository.events.isEmpty())
        assertEquals(0L, recorder.diagnostics.value.persistedEventCount)
    }

    @Test
    fun `bounded recorder queue reports overflow without blocking collection`() = runTest {
        val source = FakeRawSensorEventSource()
        val repository = FakeSensorEventRepository()
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate
        val recorder = recorder(source, repository, batchSize = 1, queueCapacity = 1)

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        source.emit(event(1L))
        runCurrent() // Writer is held at the database seam.
        source.emit(event(2L))
        source.emit(event(3L))
        runCurrent()

        assertEquals(1L, recorder.diagnostics.value.recorderDroppedEventCount)
        gate.complete(Unit)
        runCurrent()
        recorder.stopAndFlush()
        assertEquals(2L, recorder.diagnostics.value.persistedEventCount)
    }

    @Test
    fun `failed insert retries once and reports lost batch`() = runTest {
        val repository = FakeSensorEventRepository().apply { failuresRemaining = 2 }
        val source = FakeRawSensorEventSource()
        val recorder = recorder(source, repository, batchSize = 1)

        assertTrue(recorder.start("ride-1"))
        runCurrent()
        source.emit(event(1L))
        runCurrent()

        assertEquals(2, repository.insertAttempts)
        assertEquals(0L, recorder.diagnostics.value.persistedEventCount)
        assertEquals(1L, recorder.diagnostics.value.failedBatchCount)
        assertEquals(1L, recorder.diagnostics.value.failedEventCount)
        assertTrue(recorder.diagnostics.value.lastError.orEmpty().contains("abandoned"))
        recorder.stopAndFlush()
    }

    @Test
    fun `missing or completed session is never recorded`() = runTest {
        val source = FakeRawSensorEventSource()
        val repository = FakeSensorEventRepository()
        val sessions = FakeRideSessionRepository().apply {
            session = session.copy(status = RideSessionStatus.COMPLETED)
        }
        val recorder = recorder(source, repository, sessions = sessions)
        source.emit(event(1L))

        assertFalse(recorder.start("ride-1"))
        assertTrue(repository.events.isEmpty())
        assertEquals(1L, recorder.diagnostics.value.recorderDroppedEventCount)
        assertEquals(SensorRecordingStatus.UNAVAILABLE, recorder.diagnostics.value.status)
        assertNull(recorder.diagnostics.value.activeSessionId)
    }

    @Test
    fun `repository exception during session validation becomes unavailable`() = runTest {
        val source = FakeRawSensorEventSource()
        val sessions = FakeRideSessionRepository().apply { lookupFailure = IllegalStateException("db unavailable") }
        val recorder = recorder(source, FakeSensorEventRepository(), sessions = sessions)

        assertFalse(recorder.start("ride-1"))
        assertEquals(SensorRecordingStatus.UNAVAILABLE, recorder.diagnostics.value.status)
        assertTrue(recorder.diagnostics.value.lastError.orEmpty().contains("db unavailable"))
        assertEquals(0, source.collectorStartCount)
    }

    private fun TestScope.recorder(
        source: FakeRawSensorEventSource,
        repository: FakeSensorEventRepository,
        batchSize: Int = 64,
        queueCapacity: Int = 512,
        sessions: FakeRideSessionRepository = FakeRideSessionRepository()
    ): SensorSessionRecorder = SensorSessionRecorder(
            source = source,
            sensorEventRepository = repository,
            rideSessionRepository = sessions,
            batchSize = batchSize,
            queueCapacity = queueCapacity,
            retryDelayMillis = 0L,
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            scope = backgroundScope
        )

    private fun event(timestamp: Long, type: RawSensorType = RawSensorType.ACCELEROMETER) = RawSensorEvent(
        type = type,
        timestampNanos = timestamp,
        timestampEpochMillis = timestamp / 1_000_000L,
        x = timestamp.toFloat(),
        y = 2f,
        z = 3f,
        accuracy = 3
    )

    private class FakeRawSensorEventSource : RawSensorEventSource {
        private val channel = Channel<RawSensorEvent>(64)
        private val _dropCount = AtomicLong(0L)
        var collectorStartCount = 0
            private set
        override val droppedEventCount: Long get() = _dropCount.get()
        override val rawSensorEvents: Flow<RawSensorEvent> = kotlinx.coroutines.flow.flow {
            collectorStartCount++
            channel.receiveAsFlow().collect { emit(it) }
        }

        fun emit(event: RawSensorEvent) {
            if (channel.trySend(event).isFailure) _dropCount.incrementAndGet()
        }

        override fun drainPendingEvents(): List<RawSensorEvent> = buildList {
            while (true) add(channel.tryReceive().getOrNull() ?: break)
        }
    }

    private class FakeSensorEventRepository : SensorEventRepository {
        val events = linkedMapOf<String, MutableList<RecordedSensorEvent>>()
        private var nextEventId = 0L
        var insertAttempts = 0
        var failuresRemaining = 0
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) {
            insertAttempts++
            writeGate?.await()
            if (failuresRemaining > 0) {
                failuresRemaining--
                throw IllegalStateException("database unavailable")
            }
            this.events.getOrPut(sessionId) { mutableListOf() }.addAll(events.map {
                RecordedSensorEvent(++nextEventId, it)
            })
        }

        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int) =
            events[sessionId].orEmpty().sortedBy { it.event.timestampNanos }.drop(offset).take(limit).map { it.event }

        override suspend fun countForSession(sessionId: String): Long = events[sessionId].orEmpty().size.toLong()

        override suspend fun getExportSnapshot(sessionId: String): SensorEventExportSnapshot {
            val rows = events[sessionId].orEmpty()
            return SensorEventExportSnapshot(rows.size.toLong(), rows.maxOfOrNull { it.eventId } ?: 0L)
        }

        override suspend fun getExportPage(
            sessionId: String,
            maxEventId: Long,
            after: SensorEventExportCursor?,
            limit: Int
        ): List<RecordedSensorEvent> = events[sessionId].orEmpty()
            .asSequence()
            .filter { it.eventId <= maxEventId }
            .filter { row -> after == null || row.event.timestampNanos > after.timestampNanos ||
                (row.event.timestampNanos == after.timestampNanos && row.eventId > after.eventId) }
            .sortedWith(compareBy<RecordedSensorEvent> { it.event.timestampNanos }.thenBy { it.eventId })
            .take(limit)
            .toList()
    }

    private class FakeRideSessionRepository : RideSessionRepository {
        var session = RideSession(
            id = "ride-1",
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = null,
            startLatitude = null,
            startLongitude = null,
            endLatitude = null,
            endLongitude = null,
            durationMillis = 0L,
            distanceMeters = null,
            averageSpeedMetersPerSecond = null,
            maximumSpeedMetersPerSecond = null,
            status = RideSessionStatus.ACTIVE
        )
        var lookupFailure: Exception? = null

        override fun observeSessions(): Flow<List<RideSession>> = MutableStateFlow(listOf(session)).asStateFlow()
        override fun observeSession(id: String): Flow<RideSession?> = MutableStateFlow(session.takeIf { it.id == id })
        override suspend fun getSession(id: String): RideSession? {
            lookupFailure?.let { throw it }
            return session.takeIf { it.id == id }
        }
        override suspend fun getActiveSession(): RideSession? = session.takeIf { it.status == RideSessionStatus.ACTIVE }
        override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession = session
        override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean = false
        override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean = false
        override suspend fun getPoints(sessionId: String): List<RideSessionPoint> = emptyList()
        override suspend fun deleteSession(sessionId: String) = Unit
    }
}
