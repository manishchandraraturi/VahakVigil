package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.RawSensorEvent
import kotlinx.coroutines.flow.Flow

/** Read-only stream of individual hardware samples and the number rejected by its bounded buffer. */
interface RawSensorEventSource {
    val rawSensorEvents: Flow<RawSensorEvent>
    val droppedEventCount: Long

    /** Drains events not yet received by the single stream collector; call only when it is stopped. */
    fun drainPendingEvents(): List<RawSensorEvent> = emptyList()
}
