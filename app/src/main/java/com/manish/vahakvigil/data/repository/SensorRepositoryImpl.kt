package com.manish.vahakvigil.data.repository

import com.manish.vahakvigil.data.sensor.SensorDataSource
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import com.manish.vahakvigil.domain.repository.SensorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of [SensorRepository] delegating to a [SensorDataSource].
 *
 * Keeps tracking of monitoring active state via [isMonitoring] StateFlow.
 */
class SensorRepositoryImpl(
    private val dataSource: SensorDataSource
) : SensorRepository {

    override val capabilities: SensorCapabilities
        get() = dataSource.capabilities

    override val sensorReadings: Flow<SensorReading>
        get() = dataSource.sensorReadings

    private val _isMonitoring = MutableStateFlow(false)
    override val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    @Synchronized
    override fun startMonitoring() {
        if (_isMonitoring.value) return
        dataSource.startListening()
        _isMonitoring.value = true
    }

    @Synchronized
    override fun stopMonitoring() {
        if (!_isMonitoring.value) return
        dataSource.stopListening()
        _isMonitoring.value = false
    }
}
