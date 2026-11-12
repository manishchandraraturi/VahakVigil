package com.manish.vahakvigil.ui.screens.ridehistory

import com.manish.vahakvigil.domain.export.SensorCsvDocumentExport
import com.manish.vahakvigil.domain.export.SensorCsvExportProgress
import com.manish.vahakvigil.domain.export.SensorCsvExportResult
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RideHistoryViewModelTest {
    @Test
    fun `detail count and CSV progress reach the UI state after completed export`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val exporter = FakeDocumentExport(eventCount = 3L)
            val viewModel = newViewModel(exporter, StandardTestDispatcher(testScheduler))
            viewModel.loadDetail("ride-1")
            runCurrent()
            assertEquals(3L, viewModel.state.value.sensorEventCount)

            viewModel.exportSensorCsv("ride-1", "content://selected/document")
            runCurrent()

            assertEquals(1, exporter.exportCalls)
            assertEquals(SensorCsvExportStatus.SUCCEEDED, viewModel.state.value.sensorCsvExportStatus)
            assertEquals(SensorCsvExportProgress(3L, 3L), viewModel.state.value.sensorCsvProgress)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `duplicate export is prevented and cancellation becomes visible`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val exporter = FakeDocumentExport(eventCount = 5L, gate = gate)
            val viewModel = newViewModel(exporter, StandardTestDispatcher(testScheduler))

            viewModel.exportSensorCsv("ride-1", "content://selected/document")
            viewModel.exportSensorCsv("ride-1", "content://selected/document-2")
            runCurrent()
            assertEquals(1, exporter.exportCalls)
            assertEquals(SensorCsvExportStatus.RUNNING, viewModel.state.value.sensorCsvExportStatus)

            viewModel.cancelSensorCsvExport()
            runCurrent()

            assertEquals(SensorCsvExportStatus.CANCELLED, viewModel.state.value.sensorCsvExportStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `export errors are exposed instead of success`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val exporter = FakeDocumentExport(eventCount = 1L, failure = IllegalStateException("disk full"))
            val viewModel = newViewModel(exporter, StandardTestDispatcher(testScheduler))

            viewModel.exportSensorCsv("ride-1", "content://selected/document")
            advanceUntilIdle()

            assertEquals(SensorCsvExportStatus.FAILED, viewModel.state.value.sensorCsvExportStatus)
            assertEquals("disk full", viewModel.state.value.sensorCsvError)
            assertFalse(viewModel.state.value.sensorCsvExportStatus == SensorCsvExportStatus.SUCCEEDED)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun newViewModel(exporter: SensorCsvDocumentExport, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        RideHistoryViewModel(FakeRideRepository(), exporter, EmptySensorEventRepository(), dispatcher)

    private class FakeDocumentExport(
        private val eventCount: Long,
        private val gate: CompletableDeferred<Unit>? = null,
        private val failure: Exception? = null
    ) : SensorCsvDocumentExport {
        var exportCalls = 0

        override suspend fun countEvents(sessionId: String): Long = eventCount

        override suspend fun export(
            sessionId: String,
            destination: String,
            onProgress: suspend (SensorCsvExportProgress) -> Unit
        ): SensorCsvExportResult {
            exportCalls++
            onProgress(SensorCsvExportProgress(0L, eventCount))
            gate?.await()
            failure?.let { throw it }
            onProgress(SensorCsvExportProgress(eventCount, eventCount))
            return SensorCsvExportResult.Complete(eventCount)
        }
    }

    private class FakeRideRepository : RideSessionRepository {
        private val session = RideSession(
            id = "ride-1", startedAtEpochMillis = 1_000L, endedAtEpochMillis = null,
            startLatitude = null, startLongitude = null, endLatitude = null, endLongitude = null,
            durationMillis = 1_000L, distanceMeters = null, averageSpeedMetersPerSecond = null,
            maximumSpeedMetersPerSecond = null, status = RideSessionStatus.COMPLETED
        )

        override fun observeSessions(): Flow<List<RideSession>> = MutableStateFlow(listOf(session))
        override fun observeSession(id: String): Flow<RideSession?> = MutableStateFlow(session.takeIf { it.id == id })
        override suspend fun getSession(id: String): RideSession? = session.takeIf { it.id == id }
        override suspend fun getActiveSession(): RideSession? = null
        override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession = session
        override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean = false
        override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean = false
        override suspend fun getPoints(sessionId: String): List<RideSessionPoint> = emptyList()
        override suspend fun deleteSession(sessionId: String) = Unit
    }

    private class EmptySensorEventRepository : SensorEventRepository {
        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) = Unit
        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int) = emptyList<RawSensorEvent>()
        override suspend fun countForSession(sessionId: String) = 0L
        override suspend fun getExportSnapshot(sessionId: String) = SensorEventExportSnapshot(0, 0)
        override suspend fun getExportPage(sessionId: String, maxEventId: Long, after: SensorEventExportCursor?, limit: Int) = emptyList<RecordedSensorEvent>()
        override suspend fun getAnalysisPage(sessionId: String, maxEventId: Long, after: SensorEventAnalysisCursor, limit: Int) = emptyList<RecordedSensorEvent>()
    }
}
