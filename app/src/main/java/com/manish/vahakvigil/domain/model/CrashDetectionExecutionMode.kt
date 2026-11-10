package com.manish.vahakvigil.domain.model

/** Controls only the emergency lifecycle side effects around production detection. */
enum class CrashDetectionExecutionMode {
    PRODUCTION,
    /** Runs the same detector and decision logic without starting a countdown or incident persistence. */
    EVALUATION
}
