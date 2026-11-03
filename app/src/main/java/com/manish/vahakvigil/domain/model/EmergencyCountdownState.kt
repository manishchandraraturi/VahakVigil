package com.manish.vahakvigil.domain.model

/**
 * Lifecycle states of the emergency cancellation countdown.
 */
enum class EmergencyCountdownState {
    /** Countdown has not been triggered or has been reset to standby. */
    IDLE,

    /** Countdown is currently in progress, counting down toward zero. */
    COUNTING_DOWN,

    /** Countdown was explicitly cancelled by the user. */
    CANCELLED,

    /** Countdown reached zero without user cancellation. */
    EXPIRED
}
