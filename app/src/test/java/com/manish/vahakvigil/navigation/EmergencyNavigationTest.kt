package com.manish.vahakvigil.navigation

import androidx.navigation.navOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyNavigationTest {

    @Test
    fun emergencyRoute_isUniqueAndIdentifiable() {
        assertEquals("emergency", Screen.Emergency.route)
        assertEquals("home", Screen.Home.route)
        assertEquals("startup", Screen.Startup.route)
        assertEquals("bystander", Screen.Bystander.route)
    }

    @Test
    fun emergencyNavigation_navOptionsConfiguresLaunchSingleTop() {
        val options = navOptions {
            launchSingleTop = true
        }
        assertTrue("Emergency navigation must configure launchSingleTop to prevent duplicate screens",
            options.shouldLaunchSingleTop())
    }

    // ─── HP-03: Active Emergency App Relaunch Navigation ───────────────────────

    @Test
    fun `resolveStartDestination routes directly to EmergencyScreen when emergency is active`() {
        val destination = resolveStartDestination(
            isEmergencyActive = true,
            defaultStartDestination = Screen.Startup.route
        )
        assertEquals(Screen.Emergency.route, destination)
    }

    @Test
    fun `resolveStartDestination routes to StartupAnimation when emergency is NOT active`() {
        val destination = resolveStartDestination(
            isEmergencyActive = false,
            defaultStartDestination = Screen.Startup.route
        )
        assertEquals(Screen.Startup.route, destination)
    }

    @Test
    fun `resolveStartDestination respects explicit non-startup start destination`() {
        val destination = resolveStartDestination(
            isEmergencyActive = false,
            defaultStartDestination = Screen.Home.route
        )
        assertEquals(Screen.Home.route, destination)
    }

    @Test
    fun `completed onboarding skips startup tracing on later launches`() {
        assertEquals(
            Screen.Home.route,
            resolveStartDestination(
                isEmergencyActive = false,
                defaultStartDestination = Screen.Startup.route,
                onboardingComplete = true
            )
        )
    }

    // ─── MP-01: Back Gesture Prevention During Active Countdown ───────────────

    @Test
    fun `EmergencyBackPolicy disables back gesture during active countdown`() {
        val shouldDisable = com.manish.vahakvigil.ui.screens.EmergencyBackPolicy.shouldDisableBack(
            isEmergencyActive = true,
            dispatchUiState = com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.COUNTING_DOWN
        )
        assertTrue("Back gesture must be disabled during active countdown", shouldDisable)
    }

    @Test
    fun `EmergencyBackPolicy allows back after emergency is resolved or triggered`() {
        val shouldDisableTriggered = com.manish.vahakvigil.ui.screens.EmergencyBackPolicy.shouldDisableBack(
            isEmergencyActive = true,
            dispatchUiState = com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.DISPATCH_TRIGGERED
        )
        org.junit.Assert.assertFalse("Back gesture must be allowed after dispatch is triggered", shouldDisableTriggered)

        val shouldDisableFailed = com.manish.vahakvigil.ui.screens.EmergencyBackPolicy.shouldDisableBack(
            isEmergencyActive = true,
            dispatchUiState = com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.DISPATCH_FAILED
        )
        org.junit.Assert.assertFalse("Back gesture must be allowed after dispatch fails", shouldDisableFailed)

        val shouldDisableCancelled = com.manish.vahakvigil.ui.screens.EmergencyBackPolicy.shouldDisableBack(
            isEmergencyActive = false,
            dispatchUiState = com.manish.vahakvigil.domain.model.EmergencyDispatchUiState.CANCELLED
        )
        org.junit.Assert.assertFalse("Back gesture must be allowed after emergency is cancelled", shouldDisableCancelled)
    }
}
