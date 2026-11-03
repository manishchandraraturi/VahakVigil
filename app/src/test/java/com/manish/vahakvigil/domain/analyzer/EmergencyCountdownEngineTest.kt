package com.manish.vahakvigil.domain.analyzer

import com.manish.vahakvigil.domain.model.EmergencyCountdownState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [EmergencyCountdownEngine].
 */
class EmergencyCountdownEngineTest {

    private val defaultDuration = 75_000L
    private val engine = EmergencyCountdownEngine(defaultDuration)

    // ─── 8. Initial state is IDLE ─────────────────────────────────────────────

    @Test
    fun `initial state is IDLE`() {
        assertEquals(EmergencyCountdownState.IDLE, engine.state)
        assertEquals(defaultDuration, engine.remainingMillis)
    }

    // ─── 9. Start enters COUNTING_DOWN ────────────────────────────────────────

    @Test
    fun `start transitions IDLE to COUNTING_DOWN`() {
        engine.start(startTimestampMillis = 10_000L)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, engine.state)
    }

    // ─── 10. Remaining time initially equals configured duration ──────────────

    @Test
    fun `remaining time initially equals configured duration`() {
        engine.start(startTimestampMillis = 10_000L)
        assertEquals(defaultDuration, engine.remainingMillis)
    }

    // ─── 11. Timestamp progression reduces remaining time correctly ───────────

    @Test
    fun `timestamp progression reduces remaining time correctly`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 15_000L) // 5s elapsed

        assertEquals(70_000L, engine.remainingMillis)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, engine.state)

        engine.update(currentTimestampMillis = 35_000L) // 25s elapsed total
        assertEquals(50_000L, engine.remainingMillis)
    }

    // ─── 12. Countdown reaches zero at exact expiry ───────────────────────────

    @Test
    fun `countdown reaches zero at exact expiry`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 85_000L) // exactly 75s elapsed

        assertEquals(0L, engine.remainingMillis)
        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
    }

    // ─── 13. Expired state is EXPIRED ─────────────────────────────────────────

    @Test
    fun `expired state is EXPIRED`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 90_000L) // 80s elapsed > 75s

        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
    }

    // ─── 14. Countdown never becomes negative ─────────────────────────────────

    @Test
    fun `countdown never becomes negative even with large elapsed timestamps`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 999_999L)

        assertEquals(0L, engine.remainingMillis)
        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
    }

    // ─── 15. Countdown never exceeds configured duration ──────────────────────

    @Test
    fun `countdown never exceeds configured duration`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 5_000L) // timestamp before start

        assertEquals(defaultDuration, engine.remainingMillis)
    }

    // ─── 16. Cancel transitions to CANCELLED ──────────────────────────────────

    @Test
    fun `cancel transitions COUNTING_DOWN to CANCELLED`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 20_000L)
        engine.cancel()

        assertEquals(EmergencyCountdownState.CANCELLED, engine.state)
        assertEquals(65_000L, engine.remainingMillis)
    }

    // ─── 17. Cancelled countdown does not continue ────────────────────────────

    @Test
    fun `cancelled countdown ignores subsequent updates`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 20_000L)
        engine.cancel()

        val remainingAtCancel = engine.remainingMillis
        engine.update(currentTimestampMillis = 50_000L)

        assertEquals(EmergencyCountdownState.CANCELLED, engine.state)
        assertEquals(remainingAtCancel, engine.remainingMillis)
    }

    // ─── 18. Reset returns to IDLE ────────────────────────────────────────────

    @Test
    fun `reset returns engine to IDLE with full duration`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 30_000L)
        engine.reset()

        assertEquals(EmergencyCountdownState.IDLE, engine.state)
        assertEquals(defaultDuration, engine.remainingMillis)
    }

    // ─── 19. Updating before start does not start countdown ───────────────────

    @Test
    fun `updating before start does not start countdown`() {
        engine.update(currentTimestampMillis = 20_000L)

        assertEquals(EmergencyCountdownState.IDLE, engine.state)
        assertEquals(defaultDuration, engine.remainingMillis)
    }

    // ─── 20. Equal timestamps produce no elapsed time ─────────────────────────

    @Test
    fun `equal timestamps produce no elapsed time`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 10_000L)

        assertEquals(defaultDuration, engine.remainingMillis)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, engine.state)
    }

    // ─── 21. Large timestamp jumps safely expire ──────────────────────────────

    @Test
    fun `large timestamp jumps safely expire without error`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = Long.MAX_VALUE / 2)

        assertEquals(0L, engine.remainingMillis)
        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
    }

    // ─── 22. Out-of-order timestamps do not create negative elapsed time ──────

    @Test
    fun `out-of-order timestamps do not increase remaining time`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 30_000L) // 20s elapsed -> 55s remaining
        assertEquals(55_000L, engine.remainingMillis)

        // Timestamp jumps backwards in time
        engine.update(currentTimestampMillis = 15_000L)
        assertEquals(55_000L, engine.remainingMillis)
    }

    // ─── 23. Custom countdown configuration works ─────────────────────────────

    @Test
    fun `custom countdown duration operates as configured`() {
        val customEngine = EmergencyCountdownEngine(durationMillis = 30_000L)
        customEngine.start(startTimestampMillis = 1000L)
        customEngine.update(currentTimestampMillis = 16_000L) // 15s elapsed

        assertEquals(15_000L, customEngine.remainingMillis)
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, customEngine.state)

        customEngine.update(currentTimestampMillis = 31_000L) // 30s elapsed
        assertEquals(0L, customEngine.remainingMillis)
        assertEquals(EmergencyCountdownState.EXPIRED, customEngine.state)
    }

    // ─── 24. expireImmediately transitions immediately to EXPIRED ────────────

    @Test
    fun `expireImmediately transitions COUNTING_DOWN to EXPIRED and zeros remaining time`() {
        engine.start(startTimestampMillis = 10_000L)
        engine.update(currentTimestampMillis = 20_000L) // 65s remaining
        assertEquals(EmergencyCountdownState.COUNTING_DOWN, engine.state)
        assertEquals(65_000L, engine.remainingMillis)

        engine.expireImmediately()
        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
        assertEquals(0L, engine.remainingMillis)

        // Calling when not COUNTING_DOWN is a safe no-op
        engine.expireImmediately()
        assertEquals(EmergencyCountdownState.EXPIRED, engine.state)
    }
}
