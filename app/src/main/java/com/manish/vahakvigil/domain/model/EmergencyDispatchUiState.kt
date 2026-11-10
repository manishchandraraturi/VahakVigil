package com.manish.vahakvigil.domain.model

/**
 * High-level user interface and dispatch lifecycle state for emergency response.
 *
 * Represents the progression of an emergency candidate from the pre-expiration countdown
 * through post-expiration dispatch execution and final resolution.
 */
enum class EmergencyDispatchUiState {
    /** No active emergency candidate event. */
    IDLE,

    /** Qualified incident and stillness confirmed; 75-second countdown active. */
    COUNTING_DOWN,

    /** User manually cancelled countdown via "I'm Fine" or system back before expiration. */
    CANCELLED,

    /** 75-second countdown reached zero; dispatch payload compilation / handoff in progress. */
    EXPIRED,

    /** Emergency action orchestrator is transmitting dispatch requests. */
    DISPATCHING,

    /** Emergency alert successfully triggered for one or more emergency contacts. */
    DISPATCH_TRIGGERED,

    /** Emergency action failed (e.g., no contacts, missing permissions, or telephony error). */
    DISPATCH_FAILED,

    /** SMS API outcome was ambiguous; this incident is not resent automatically. */
    DISPATCH_UNKNOWN
}
