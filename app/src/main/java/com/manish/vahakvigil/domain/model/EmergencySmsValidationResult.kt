package com.manish.vahakvigil.domain.model

/**
 * Status classification of emergency SMS message validation.
 */
enum class EmergencySmsValidationStatus {
    VALID,
    REQUIRES_MULTIPART,
    INVALID
}

/**
 * Pure Kotlin domain model representing the result of validating an [EmergencySmsMessage].
 *
 * @param status Overall validation status ([EmergencySmsValidationStatus.VALID],
 *               [EmergencySmsValidationStatus.REQUIRES_MULTIPART], or [EmergencySmsValidationStatus.INVALID]).
 * @param reason Explanatory rationale, especially for invalid messages or multipart requirements.
 */
data class EmergencySmsValidationResult(
    val status: EmergencySmsValidationStatus,
    val reason: String = ""
) {
    val isValid: Boolean get() = status != EmergencySmsValidationStatus.INVALID

    companion object {
        fun valid() = EmergencySmsValidationResult(EmergencySmsValidationStatus.VALID)

        fun requiresMultipart(segmentCount: Int) = EmergencySmsValidationResult(
            EmergencySmsValidationStatus.REQUIRES_MULTIPART,
            "Message requires $segmentCount SMS segments"
        )

        fun invalid(reason: String) = EmergencySmsValidationResult(
            EmergencySmsValidationStatus.INVALID,
            reason
        )
    }
}
