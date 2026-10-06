package online.assadawut.atom.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CircuitBreaker state transitions.
 *
 * Uses an injectable clock so no test ever sleeps - open/closed behaviour is
 * asserted by moving time explicitly rather than by waiting.
 */
class CircuitBreakerTest {

    private var now = 0L

    private fun breaker(
        threshold: Int = 3,
        resetAfterMs: Long = 30_000L
    ): CircuitBreaker = CircuitBreaker(
        failureThreshold = threshold,
        resetAfterMs = resetAfterMs,
        clock = { now }
    )

    @Test
    fun `starts closed and allows requests`() {
        val c = breaker()
        assertEquals(CircuitBreaker.State.CLOSED, c.state())
        assertTrue(c.allowRequest())
    }

    @Test
    fun `single failure does not open the circuit`() {
        val c = breaker(threshold = 3)
        c.onFailure()
        assertEquals(CircuitBreaker.State.CLOSED, c.state())
        assertTrue(c.allowRequest())
    }

    @Test
    fun `failures at threshold open the circuit`() {
        val c = breaker(threshold = 3)
        c.onFailure()
        c.onFailure()
        c.onFailure()
        assertEquals(CircuitBreaker.State.OPEN, c.state())
        assertFalse(c.allowRequest())
    }

    @Test
    fun `open circuit blocks requests until reset window elapses`() {
        val c = breaker(threshold = 1, resetAfterMs = 30_000L)
        c.onFailure()
        assertEquals(CircuitBreaker.State.OPEN, c.state())

        now = 29_999L
        assertFalse("still inside reset window", c.allowRequest())

        now = 30_000L
        assertTrue("window elapsed", c.allowRequest())
        assertEquals(CircuitBreaker.State.HALF_OPEN, c.state())
    }

    @Test
    fun `success while half open closes the circuit`() {
        val c = breaker(threshold = 1, resetAfterMs = 10_000L)
        c.onFailure()
        now = 10_000L
        c.allowRequest()                 // OPEN -> HALF_OPEN
        c.onSuccess()

        assertEquals(CircuitBreaker.State.CLOSED, c.state())
        assertTrue(c.allowRequest())
        assertEquals(0, c.failureCount())
    }

    @Test
    fun `failure while half open reopens the circuit`() {
        val c = breaker(threshold = 1, resetAfterMs = 10_000L)
        c.onFailure()
        assertEquals("circuit must be open before the trial", CircuitBreaker.State.OPEN, c.state())

        now = 10_000L
        assertTrue("reset window elapsed", c.allowRequest())   // OPEN -> HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, c.state())

        c.onFailure()   // a single failure during the trial must reopen immediately

        assertEquals(CircuitBreaker.State.OPEN, c.state())
        assertFalse(c.allowRequest())
    }

    @Test
    fun `reset restores closed state immediately`() {
        val c = breaker(threshold = 1, resetAfterMs = 60_000L)
        c.onFailure()
        assertEquals(CircuitBreaker.State.OPEN, c.state())

        c.reset()
        assertEquals(CircuitBreaker.State.CLOSED, c.state())
        assertTrue(c.allowRequest())
        assertEquals(0, c.failureCount())
    }

    @Test
    fun `failure count accumulates before threshold`() {
        val c = breaker(threshold = 5)
        c.onFailure()
        c.onFailure()
        assertEquals(2, c.failureCount())
        assertEquals(CircuitBreaker.State.CLOSED, c.state())
    }
}