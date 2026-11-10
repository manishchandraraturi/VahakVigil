package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.data.repository.SensorRepositoryImpl
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorRepositoryLifecycleTest {
    @Test
    fun `repeated monitoring transitions own exactly one listener registration`() {
        val source = CountingSensorDataSource()
        val repository = SensorRepositoryImpl(source)

        repository.startMonitoring()
        repository.startMonitoring()
        repository.startMonitoring()

        assertTrue(repository.isMonitoring.value)
        assertEquals(1, source.startCount)
        assertEquals(0, source.stopCount)

        repository.stopMonitoring()
        repository.stopMonitoring()

        assertFalse(repository.isMonitoring.value)
        assertEquals(1, source.startCount)
        assertEquals(1, source.stopCount)

        repository.startMonitoring()
        assertEquals(2, source.startCount)
    }

    private class CountingSensorDataSource : SensorDataSource {
        override val capabilities = SensorCapabilities(hasAccelerometer = true, hasGyroscope = true)
        override val sensorReadings: Flow<SensorReading> = emptyFlow()
        private var listening = false
        var startCount = 0
            private set
        var stopCount = 0
            private set
        override val isListening: Boolean get() = listening

        override fun startListening() {
            if (!listening) {
                listening = true
                startCount++
            }
        }

        override fun stopListening() {
            if (listening) {
                listening = false
                stopCount++
            }
        }
    }
}
