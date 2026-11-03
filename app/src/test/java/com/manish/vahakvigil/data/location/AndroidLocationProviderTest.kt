package com.manish.vahakvigil.data.location

import com.manish.vahakvigil.domain.model.EmergencyLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused unit tests for [AndroidLocationProvider] verifying fresh GPS preference,
 * early/concurrent last-known location fallback, 5-second timeout safety, and cancellation.
 */
class AndroidLocationProviderTest {

    @Test
    fun `live updates reports disabled device location setting`() = runTest {
        val provider = AndroidLocationProvider().apply { locationEnabledChecker = { false } }
        assertEquals(com.manish.vahakvigil.domain.model.RideLocationState.GpsDisabled, provider.getLocationUpdates().first())
    }

    // ─── 1. Fresh location succeeds ──────────────────────────────────────────

    @Test
    fun `fresh location succeeds and is preferred over last known location`() = runTest {
        val freshLoc = EmergencyLocation.Available(12.9716, 77.5946, 3.0f, 1_700_000_000_000L)
        val lastLoc = EmergencyLocation.Available(13.0827, 80.2707, 15.0f, 1_699_999_000_000L)

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            freshLocationTimeoutMs = 500L
            totalLocationTimeoutMs = 1000L
            freshLocationFetcher = {
                delay(50)
                freshLoc
            }
            lastLocationFetcher = {
                delay(10)
                lastLoc
            }
        }

        val result = provider.getCurrentLocation()
        assertTrue("Must be Available location", result is EmergencyLocation.Available)
        val available = result as EmergencyLocation.Available
        assertEquals(12.9716, available.latitude, 0.0001)
        assertEquals(77.5946, available.longitude, 0.0001)
    }

    // ─── 2. Fresh location fails but last known location exists ──────────────

    @Test
    fun `fresh location fails but last known location exists falls back immediately`() = runTest {
        val lastLoc = EmergencyLocation.Available(13.0827, 80.2707, 10.0f, 1_699_999_000_000L)

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            freshLocationTimeoutMs = 500L
            totalLocationTimeoutMs = 1000L
            freshLocationFetcher = {
                delay(20)
                null // Fresh GPS failure
            }
            lastLocationFetcher = {
                delay(10)
                lastLoc
            }
        }

        val result = provider.getCurrentLocation()
        assertTrue("Must fall back to last known location", result is EmergencyLocation.Available)
        val available = result as EmergencyLocation.Available
        assertEquals(13.0827, available.latitude, 0.0001)
        assertEquals(80.2707, available.longitude, 0.0001)
    }

    // ─── 3. Fresh location remains pending and last known location exists ────

    @Test
    fun `fresh location remains pending and last known location exists falls back at fresh timeout`() = runTest {
        val lastLoc = EmergencyLocation.Available(28.6139, 77.2090, 8.0f, 1_699_999_000_000L)
        var freshCancelled = false

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            freshLocationTimeoutMs = 200L
            totalLocationTimeoutMs = 1000L
            freshLocationFetcher = {
                try {
                    delay(5000) // Simulating slow cold-start GPS fix (>200ms fresh timeout)
                    EmergencyLocation.Available(12.9716, 77.5946)
                } catch (e: CancellationException) {
                    freshCancelled = true
                    throw e
                }
            }
            lastLocationFetcher = {
                delay(20) // Cached location returns quickly
                lastLoc
            }
        }

        val result = provider.getCurrentLocation()
        assertTrue("Must fall back to last known location when fresh GPS times out", result is EmergencyLocation.Available)
        val available = result as EmergencyLocation.Available
        assertEquals(28.6139, available.latitude, 0.0001)
        assertEquals(77.2090, available.longitude, 0.0001)
        assertTrue("Fresh GPS coroutine must be cancelled upon fallback", freshCancelled)
    }

    // ─── 4. Neither location is available ────────────────────────────────────

    @Test
    fun `neither location is available returns EmergencyLocation Unavailable`() = runTest {
        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            freshLocationTimeoutMs = 100L
            totalLocationTimeoutMs = 300L
            freshLocationFetcher = {
                delay(20)
                null
            }
            lastLocationFetcher = {
                delay(20)
                null
            }
        }

        val result = provider.getCurrentLocation()
        assertEquals(EmergencyLocation.Unavailable, result)
    }

    // ─── 5. Coroutine cancellation ───────────────────────────────────────────

    @Test
    fun `coroutine cancellation cancels in-flight location queries and propagates`() = runTest {
        var freshCancellationNotified = false
        val jobStarted = CompletableDeferred<Unit>()

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            freshLocationTimeoutMs = 5000L
            totalLocationTimeoutMs = 5000L
            freshLocationFetcher = {
                jobStarted.complete(Unit)
                try {
                    delay(10000)
                    null
                } catch (e: CancellationException) {
                    freshCancellationNotified = true
                    throw e
                }
            }
            lastLocationFetcher = {
                try {
                    delay(10000)
                    null
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }

        val job = launch {
            provider.getCurrentLocation()
        }

        jobStarted.await()
        job.cancel()
        job.join()

        assertTrue("Cancellation must propagate", job.isCancelled)
        assertTrue("Fresh query must be cancelled on outer coroutine cancellation", freshCancellationNotified)
    }

    // ─── 6. Existing 5-second timeout behavior ───────────────────────────────

    @Test
    fun `existing 5-second emergency timeout is respected and returns Unavailable`() = runTest {
        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            // Using production timeout constants
            freshLocationTimeoutMs = 4500L
            totalLocationTimeoutMs = 5000L
            freshLocationFetcher = {
                delay(15000) // Hangs
                null
            }
            lastLocationFetcher = {
                delay(15000) // Hangs
                null
            }
        }

        val result = provider.getCurrentLocation()
        assertEquals("When all providers hang past 5s, safely returns Unavailable", EmergencyLocation.Unavailable, result)
    }

    // ─── 7. Permission failure ───────────────────────────────────────────────

    @Test
    fun `permission denial immediately returns Unavailable without querying location`() = runTest {
        var queryAttempted = false

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            permissionChecker = { _, _ -> false } // Denied
            freshLocationFetcher = {
                queryAttempted = true
                EmergencyLocation.Available(12.0, 77.0)
            }
            lastLocationFetcher = {
                queryAttempted = true
                EmergencyLocation.Available(12.0, 77.0)
            }
        }

        val result = provider.getCurrentLocation()
        assertEquals(EmergencyLocation.Unavailable, result)
        assertFalse("Must not attempt location queries when permissions are denied", queryAttempted)
    }

    // ─── 8. Live Ride Location Updates ───────────────────────────────────────

    @Test
    fun `getLocationUpdates emits PermissionDenied when location permissions are missing`() = runTest {
        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            permissionChecker = { _, _ -> false } // Denied
        }

        val result = provider.getLocationUpdates()
        var emittedState: com.manish.vahakvigil.domain.model.RideLocationState? = null
        result.collect { state ->
            emittedState = state
        }

        assertEquals(com.manish.vahakvigil.domain.model.RideLocationState.PermissionDenied, emittedState)
    }

    @Test
    fun `getLocationUpdates emits LocationUnavailable when client cannot be resolved`() = runTest {
        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            permissionChecker = { _, _ -> true } // Granted
        }

        val result = provider.getLocationUpdates()
        var emittedState: com.manish.vahakvigil.domain.model.RideLocationState? = null
        result.collect { state ->
            emittedState = state
        }

        assertEquals(com.manish.vahakvigil.domain.model.RideLocationState.LocationUnavailable, emittedState)
    }

    @Test
    fun `getLocationUpdates delivers injected live location flow`() = runTest {
        val expected = com.manish.vahakvigil.domain.model.RideLocationState.Available(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5.0f,
            timestampMs = 1_700_000_000_000L
        )

        val provider = AndroidLocationProvider(
            context = null,
            fusedLocationClient = null
        ).apply {
            liveLocationUpdatesFlow = kotlinx.coroutines.flow.flowOf(expected)
        }

        var collected: com.manish.vahakvigil.domain.model.RideLocationState? = null
        provider.getLocationUpdates().collect {
            collected = it
        }

        assertEquals(expected, collected)
    }
}
