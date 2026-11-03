package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.CandidateAccidentEvent
import com.manish.vahakvigil.domain.model.CandidateEventWindow
import com.manish.vahakvigil.domain.model.ConfidenceScore
import com.manish.vahakvigil.domain.model.EventQualificationConfig
import com.manish.vahakvigil.domain.model.QualifiedIncident

/**
 * Pure Kotlin deterministic qualification engine.
 *
 * Sits downstream of [ConfidenceScorer] and evaluates whether [CandidateAccidentEvent]s
 * provide sufficiently persistent evidence over multiple consecutive windows within a
 * configured time span to produce a [QualifiedIncident].
 *
 * ## Responsibilities
 * - Requires [EventQualificationConfig.requiredConsecutiveWindows] consecutive candidate
 *   events with confidence score >= [EventQualificationConfig.minimumConfidenceScore].
 * - Enforces that consecutive qualifying events span no longer than
 *   [EventQualificationConfig.qualificationWindowMillis].
 * - Resets consecutive counts upon encountering a non-qualifying candidate or gap.
 * - Prevents duplicate qualification emissions for the same ongoing qualified sequence.
 * - Provides full state reset capability for clean session transitions.
 *
 * ## Android independence
 * Completely independent of Android framework APIs. Fully unit-testable on a plain JVM.
 *
 * @param config Qualification rules configuration. Defaults to [EventQualificationConfig.DEFAULT].
 */
class EventQualificationEngine(
    val config: EventQualificationConfig = EventQualificationConfig.DEFAULT
) {

    private val consecutiveCandidates = mutableListOf<CandidateAccidentEvent>()
    private var hasEmittedForCurrentSequence = false

    /**
     * Evaluates an immutable window of candidate events in one pass.
     * Useful for batch analysis and deterministic unit testing.
     */
    fun evaluate(window: CandidateEventWindow): QualifiedIncident? {
        if (window.isEmpty) return null
        val tempEngine = EventQualificationEngine(config)
        var result: QualifiedIncident? = null
        for (event in window.events) {
            val incident = tempEngine.process(event)
            if (incident != null) {
                result = incident
            }
        }
        return result
    }

    /**
     * Processes the next [CandidateAccidentEvent] in the stream.
     *
     * @param candidate The candidate accident event from the confidence scorer, or null
     *                  if the current window had no significant motion sequence.
     * @return A [QualifiedIncident] once when qualification criteria are satisfied, or null.
     */
    fun process(candidate: CandidateAccidentEvent?): QualifiedIncident? {
        // A null candidate or candidate below minimum confidence score breaks consecutive sequence
        if (candidate == null || candidate.confidenceScore.value < config.minimumConfidenceScore) {
            resetSequence()
            return null
        }

        // Defensive check: invalid negative duration in candidate window
        if (candidate.windowEndMs < candidate.windowStartMs) {
            resetSequence()
            return null
        }

        // Check if timestamps are out of order relative to the preceding candidate
        if (consecutiveCandidates.isNotEmpty() &&
            candidate.windowStartMs < consecutiveCandidates.last().windowStartMs
        ) {
            resetSequence()
        }

        consecutiveCandidates.add(candidate)

        // Evict candidates from the front that fall outside the qualificationWindowMillis
        while (consecutiveCandidates.size > 1 &&
            (candidate.windowEndMs - consecutiveCandidates.first().windowStartMs) > config.qualificationWindowMillis
        ) {
            consecutiveCandidates.removeAt(0)
            // Once earlier candidates are pruned due to time window expiration,
            // the previous sequence has expired and is no longer locked into emitted state
            hasEmittedForCurrentSequence = false
        }

        val sequenceDuration = candidate.windowEndMs - consecutiveCandidates.first().windowStartMs
        val isWithinTimeWindow = sequenceDuration <= config.qualificationWindowMillis

        if (consecutiveCandidates.size >= config.requiredConsecutiveWindows && isWithinTimeWindow) {
            if (!hasEmittedForCurrentSequence) {
                hasEmittedForCurrentSequence = true
                val qualifyingSubset = consecutiveCandidates.takeLast(config.requiredConsecutiveWindows)
                val peakScore = qualifyingSubset.maxOf { it.confidenceScore.value }

                return QualifiedIncident(
                    windowStartMs = qualifyingSubset.first().windowStartMs,
                    windowEndMs = qualifyingSubset.last().windowEndMs,
                    qualifyingConfidenceScore = ConfidenceScore.fromRaw(peakScore),
                    qualifyingWindowCount = qualifyingSubset.size,
                    candidateEvents = qualifyingSubset.toList()
                )
            } else {
                // Duplicate qualification prevented: sequence already qualified and emitted
                return null
            }
        }

        return null
    }

    private fun resetSequence() {
        consecutiveCandidates.clear()
        hasEmittedForCurrentSequence = false
    }

    /**
     * Resets all internal qualification state.
     * Must be called when monitoring stops or when starting a fresh session.
     */
    fun reset() {
        resetSequence()
    }
}
