package com.manish.vahakvigil.data.sensor

import com.manish.vahakvigil.domain.model.RawSensorEvent
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Non-blocking bounded handoff for sensor callbacks. Overflow drops the incoming sample. */
internal class BoundedRawSensorEventBuffer(capacity: Int) {
    private val channel = Channel<RawSensorEvent>(capacity = capacity)
    private val dropped = AtomicLong(0)

    val events: Flow<RawSensorEvent> = channel.receiveAsFlow()
    val droppedEventCount: Long get() = dropped.get()

    fun tryPublish(event: RawSensorEvent): Boolean {
        val result = channel.trySend(event)
        if (result.isFailure) dropped.incrementAndGet()
        return result.isSuccess
    }

    fun drainPendingEvents(): List<RawSensorEvent> = buildList {
        while (true) add(channel.tryReceive().getOrNull() ?: break)
    }
}
