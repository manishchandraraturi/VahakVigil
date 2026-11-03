package com.manish.vahakvigil.sensor

import com.manish.vahakvigil.data.sensor.SensorDataSource
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory test double for [SensorDataSource].
 * Enables testing sensor repositories and ViewModels without a physical Android device.
 */
class FakeSensorDataSource(
    override val capabilities: SensorCapabilities = SensorCapabilities(
        hasAccelerometer = true,
        hasGyroscope = true
    )
) : SensorDataSource {

    private val _readings = MutableSharedFlow<SensorReading>(extraBufferCapacity = 64)
    override val sensorReadings: Flow<SensorReading> = _readings.asSharedFlow()
    val subscriptionCount: StateFlow<Int> = _readings.subscriptionCount

    private var _isListening: Boolean = false
    override val isListening: Boolean
        get() = _isListening

    override fun startListening() {
        _isListening = true
    }

    override fun stopListening() {
        _isListening = false
    }

    fun emitReading(reading: SensorReading) {
        if (_isListening) {
            _readings.tryEmit(reading)
        }
    }
}
