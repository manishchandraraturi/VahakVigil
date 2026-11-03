package com.manish.vahakvigil.domain.model

/** Honest summary derived only from the persisted ride and recorded GPS point count. */
data class RideCompletionSummary(
    val session: RideSession,
    val gpsPointCount: Int
) {
    val isFinalized: Boolean get() = session.endedAtEpochMillis != null
    val hasIncompleteGps: Boolean
        get() = session.status == RideSessionStatus.INTERRUPTED ||
            session.status == RideSessionStatus.INCOMPLETE || gpsPointCount < 2

    companion object {
        fun from(session: RideSession, gpsPointCount: Int) = RideCompletionSummary(session, gpsPointCount.coerceAtLeast(0))
    }
}
