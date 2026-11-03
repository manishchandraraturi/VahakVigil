package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow

/**
 * Data source interface abstracting motion sensor access.
 */
interface SensorDataSource {
    val capabilities: SensorCapabilities
    val sensorReadings: Flow<SensorReading>
    val isListening: Boolean

    fun startListening()
    fun stopListening()
}
