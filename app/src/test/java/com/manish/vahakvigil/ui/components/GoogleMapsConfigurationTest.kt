package com.manish.vahakvigil.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleMapsConfigurationTest {
    @Test
    fun `missing metadata is reported as not configured`() {
        assertFalse(GoogleMapsConfiguration.hasApiKey(null))
        assertFalse(GoogleMapsConfiguration.hasApiKey("  "))
    }

    @Test
    fun `nonblank injected metadata is configured`() {
        assertTrue(GoogleMapsConfiguration.hasApiKey("restricted-key"))
    }
}
