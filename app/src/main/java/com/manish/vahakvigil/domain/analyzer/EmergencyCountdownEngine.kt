package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.EmergencyCountdownState

/**
 * Pure Kotlin, deterministic cancellation countdown engine.
 *
 * Drives the user cancellation countdown timer purely via explicit caller-supplied timestamps.
 * Does NOT depend on system clocks, Coroutines, Handlers, or Android CountDownTimer.
 *
 * @param durationMillis Total duration in milliseconds. Defaults to 75_000L (75 seconds).
 */
class EmergencyCountdownEngine(
    val durationMillis: Long = 75_000L
) {
    init {
        require(durationMillis > 0L) {
            "durationMillis must be positive, got $durationMillis"
        }
    }

    var state: EmergencyCountdownState = EmergencyCountdownState.IDLE
        private set

    var remainingMillis: Long = durationMillis
        private set

    var startTimestampMillis: Long? = null
        private set
    private var lastObservedTimestampMillis: Long? = null

    /**
     * Starts the countdown at [startTimestampMillis].
     * Transitions IDLE -> COUNTING_DOWN.
     * If already counting down, does NOT restart.
     */
    fun start(startTimestampMillis: Long) {
        if (state == EmergencyCountdownState.COUNTING_DOWN) return

        state = EmergencyCountdownState.COUNTING_DOWN
        this.startTimestampMillis = startTimestampMillis
        this.lastObservedTimestampMillis = startTimestampMillis
        this.remainingMillis = durationMillis
    }

    /** Restores an active countdown without changing its configured total duration. */
    fun restore(startTimestampMillis: Long, currentTimestampMillis: Long) {
        reset()
        start(startTimestampMillis)
        update(currentTimestampMillis)
    }

    /**
     * Updates the countdown given the current [currentTimestampMillis].
     *
     * Calculates elapsed time since start, clamps remainingMillis to [0, durationMillis],
     * and transitions to [EmergencyCountdownState.EXPIRED] when remainingMillis reaches 0.
     *
     * If out-of-order or earlier timestamps are provided, they are safely clamped so
     * remaining time never increases or becomes negative.
     */
    fun update(currentTimestampMillis: Long) {
        if (state != EmergencyCountdownState.COUNTING_DOWN) return
        val start = startTimestampMillis ?: return

        // Guard against out-of-order timestamps: do not allow elapsed time to decrease
        val effectiveCurrent = maxOf(lastObservedTimestampMillis ?: start, currentTimestampMillis)
        lastObservedTimestampMillis = effectiveCurrent

        val elapsed = (effectiveCurrent - start).coerceAtLeast(0L)
        remainingMillis = (durationMillis - elapsed).coerceIn(0L, durationMillis)

        if (remainingMillis <= 0L) {
            state = EmergencyCountdownState.EXPIRED
        }
    }

    /**
     * Cancels the countdown. Transitions COUNTING_DOWN -> CANCELLED.
     */
    fun cancel() {
        if (state == EmergencyCountdownState.COUNTING_DOWN) {
            state = EmergencyCountdownState.CANCELLED
        }
    }

    /**
     * Immediately expires the countdown. Transitions COUNTING_DOWN -> EXPIRED.
     * Sets remainingMillis to 0L.
     */
    fun expireImmediately() {
        if (state == EmergencyCountdownState.COUNTING_DOWN) {
            remainingMillis = 0L
            state = EmergencyCountdownState.EXPIRED
        }
    }

    /**
     * Resets the countdown back to [EmergencyCountdownState.IDLE] with full remaining duration.
     */
    fun reset() {
        state = EmergencyCountdownState.IDLE
        remainingMillis = durationMillis
        startTimestampMillis = null
        lastObservedTimestampMillis = null
    }
}
