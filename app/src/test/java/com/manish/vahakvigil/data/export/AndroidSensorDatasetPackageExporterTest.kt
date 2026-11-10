package com.manish.vahakvigil.data.export

import com.manish.vahakvigil.domain.export.DatasetContributionConsent
import com.manish.vahakvigil.domain.export.DatasetContributionConsentStore
import com.manish.vahakvigil.domain.export.DatasetPackageExportResult
import com.manish.vahakvigil.domain.export.SensorCsvExporter
import com.manish.vahakvigil.domain.model.RawSensorEvent
import com.manish.vahakvigil.domain.model.RawSensorType
import com.manish.vahakvigil.domain.model.RecordedSensorEvent
import com.manish.vahakvigil.domain.model.RideSession
import com.manish.vahakvigil.domain.model.RideSessionPoint
import com.manish.vahakvigil.domain.model.RideSessionStatus
import com.manish.vahakvigil.domain.model.SensorEventAnalysisCursor
import com.manish.vahakvigil.domain.model.SensorEventExportCursor
import com.manish.vahakvigil.domain.model.SensorEventExportSnapshot
import com.manish.vahakvigil.domain.repository.RideSessionRepository
import com.manish.vahakvigil.domain.repository.SensorEventRepository
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidSensorDatasetPackageExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeSensorEvents : SensorEventRepository {
        private val events = mutableMapOf<String, MutableList<RecordedSensorEvent>>()
        private var nextId = 1L

        fun add(sessionId: String, type: RawSensorType, tsNanos: Long) {
            events.getOrPut(sessionId) { mutableListOf() }.add(
                RecordedSensorEvent(
                    eventId = nextId++,
                    event = RawSensorEvent(
                        type = type,
                        timestampNanos = tsNanos,
                        timestampEpochMillis = tsNanos / 1_000_000L,
                        x = 1.0f,
                        y = 2.0f,
                        z = 3.0f,
                        accuracy = 3
                    )
                )
            )
        }

        override suspend fun insertBatch(sessionId: String, events: List<RawSensorEvent>) {}
        override suspend fun getEventsForSession(sessionId: String, limit: Int, offset: Int): List<RawSensorEvent> = emptyList()
        override suspend fun countForSession(sessionId: String): Long = events[sessionId].orEmpty().size.toLong()

        override suspend fun getExportSnapshot(sessionId: String): SensorEventExportSnapshot {
            val list = events[sessionId].orEmpty()
            return SensorEventExportSnapshot(list.size.toLong(), list.maxOfOrNull { it.eventId } ?: 0L)
        }

        override suspend fun getExportPage(
            sessionId: String,
            maxEventId: Long,
            after: SensorEventExportCursor?,
            limit: Int
        ): List<RecordedSensorEvent> {
            return events[sessionId].orEmpty().asSequence()
                .filter { it.eventId <= maxEventId }
                .filter { row -> after == null || row.event.timestampNanos > after.timestampNanos ||
                    (row.event.timestampNanos == after.timestampNanos && row.eventId > after.eventId) }
                .sortedWith(compareBy<RecordedSensorEvent> { it.event.timestampNanos }.thenBy { it.eventId })
                .take(limit)
                .toList()
        }

        override suspend fun getAnalysisPage(sessionId: String, maxEventId: Long, after: SensorEventAnalysisCursor, limit: Int): List<RecordedSensorEvent> = emptyList()
    }

    private class FakeRideSessions : RideSessionRepository {
        val sessions = mutableMapOf<String, RideSession>()

        override fun observeSessions(): Flow<List<RideSession>> = emptyFlow()
        override fun observeSession(id: String): Flow<RideSession?> = emptyFlow()
        override suspend fun getSession(id: String): RideSession? = sessions[id]
        override suspend fun getActiveSession(): RideSession? = sessions.values.find { it.status == RideSessionStatus.ACTIVE }
        override suspend fun startOrResume(startedAtEpochMillis: Long): RideSession = throw UnsupportedOperationException()
        override suspend fun appendPoint(sessionId: String, point: RideSessionPoint): Boolean = true
        override suspend fun finish(sessionId: String, endedAtEpochMillis: Long, interrupted: Boolean): Boolean = true
        override suspend fun getPoints(sessionId: String): List<RideSessionPoint> = emptyList()
        override suspend fun deleteSession(sessionId: String) { sessions.remove(sessionId) }
    }

    private class FakeConsentStore : DatasetContributionConsentStore {
        var granted = false
        var time: Long? = null
        override fun isGranted() = granted
        override fun grantedAtEpochMillis() = time
        override fun grant(nowEpochMillis: Long) { granted = true; time = nowEpochMillis }
        override fun withdraw() { granted = false; time = null }
    }

    private fun testSession(id: String, status: RideSessionStatus): RideSession {
        return RideSession(
            id = id,
            startedAtEpochMillis = 1_700_000_000_000L,
            endedAtEpochMillis = 1_700_000_010_000L,
            startLatitude = 12.0,
            startLongitude = 77.0,
            endLatitude = 12.1,
            endLongitude = 77.1,
            durationMillis = 10_000L,
            distanceMeters = 500.0,
            averageSpeedMetersPerSecond = 15.0,
            maximumSpeedMetersPerSecond = 20.0,
            status = status
        )
    }

    @Test
    fun `export requires explicit consent`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore()
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents()
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-1"] = testSession("ride-1", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = { ByteArrayOutputStream() }
        )

        try {
            exporter.export("ride-1", "file://dummy", "REAL")
            fail("Expected exception when consent is missing")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("consent is required") == true)
        }
    }

    @Test
    fun `export excludes active rides and requires valid completed ride`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents()
        val rideRepo = FakeRideSessions().apply {
            sessions["active-ride"] = testSession("active-ride", RideSessionStatus.ACTIVE)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = { ByteArrayOutputStream() }
        )

        try {
            exporter.export("active-ride", "file://dummy", "REAL")
            fail("Expected exception for active ride export")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Active rides cannot be exported") == true)
        }
    }

    @Test
    fun `successful export produces valid zip with pseudonymous dataset ID and allowlisted metadata`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents().apply {
            add("ride-internal-123", RawSensorType.ACCELEROMETER, 1_000_000_000L)
            add("ride-internal-123", RawSensorType.GYROSCOPE, 1_000_000_000L)
        }
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-internal-123"] = testSession("ride-internal-123", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)

        val fixedDatasetId = UUID.randomUUID().toString()
        val outputFile = tempFolder.newFile("exported_package.zip")

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            now = { 1_700_000_020_000L },
            newDatasetId = { fixedDatasetId },
            deviceModelProvider = { "Pixel 8" },
            appVersionProvider = { "1.0.0-test" },
            openOutputStream = { FileOutputStream(outputFile) }
        )

        val result = exporter.export("ride-internal-123", outputFile.absolutePath, "REAL")
        assertTrue(result is DatasetPackageExportResult.Complete)
        assertEquals(fixedDatasetId, (result as DatasetPackageExportResult.Complete).datasetSessionId)
        assertEquals(2L, result.exportedRows)

        // Verify ZIP contents
        var foundCsv = false
        var foundManifest = false
        var manifestContent: String? = null
        var csvContent: String? = null

        ZipInputStream(outputFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                when (entry.name) {
                    AndroidSensorDatasetPackageExporter.CSV_ENTRY -> {
                        foundCsv = true
                        csvContent = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                    }
                    AndroidSensorDatasetPackageExporter.MANIFEST_ENTRY -> {
                        foundManifest = true
                        manifestContent = zis.bufferedReader(StandardCharsets.UTF_8).readText()
                    }
                }
                zis.closeEntry()
            }
        }

        assertTrue("ZIP must contain CSV entry", foundCsv)
        assertTrue("ZIP must contain manifest entry", foundManifest)
        assertNotNull(manifestContent)
        assertNotNull(csvContent)

        // 1. Check CSV content: uses public random dataset ID, never internal session ID
        assertTrue(csvContent!!.contains(fixedDatasetId))
        assertFalse(csvContent!!.contains("ride-internal-123"))

        // 2. Check Manifest content
        assertTrue(manifestContent!!.contains("\"package_schema_version\":1"))
        assertTrue(manifestContent!!.contains("\"dataset_session_id\":\"$fixedDatasetId\""))
        assertTrue(manifestContent!!.contains("\"recording_provenance\":\"REAL\""))
        assertFalse(manifestContent!!.contains("ride-internal-123"))

        // 3. Strictly verify absence of PII / GPS
        val forbidden = listOf("gps", "latitude", "longitude", "contacts", "phone", "medical", "rider_name")
        for (f in forbidden) {
            assertFalse("Manifest must not contain $f", manifestContent!!.contains(f, ignoreCase = true))
        }

        // 4. Verify quality stats block
        assertTrue(manifestContent!!.contains("\"recording_quality\":{"))
        assertTrue(manifestContent!!.contains("\"event_count\":2"))
        assertTrue(manifestContent!!.contains("\"dropped_samples\":null"))
        assertTrue(manifestContent!!.contains("\"dropped_samples_unavailable_reason\":\"per-ride dropped counts are not persisted\""))
        assertTrue(manifestContent!!.contains("\"sensor_content_hash\":\"sha256:"))
    }

    private fun readManifest(zipFile: java.io.File): String {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == AndroidSensorDatasetPackageExporter.MANIFEST_ENTRY) {
                    return zis.bufferedReader(StandardCharsets.UTF_8).readText()
                }
                zis.closeEntry()
            }
        }
        throw AssertionError("Manifest entry not found in ${zipFile.name}")
    }

    private fun extractContentHash(manifest: String): String {
        val pattern = """"sensor_content_hash":\s*"([^"]+)"""".toRegex()
        val match = pattern.find(manifest)
        return match?.groupValues?.get(1) ?: throw AssertionError("sensor_content_hash not found in manifest")
    }

    @Test
    fun `exporting same ride twice produces distinct dataset IDs but identical sensor content hash`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents().apply {
            add("ride-repeat", RawSensorType.ACCELEROMETER, 1_000_000_000L)
            add("ride-repeat", RawSensorType.GYROSCOPE, 1_000_000_000L)
        }
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-repeat"] = testSession("ride-repeat", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)

        val pkg1 = tempFolder.newFile("repeat_export_1.zip")
        val pkg2 = tempFolder.newFile("repeat_export_2.zip")

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = { dest ->
                if (dest.contains("repeat_export_1")) FileOutputStream(pkg1) else FileOutputStream(pkg2)
            }
        )

        val res1 = exporter.export("ride-repeat", pkg1.absolutePath, "REAL") as DatasetPackageExportResult.Complete
        val res2 = exporter.export("ride-repeat", pkg2.absolutePath, "REAL") as DatasetPackageExportResult.Complete

        // Dataset IDs must be distinct random UUIDs
        assertFalse(res1.datasetSessionId == res2.datasetSessionId)

        val manifest1 = readManifest(pkg1)
        val manifest2 = readManifest(pkg2)

        val hash1 = extractContentHash(manifest1)
        val hash2 = extractContentHash(manifest2)

        assertTrue(hash1.startsWith("sha256:"))
        assertEquals(hash1, hash2)
    }

    @Test
    fun `exporting different rides produces different sensor content hashes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents().apply {
            add("ride-A", RawSensorType.ACCELEROMETER, 1_000_000_000L)
            add("ride-B", RawSensorType.ACCELEROMETER, 2_000_000_000L)
        }
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-A"] = testSession("ride-A", RideSessionStatus.COMPLETED)
            sessions["ride-B"] = testSession("ride-B", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)

        val pkgA = tempFolder.newFile("ride_a.zip")
        val pkgB = tempFolder.newFile("ride_b.zip")

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = { dest ->
                if (dest.contains("ride_a")) FileOutputStream(pkgA) else FileOutputStream(pkgB)
            }
        )

        exporter.export("ride-A", pkgA.absolutePath, "REAL")
        exporter.export("ride-B", pkgB.absolutePath, "REAL")

        val hashA = extractContentHash(readManifest(pkgA))
        val hashB = extractContentHash(readManifest(pkgB))

        assertFalse(hashA == hashB)
    }

    @Test
    fun `withdrawing consent during active export aborts export immediately`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents().apply {
            for (i in 1..20) {
                add("ride-long", RawSensorType.ACCELEROMETER, i * 1_000_000L)
            }
        }
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-long"] = testSession("ride-long", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher, pageSize = 5)
        val abortedFile = tempFolder.newFile("aborted.zip")
        var progressCount = 0

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = { FileOutputStream(abortedFile) }
        )

        try {
            exporter.export("ride-long", abortedFile.absolutePath, "REAL", onProgress = {
                progressCount++
                if (progressCount >= 2) {
                    consent.withdraw()
                }
            })
            fail("Expected export to abort when consent is withdrawn mid-stream")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("consent is required") == true)
        }
        assertFalse("Destination file must be cleaned up when export is aborted", abortedFile.exists())
    }

    @Test
    fun `export failure from IO error cleans up partial file at destination`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents().apply {
            add("ride-io-err", RawSensorType.ACCELEROMETER, 1_000_000_000L)
        }
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-io-err"] = testSession("ride-io-err", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)
        val targetFile = tempFolder.newFile("failing.zip")

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent,
            openOutputStream = {
                object : java.io.OutputStream() {
                    override fun write(b: Int) { throw java.io.IOException("Simulated disk error") }
                    override fun write(b: ByteArray, off: Int, len: Int) { throw java.io.IOException("Simulated disk error") }
                }
            }
        )

        try {
            exporter.export("ride-io-err", targetFile.absolutePath, "REAL")
            fail("Expected export failure from IO error")
        } catch (e: java.io.IOException) {
            assertTrue(e.message?.contains("Simulated disk error") == true)
        }

        assertFalse("Destination file must be cleaned up on failure", targetFile.exists())
    }

    @Test
    fun `export on empty recording cleans up destination file`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val consentStore = FakeConsentStore().apply { grant(1_700_000_000_000L) }
        val consent = DatasetContributionConsent(consentStore)
        val sensorRepo = FakeSensorEvents()
        val rideRepo = FakeRideSessions().apply {
            sessions["ride-empty"] = testSession("ride-empty", RideSessionStatus.COMPLETED)
        }
        val csvExporter = SensorCsvExporter(repository = sensorRepo, ioDispatcher = testDispatcher)
        val targetFile = tempFolder.newFile("empty.zip")
        assertTrue(targetFile.exists())

        val exporter = AndroidSensorDatasetPackageExporter(
            sensorEvents = sensorRepo,
            rides = rideRepo,
            csvExporter = csvExporter,
            consent = consent
        )

        val result = exporter.export("ride-empty", targetFile.absolutePath, "REAL")
        assertEquals(DatasetPackageExportResult.Empty, result)
        assertFalse("Destination file must be cleaned up when recording has 0 events", targetFile.exists())
    }
}
