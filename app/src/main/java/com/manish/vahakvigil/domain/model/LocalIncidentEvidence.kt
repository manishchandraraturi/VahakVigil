package com.manish.vahakvigil.domain.model

/** Sensitive, local-only incident metadata. File references are never included in SMS formatting. */
data class LocalIncidentEvidence(
    val incidentTimestampMs: Long,
    val voiceNote: String? = null,
    val photoReference: String? = null
)
