package com.manish.vahakvigil.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.manish.vahakvigil.domain.model.SensorCapabilities
import com.manish.vahakvigil.domain.model.SensorReading
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android implementation of [SensorDataSource] wrapping [SensorManager].
 *
 * Responsibilities:
 * - Detects presence of accelerometer and gyroscope sensors.
 * - Manages explicit sensor listener lifecycle.
 * - Converts raw hardware [SensorEvent]s into immutable domain [SensorReading]s.
 * - Ensures listeners are cleanly unregistered when monitoring stops to preserve battery.
 */
class AndroidSensorDataSource(
    context: Context
) : SensorDataSource, SensorEventListener {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    override val capabilities = SensorCapabilities(
        hasAccelerometer = accelerometer != null,
        hasGyroscope = gyroscope != null
    )

    private val _sensorReadings = MutableSharedFlow<SensorReading>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val sensorReadings: Flow<SensorReading> = _sensorReadings.asSharedFlow()

    @Volatile
    private var _isListening: Boolean = false
    override val isListening: Boolean
        get() = _isListening

    // Thread-safe caches for the latest readings to produce synchronized snapshots
    @Volatile private var latestAx = 0f
    @Volatile private var latestAy = 0f
    @Volatile private var latestAz = 0f
    @Volatile private var latestGx = 0f
    @Volatile private var latestGy = 0f
    @Volatile private var latestGz = 0f

    @Synchronized
    override fun startListening() {
        if (_isListening || sensorManager == null) return

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        _isListening = true
    }

    @Synchronized
    override fun stopListening() {
        if (!_isListening) return
        sensorManager?.unregisterListener(this)
        _isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isListening) return

        val currentTimestamp = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAx = event.values[0]
                latestAy = event.values[1]
                latestAz = event.values[2]

                // Accelerometer event triggers a new snapshot
                val reading = SensorReading(
                    timestamp = currentTimestamp,
                    ax = latestAx,
                    ay = latestAy,
                    az = latestAz,
                    gx = latestGx,
                    gy = latestGy,
                    gz = latestGz
                )
                _sensorReadings.tryEmit(reading)
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGx = event.values[0]
                latestGy = event.values[1]
                latestGz = event.values[2]

                // If device lacks accelerometer (unusual), gyroscope can also drive snapshot
                if (accelerometer == null) {
                    val reading = SensorReading(
                        timestamp = currentTimestamp,
                        ax = latestAx,
                        ay = latestAy,
                        az = latestAz,
                        gx = latestGx,
                        gy = latestGy,
                        gz = latestGz
                    )
                    _sensorReadings.tryEmit(reading)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for now; accuracy flags can be incorporated in future calibration stages
    }
}
