package com.manish.vahakvigil.domain.model

/** Stable snapshot boundary and cursor for ordered sensor CSV export. */
data class SensorEventExportSnapshot(
    val eventCount: Long,
    val maxEventId: Long
)

data class SensorEventExportCursor(
    val timestampNanos: Long,
    val eventId: Long
)

/** Stable insertion-order cursor used to inspect recording-order quality without offset scans. */
data class SensorEventAnalysisCursor(val eventId: Long)

data class RecordedSensorEvent(
    val eventId: Long,
    val event: RawSensorEvent
)
