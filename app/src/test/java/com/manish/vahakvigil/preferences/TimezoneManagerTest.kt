package com.manish.vahakvigil.preferences

import com.manish.vahakvigil.data.preferences.TimezoneManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimezoneManagerTest {

    @Test
    fun resolveZoneId_withValidZone_returnsExactZoneId() {
        val zone = TimezoneManager.resolveZoneId("Asia/Kolkata")
        assertEquals(ZoneId.of("Asia/Kolkata"), zone)
    }

    @Test
    fun resolveZoneId_withNull_returnsSystemDefault() {
        val zone = TimezoneManager.resolveZoneId(null)
        assertNotNull(zone)
    }

    @Test
    fun resolveZoneId_withInvalidZone_fallsBackToSystemDefault() {
        val zone = TimezoneManager.resolveZoneId("Invalid/NonExistent_Zone_123")
        assertNotNull(zone)
    }

    @Test
    fun formatDate_formatsCorrectlyForKnownInstant() {
        // 2026-09-20T14:30:00Z in UTC
        val instant = Instant.parse("2026-09-20T14:30:00Z")
        val zone = ZoneId.of("UTC")
        val formatted = TimezoneManager.formatDate(instant, zone)
        assertEquals("Sunday, 20 September", formatted)
    }

    @Test
    fun formatTime_formatsCorrectlyIn12HourFormat() {
        // 2026-09-20T14:30:00Z in UTC is 2:30 PM
        val instant = Instant.parse("2026-09-20T14:30:00Z")
        val zone = ZoneId.of("UTC")
        val formatted = TimezoneManager.formatTime(instant, zone)
        assertEquals("2:30 PM", formatted)
    }

    @Test
    fun formatTime_inAsiaKolkata_addsFiveAndHalfHours() {
        // 2026-09-20T14:30:00Z in Asia/Kolkata is 8:00 PM (20:00)
        val instant = Instant.parse("2026-09-20T14:30:00Z")
        val zone = ZoneId.of("Asia/Kolkata")
        val formatted = TimezoneManager.formatTime(instant, zone)
        assertEquals("8:00 PM", formatted)
    }

    @Test
    fun formatGreeting_morningAfternoonEveningLogic() {
        val zone = ZoneId.of("UTC")

        // 08:00 UTC -> Morning
        val morning = Instant.parse("2026-09-20T08:00:00Z")
        assertEquals("Good morning", TimezoneManager.formatGreeting(morning, zone))

        // 13:00 UTC -> Afternoon
        val afternoon = Instant.parse("2026-09-20T13:00:00Z")
        assertEquals("Good afternoon", TimezoneManager.formatGreeting(afternoon, zone))

        // 19:00 UTC -> Evening
        val evening = Instant.parse("2026-09-20T19:00:00Z")
        assertEquals("Good evening", TimezoneManager.formatGreeting(evening, zone))

        // 23:00 UTC -> Evening/Night
        val night = Instant.parse("2026-09-20T23:00:00Z")
        assertEquals("Good evening", TimezoneManager.formatGreeting(night, zone))
    }

    @Test
    fun formatRegionDetails_forIndia_returnsRegionAndOffset() {
        val instant = Instant.parse("2026-09-20T14:30:00Z")
        val zone = ZoneId.of("Asia/Kolkata")
        val (region, offset) = TimezoneManager.formatRegionDetails(instant, zone)

        assertEquals("India", region)
        assertTrue("Expected offset containing UTC+05:30 but got $offset", offset.contains("UTC+05:30"))
    }

    @Test
    fun supportedRegions_containsExpectedPredefinedRegions() {
        val regions = TimezoneManager.supportedRegions
        assertTrue(regions.isNotEmpty())
        assertTrue(regions.any { it.label == "India" && it.code == "IST" })
        assertTrue(regions.any { it.label.contains("System Default") })
        assertTrue(regions.any { it.label.contains("United States") })
    }
}
