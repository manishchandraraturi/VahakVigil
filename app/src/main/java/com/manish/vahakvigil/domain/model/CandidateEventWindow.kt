package com.manish.vahakvigil.domain.model

/**
 * An ordered, immutable collection of recent [CandidateAccidentEvent] values.
 *
 * Provides a bounded temporal window of candidate accident events for evaluation
 * by [com.manish.vahakvigil.domain.analyzer.EventQualificationEngine].
 *
 * ## Constraints
 *
 * - Chronologically ordered (first event is oldest, last is newest).
 * - Bounded in memory.
 * - Pure Kotlin with zero Android framework dependencies.
 *
 * @param events Chronologically ordered list of candidate events in the window.
 */
data class CandidateEventWindow(
    val events: List<CandidateAccidentEvent>
) {
    /** Returns true if there are no candidate events in this window. */
    val isEmpty: Boolean get() = events.isEmpty()

    /** Number of candidate events in the window. */
    val size: Int get() = events.size

    /** Start timestamp of the oldest candidate event, or null if empty. */
    val startTimestamp: Long? get() = events.firstOrNull()?.windowStartMs

    /** End timestamp of the newest candidate event, or null if empty. */
    val endTimestamp: Long? get() = events.lastOrNull()?.windowEndMs
}
