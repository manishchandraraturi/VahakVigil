package com.manish.vahakvigil.domain.model

/** A read-only trace point emitted by the production pipeline when explicitly run in evaluation mode. */
data class DetectionEvaluationObservation(
    val timestampMillis: Long,
    val motionEvent: MotionEvent?,
    val temporalEvent: TemporalMotionEvent,
    val confidenceScore: ConfidenceScore,
    val candidateEvent: CandidateAccidentEvent?,
    val qualifiedIncident: QualifiedIncident?,
    val postImpactResult: PostImpactMotionResult?,
    val decision: EmergencyDecision
)
