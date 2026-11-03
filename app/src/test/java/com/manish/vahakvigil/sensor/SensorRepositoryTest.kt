package com.manish.vahakvigil.sensor

import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorRepositoryTest {

    @Test
    fun `capabilities reflect underlying data source`() {
        val customCapabilities = SensorCapabilities(hasAccelerometer = true, hasGyroscope = false)
        val dataSource = FakeSensorDataSource(customCapabilities)
        val repository = SensorRepositoryImpl(dataSource)

        assertEquals(customCapabilities, repository.capabilities)
        assertTrue(repository.capabilities.hasAccelerometer)
        assertFalse(repository.capabilities.hasGyroscope)
    }

    @Test
    fun `startMonitoring and stopMonitoring control listening lifecycle`() {
        val dataSource = FakeSensorDataSource()
        val repository = SensorRepositoryImpl(dataSource)

        assertFalse(repository.isMonitoring.value)
        assertFalse(dataSource.isListening)

        repository.startMonitoring()
        assertTrue(repository.isMonitoring.value)
        assertTrue(dataSource.isListening)

        repository.stopMonitoring()
        assertFalse(repository.isMonitoring.value)
        assertFalse(dataSource.isListening)
    }

    @Test
    fun `sensor readings stream through from data source to repository`() = runTest {
        val dataSource = FakeSensorDataSource()
        val repository = SensorRepositoryImpl(dataSource)

        repository.startMonitoring()

        val emittedReading = SensorReading(
            timestamp = 123456L,
            ax = 0f,
            ay = 9.8f,
            az = 0f,
            gx = 0.05f,
            gy = 0.01f,
            gz = 0f
        )

        var receivedReading: SensorReading? = null
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            receivedReading = repository.sensorReadings.first()
        }

        dataSource.emitReading(emittedReading)
        job.join()

        assertEquals(emittedReading, receivedReading)
    }
}
