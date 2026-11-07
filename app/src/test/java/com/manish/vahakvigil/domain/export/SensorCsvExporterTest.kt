package com.manish.vahakvigil.domain.export

import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorCsvExporterTest {
    @Test
    fun `writes exact stable header and raw accelerometer and gyroscope fields`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            add("ride-1", event(RawSensorType.ACCELEROMETER, 1_234_567_890L, 1.2345678f, -2.5f, 9.81f, 3))
            add("ride-1", event(RawSensorType.GYROSCOPE, 1_234_567_999L, 0.12345679f, -0.25f, 1.5f, 2))
        }
        val output = ByteArrayOutputStream()
        val exporter = exporter(repository)

        val result = exporter.export("ride-1", { output })
        val csv = output.toString(StandardCharsets.UTF_8.name())

        assertEquals(SensorCsvExportResult.Complete(2), result)
        assertEquals(SensorCsvExporter.CSV_HEADER, csv.lineSequence().first())
        assertTrue(csv.contains("ride-1,ACCELEROMETER,1234567890,1234,1.2345678,-2.5,9.81,3"))
        assertTrue(csv.contains("ride-1,GYROSCOPE,1234567999,1234,0.12345679,-0.25,1.5,2"))
    }

    @Test
    fun `decimal output is independent of device locale`() = runTest {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val repository = FakeSensorEventRepository().apply {
                add("ride-1", event(RawSensorType.ACCELEROMETER, 1_000_000L, 1.25f, 2.5f, 3.75f, 3))
            }
            val output = ByteArrayOutputStream()
            exporter(repository).export("ride-1", { output })
            val csv = output.toString(StandardCharsets.UTF_8.name())

            assertTrue(csv.contains("ride-1,ACCELEROMETER,1000000,1,1.25,2.5,3.75,3"))
            assertFalse(csv.contains("1,25"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `session identifiers with commas quotes and newlines follow CSV escaping`() = runTest {
        val sessionId = "ride,\"quoted\"\nsecond line"
        val repository = FakeSensorEventRepository().apply {
            add(sessionId, event(RawSensorType.GYROSCOPE, 5_000_000L, 1f, 2f, 3f, 3))
        }
        val output = ByteArrayOutputStream()

        exporter(repository).export(sessionId, { output })

        val csv = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(csv.contains("\"ride,\"\"quoted\"\"\nsecond line\",GYROSCOPE"))
    }

    @Test
    fun `empty session neither opens a document nor reports a completed export`() = runTest {
        var opened = false
        val result = exporter(FakeSensorEventRepository()).export("empty-ride", {
            opened = true
            ByteArrayOutputStream()
        })

        assertEquals(SensorCsvExportResult.Empty, result)
        assertFalse(opened)
    }

    @Test
    fun `keyset pages preserve deterministic equal timestamp ordering`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            repeat(5) { index ->
                add("ride-1", event(RawSensorType.ACCELEROMETER, 7_000_000L, index.toFloat(), 0f, 0f, index))
            }
        }
        val output = ByteArrayOutputStream()

        exporter(repository, pageSize = 2).export("ride-1", { output })

        val dataRows = output.toString(StandardCharsets.UTF_8.name()).lineSequence().drop(1).filter { it.isNotEmpty() }.toList()
        assertEquals(5, dataRows.size)
        assertEquals(listOf("0.0", "1.0", "2.0", "3.0", "4.0"), dataRows.map { it.split(',')[4] })
        assertEquals(listOf(2, 2, 1), repository.pageSizes)
    }

    @Test
    fun `snapshot boundary excludes later inserted events and isolates selected session`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            add("ride-1", event(RawSensorType.ACCELEROMETER, 10_000_000L, 1f, 2f, 3f, 3))
            add("other-ride", event(RawSensorType.GYROSCOPE, 11_000_000L, 4f, 5f, 6f, 3))
        }
        repository.beforeFirstPage = {
            repository.add("ride-1", event(RawSensorType.GYROSCOPE, 1_000_000L, 99f, 0f, 0f, 1))
        }
        val output = ByteArrayOutputStream()

        val result = exporter(repository, pageSize = 1).export("ride-1", { output })

        assertEquals(SensorCsvExportResult.Complete(1), result)
        val csv = output.toString(StandardCharsets.UTF_8.name())
        assertTrue(csv.contains("ride-1,ACCELEROMETER"))
        assertFalse(csv.contains("99.0"))
        assertFalse(csv.contains("other-ride"))
    }

    @Test
    fun `large export requests one bounded page at a time`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            repeat(2_501) { index ->
                add("ride-large", event(RawSensorType.ACCELEROMETER, index * 1_000_000L, index.toFloat(), 0f, 0f, 3))
            }
        }
        val output = CountingOutputStream()
        val progress = mutableListOf<SensorCsvExportProgress>()

        val result = exporter(repository, pageSize = 500).export("ride-large", { output }) { progress += it }

        assertEquals(SensorCsvExportResult.Complete(2_501), result)
        assertEquals(listOf(500, 500, 500, 500, 500, 1), repository.pageSizes)
        assertEquals(500, repository.maximumPageSize)
        assertEquals(2_501L, progress.last().writtenRows)
        assertTrue(output.bytesWritten > 100_000L)
    }

    @Test
    fun `cancellation closes output stream and does not complete`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            add("ride-1", event(RawSensorType.ACCELEROMETER, 1_000_000L, 1f, 2f, 3f, 3))
        }
        val pageRequested = CompletableDeferred<Unit>()
        val releasePage = CompletableDeferred<Unit>()
        repository.beforeFirstPage = {
            pageRequested.complete(Unit)
            releasePage.await()
        }
        val output = TrackingOutputStream()
        val job = backgroundScope.async {
            exporter(repository).export("ride-1", { output })
        }
        runCurrent()
        pageRequested.await()

        job.cancel()
        runCurrent()

        assertTrue(output.closed)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `output write failure propagates and cannot be reported as success`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            add("ride-1", event(RawSensorType.GYROSCOPE, 1_000_000L, 1f, 2f, 3f, 3))
        }
        var finalProgressReported = false

        try {
            exporter(repository).export("ride-1", { FailingOutputStream() }) {
                if (it.writtenRows == it.totalRows) finalProgressReported = true
            }
            throw AssertionError("Expected output failure")
        } catch (_: IOException) {
            assertFalse(finalProgressReported)
        }
    }

    @Test
    fun `open stream failure propagates without export success`() = runTest {
        val repository = FakeSensorEventRepository().apply {
            add("ride-1", event(RawSensorType.ACCELEROMETER, 1_000_000L, 1f, 2f, 3f, 3))
        }

        try {
            exporter(repository).export("ride-1", { throw IOException("unavailable") })
            throw AssertionError("Expected stream opening failure")
        } catch (error: IOException) {
            assertEquals("unavailable", error.message)
        }
    }

    @Test
    fun `suggested filenames sanitize session identifiers`() {
        assertEquals(
            "vahakvigil_sensor_session_ride_1.csv",
            SensorCsvExporter.suggestedFilename("ride/1?*")
        )
    }

    private fun TestScope.exporter(repository: FakeSensorEventRepository, pageSize: Int = 500) = SensorCsvExporter(
        repository = repository,
        pageSize = pageSize,
        ioDispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun event(
        type: RawSensorType,
        timestampNanos: Long,
        x: Float,
        y: Float,
        z: Float,
        accuracy: Int
    ) = RawSensorEvent(
        type = type,
        timestampNanos = timestampNanos,
        timestampEpochMillis = timestampNanos / 1_000_000L,
        x = x,
        y = y,
        z = z,
        accuracy = accuracy
    )

    private class FakeSensorEventRepository : SensorEventRepository {
        private val events = linkedMapOf<String, MutableList<RecordedSensorEvent>>()
        private var nextId = 1L
        val pageSizes = mutableListOf<Int>()
        var maximumPageSize = 0
            private set
        var beforeFirstPage: (suspend () -> Unit)? = null

        fun add(sessionId: String, event: RawSensorEvent) {
            events.getOrPut(sessionId) { mutableListOf() }.add(RecordedSensorEvent(nextId++, event))
        }

        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) {
            events.forEach { add(sessionId, it) }
        }

        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int): List<RawSensorEvent> =
            events[sessionId].orEmpty().drop(offset).take(limit).map { it.event }

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
        ): List<RecordedSensorEvent> {
            if (pageSizes.isEmpty()) beforeFirstPage?.invoke()
            val page = events[sessionId].orEmpty().asSequence()
                .filter { it.eventId <= maxEventId }
                .filter { row -> after == null || row.event.timestampNanos > after.timestampNanos ||
                    (row.event.timestampNanos == after.timestampNanos && row.eventId > after.eventId) }
                .sortedWith(compareBy<RecordedSensorEvent> { it.event.timestampNanos }.thenBy { it.eventId })
                .take(limit)
                .toList()
            pageSizes += page.size
            maximumPageSize = maxOf(maximumPageSize, page.size)
            return page
        }
    }

    private class CountingOutputStream : OutputStream() {
        var bytesWritten = 0L
            private set
        override fun write(value: Int) { bytesWritten++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { bytesWritten += length }
    }

    private class TrackingOutputStream : ByteArrayOutputStream() {
        var closed = false
            private set
        override fun close() {
            closed = true
            super.close()
        }
    }

    private class FailingOutputStream : OutputStream() {
        override fun write(value: Int) { throw IOException("disk full") }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { throw IOException("disk full") }
    }
}
