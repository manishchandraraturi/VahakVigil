package com.manish.vahakvigil.feedback

import com.manish.vahakvigil.domain.feedback.EmergencyFeedbackController

/**
 * Test fake for [EmergencyFeedbackController] verifying lifecycle interactions.
 */
class FakeEmergencyFeedbackController : EmergencyFeedbackController {

    var isAlerting: Boolean = false
        private set

    var startAlertCallCount: Int = 0
        private set

    var stopAlertCallCount: Int = 0
        private set

    override fun startAlert() {
        isAlerting = true
        startAlertCallCount++
    }

    override fun stopAlert() {
        isAlerting = false
        stopAlertCallCount++
    }

    fun reset() {
        isAlerting = false
        startAlertCallCount = 0
        stopAlertCallCount = 0
    }
}
