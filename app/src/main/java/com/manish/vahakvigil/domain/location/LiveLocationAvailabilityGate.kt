package com.manish.vahakvigil.domain.location

import com.manish.vahakvigil.domain.model.RideLocationState

/** Prevents an initial Play Services availability=false report from masquerading as lost signal. */
class LiveLocationAvailabilityGate {
    private var lastValidFixElapsedRealtimeMs: Long? = null

    fun onValidFix(elapsedRealtimeMs: Long = 0L) {
        lastValidFixElapsedRealtimeMs = elapsedRealtimeMs
    }

    fun onAvailabilityChanged(isAvailable: Boolean): RideLocationState? =
        if (!isAvailable && lastValidFixElapsedRealtimeMs != null) RideLocationState.LocationTemporarilyUnavailable else null

    fun onWatchdog(
        nowElapsedRealtimeMs: Long,
        requestStartedElapsedRealtimeMs: Long,
        initialFixTimeoutMs: Long,
        staleFixTimeoutMs: Long
    ): RideLocationState? {
        val lastFixAt = lastValidFixElapsedRealtimeMs
        return when {
            lastFixAt == null && nowElapsedRealtimeMs - requestStartedElapsedRealtimeMs >= initialFixTimeoutMs ->
                RideLocationState.LocationUnavailable
            lastFixAt != null && nowElapsedRealtimeMs - lastFixAt >= staleFixTimeoutMs ->
                RideLocationState.LocationTemporarilyUnavailable
            else -> null
        }
    }
}
