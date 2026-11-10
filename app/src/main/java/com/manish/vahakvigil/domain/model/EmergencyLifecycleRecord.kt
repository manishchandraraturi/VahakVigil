package com.manish.vahakvigil.domain.model

enum class EmergencyLifecyclePhase { COUNTING_DOWN, DISPATCHING, DISPATCH_TRIGGERED, DISPATCH_FAILED, DISPATCH_UNKNOWN, CANCELLED, COMPLETED }
enum class EmergencyDispatchAttemptState { NOT_STARTED, ATTEMPTED, SENT, FAILED, UNKNOWN }
enum class EmergencyUserResponse { NONE, NEED_HELP, IM_FINE }

/** Durable recovery data only; dispatch payloads, contacts, message bodies and medical data stay out. */
data class EmergencyLifecycleRecord(
    val incidentId: String,
    val incidentCreatedAtEpochMillis: Long,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long,
    val confidenceScore: Float,
    val phase: EmergencyLifecyclePhase,
    val deadlineElapsedRealtimeMillis: Long,
    val deadlineEpochMillis: Long,
    val bootCount: Int,
    val userResponse: EmergencyUserResponse = EmergencyUserResponse.NONE,
    val dispatchState: EmergencyDispatchAttemptState = EmergencyDispatchAttemptState.NOT_STARTED,
    val dispatchAttemptCount: Int = 0,
    val dispatchAttemptedAtEpochMillis: Long? = null,
    val dispatchResolvedAtEpochMillis: Long? = null,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastLocationTimestampEpochMillis: Long? = null
) {
    init {
        require(incidentId.isNotBlank())
        require(confidenceScore.isFinite() && confidenceScore in 0f..1f)
        require((lastLatitude == null) == (lastLongitude == null))
        if (lastLatitude != null && lastLongitude != null) require(EmergencyLocation.isValidCoordinate(lastLatitude, lastLongitude))
    }
}

interface EmergencyLifecycleClock {
    fun wallClockMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun bootCount(): Int
}

object JvmEmergencyLifecycleClock : EmergencyLifecycleClock {
    override fun wallClockMillis() = System.currentTimeMillis()
    override fun elapsedRealtimeMillis() = System.nanoTime() / 1_000_000L
    override fun bootCount() = -1
}

object EmergencyCountdownRecovery {
    fun remainingMillis(
        record: EmergencyLifecycleRecord,
        wallClockMillis: Long,
        elapsedRealtimeMillis: Long,
        bootCount: Int,
        durationMillis: Long = 75_000L
    ): Long {
        val remaining = if (record.bootCount >= 0 && bootCount == record.bootCount) {
            record.deadlineElapsedRealtimeMillis - elapsedRealtimeMillis
        } else {
            if (wallClockMillis < record.incidentCreatedAtEpochMillis) 0L
            else record.deadlineEpochMillis - wallClockMillis
        }
        return remaining.coerceIn(0L, durationMillis)
    }
}
