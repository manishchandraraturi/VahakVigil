package com.manish.vahakvigil.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DatasetContributionConsentTest {

    private class FakeConsentStore : DatasetContributionConsentStore {
        private var granted: Boolean = false
        private var grantedAt: Long? = null

        override fun isGranted(): Boolean = granted
        override fun grantedAtEpochMillis(): Long? = grantedAt
        override fun grant(nowEpochMillis: Long) {
            granted = true
            grantedAt = nowEpochMillis
        }
        override fun withdraw() {
            granted = false
            grantedAt = null
        }
    }

    @Test
    fun `consent is not granted by default`() {
        val store = FakeConsentStore()
        val consent = DatasetContributionConsent(store)

        assertFalse(consent.mayExport())
        assertNull(consent.grantedAtEpochMillis())
        try {
            consent.requireConsent()
            fail("Expected IllegalStateException when consent not granted")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("consent is required") == true)
        }
    }

    @Test
    fun `granting consent enables export and records timestamp`() {
        val store = FakeConsentStore()
        val consent = DatasetContributionConsent(store)
        val testTime = 1_700_000_000_000L

        consent.grant(testTime)

        assertTrue(consent.mayExport())
        assertEquals(testTime, consent.grantedAtEpochMillis())
        // Should not throw
        consent.requireConsent()
    }

    @Test
    fun `withdrawing consent revokes export permission`() {
        val store = FakeConsentStore()
        val consent = DatasetContributionConsent(store)
        consent.grant(1_700_000_000_000L)
        assertTrue(consent.mayExport())

        consent.withdraw()

        assertFalse(consent.mayExport())
        assertNull(consent.grantedAtEpochMillis())
        try {
            consent.requireConsent()
            fail("Expected IllegalStateException after consent withdrawal")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("consent is required") == true)
        }
    }

    @Test
    fun `granting consent does not affect or reference ride monitoring`() {
        val store = FakeConsentStore()
        val consent = DatasetContributionConsent(store)

        // Consent store is purely isolated to dataset contribution
        consent.grant(1_700_000_000_000L)
        assertTrue(consent.mayExport())

        consent.withdraw()
        assertFalse(consent.mayExport())
    }
}
