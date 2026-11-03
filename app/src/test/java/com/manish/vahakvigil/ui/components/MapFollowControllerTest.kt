package com.manish.vahakvigil.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFollowControllerTest {
    @Test
    fun `new GPS fixes are followed by default`() {
        val controller = MapFollowController()
        assertTrue(controller.shouldFollowNewFix(hasFix = true))
        assertFalse(controller.shouldFollowNewFix(hasFix = false))
    }

    @Test
    fun `manual gesture pauses follow until recenter is pressed`() {
        val controller = MapFollowController()
        controller.onGestureStarted()
        assertFalse(controller.shouldFollowNewFix(hasFix = true))
        controller.onRecenterPressed()
        assertTrue(controller.shouldFollowNewFix(hasFix = true))
    }
}
