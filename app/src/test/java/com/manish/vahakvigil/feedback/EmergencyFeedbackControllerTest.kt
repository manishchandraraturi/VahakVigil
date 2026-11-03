package com.manish.vahakvigil.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmergencyFeedbackControllerTest {

    private lateinit var feedbackController: FakeEmergencyFeedbackController

    @Before
    fun setUp() {
        feedbackController = FakeEmergencyFeedbackController()
    }

    @Test
    fun `initial state is not alerting with zero calls`() {
        assertFalse(feedbackController.isAlerting)
        assertEquals(0, feedbackController.startAlertCallCount)
        assertEquals(0, feedbackController.stopAlertCallCount)
    }

    @Test
    fun `startAlert sets isAlerting to true and increments start count`() {
        feedbackController.startAlert()

        assertTrue(feedbackController.isAlerting)
        assertEquals(1, feedbackController.startAlertCallCount)
        assertEquals(0, feedbackController.stopAlertCallCount)
    }

    @Test
    fun `stopAlert sets isAlerting to false and increments stop count`() {
        feedbackController.startAlert()
        feedbackController.stopAlert()

        assertFalse(feedbackController.isAlerting)
        assertEquals(1, feedbackController.startAlertCallCount)
        assertEquals(1, feedbackController.stopAlertCallCount)
    }

    @Test
    fun `reset clears state and call counts`() {
        feedbackController.startAlert()
        feedbackController.stopAlert()
        feedbackController.reset()

        assertFalse(feedbackController.isAlerting)
        assertEquals(0, feedbackController.startAlertCallCount)
        assertEquals(0, feedbackController.stopAlertCallCount)
    }
}
