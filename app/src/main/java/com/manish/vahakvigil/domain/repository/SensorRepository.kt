package com.manish.vahakvigil.domain.repository

import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain interface for hardware sensor monitoring.
 *
 * Provides a clean boundary decoupling ViewModels from Android's [android.hardware.SensorManager].
 */
interface SensorRepository {
    val capabilities: SensorCapabilities
    val isMonitoring: StateFlow<Boolean>
    val sensorReadings: Flow<SensorReading>

    /**
     * Explicitly registers hardware sensor listeners and begins emitting readings.
     */
    fun startMonitoring()

    /**
     * Explicitly unregisters hardware sensor listeners and stops emission to save battery.
     */
    fun stopMonitoring()
}
