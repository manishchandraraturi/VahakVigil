package com.manish.vahakvigil.ui.screens.startup

import com.manish.vahakvigil.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupAnimationScreenTest {
    @Test fun `startup route remains the app entry transition`() {
        assertEquals("startup", Screen.Startup.route)
    }

    @Test fun `line drawing traces the complete illustration at a deliberate pace`() {
        assertTrue(STARTUP_ANIMATION_TOTAL_MS in 5_000..7_000)
    }
}
