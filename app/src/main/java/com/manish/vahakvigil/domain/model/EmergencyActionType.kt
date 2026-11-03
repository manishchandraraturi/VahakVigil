package com.manish.vahakvigil.domain.model

/**
 * Pure Kotlin representation of prospective emergency actions.
 *
 * These represent future capabilities (location capture, SMS notification,
 * phone calls). In this milestone, they are contracts/types only and are
 * NOT executed against physical device APIs.
 */
enum class EmergencyActionType {
    LOCATION,
    SMS,
    PHONE_CALL
}
