package online.assadawut.atom.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Tests for ATOMHttpClient retry, backoff, Retry-After and circuit behaviour.
 *
 * Network is simulated with an OkHttp application interceptor instead of
 * MockWebServer, so the suite adds no dependency and works fully offline.
 * The interceptor can either return a synthetic response or throw IOException,
 * which covers both HTTP-level and transport-level failures.
 */
class ATOMHttpClientRetryTest {

    /** Scripted responses consumed in order; the last one repeats. */
    private class Script : Interceptor {
        var codes = mutableListOf<Int>()
        var bodies = mutableListOf<String>()
        var retryAfterHeaders = mutableListOf<String?>()
        var throwIo = false
        var requestCount = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            requestCount++
            if (throwIo) throw java.io.IOException("connection refused")

            val index = (requestCount - 1).coerceAtMost(codes.size - 1)
            val code = codes[index]
            val body = if (bodies.isEmpty()) "" else
                bodies[(requestCount - 1).coerceAtMost(bodies.size - 1)]
            val retryAfter = if (retryAfterHeaders.isEmpty()) null else
                retryAfterHeaders[(requestCount - 1).coerceAtMost(retryAfterHeaders.size - 1)]

            val builder = Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("scripted $code")
                .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
            if (retryAfter != null) builder.header("Retry-After", retryAfter)
            return builder.build()
        }
    }

    private fun clientFor(script: Script, circuit: CircuitBreaker = CircuitBreaker()) =
        ATOMHttpClient(
            client = OkHttpClient.Builder().addInterceptor(script).build(),
            circuit = circuit
        )

    // ---------------------------------------------------------------- backoff

    @Test
    fun `backoff sequence is d, 2d, 4d`() {
        val http = clientFor(Script())
        val d = ATOMHttpClient.BACKOFF_INITIAL_DELAY_MS

        assertEquals(d, http.backoffDelayMs(1, null))
        assertEquals(d * 2, http.backoffDelayMs(2, null))
        assertEquals(d * 4, http.backoffDelayMs(3, null))
    }

    @Test
    fun `four attempts are needed to produce three backoff pauses`() {
        // 3 pauses = d, 2d, 4d, which requires attempts 1..4 (3 retries).
        assertEquals(4, ATOMHttpClient.BACKOFF_MAX_ATTEMPTS)
    }

    @Test
    fun `backoff is capped at the maximum delay`() {
        val http = clientFor(Script())
        assertEquals(ATOMHttpClient.BACKOFF_MAX_DELAY_MS, http.backoffDelayMs(30, null))
    }

    @Test
    fun `retry-after header overrides exponential backoff`() {
        val http = clientFor(Script())
        assertEquals(2_000L, http.backoffDelayMs(1, 2_000L))
    }

    @Test
    fun `retry-after is also capped at the maximum delay`() {
        val http = clientFor(Script())
        assertEquals(ATOMHttpClient.BACKOFF_MAX_DELAY_MS, http.backoffDelayMs(1, 600_000L))
    }

    // ---------------------------------------------------------------- retries

    @Test
    fun `429 then success returns the body`() {
        val script = Script().apply {
            codes = mutableListOf(429, 200)
            bodies = mutableListOf("", """{"intent":"light_on"}""")
        }
        val circuit = CircuitBreaker()
        val http = clientFor(script, circuit)

        val result = http.callJevApiDecision("เปิดไฟ")

        assertEquals("""{"intent":"light_on"}""", result)
        assertEquals("must retry exactly once", 2, script.requestCount)
        assertEquals(
            "a successful cloud call must leave the circuit closed",
            CircuitBreaker.State.CLOSED,
            circuit.state()
        )
    }

    @Test
    fun `exhausted retries throw after four attempts`() {
        val script = Script().apply { codes = mutableListOf(500) }
        val http = clientFor(script)

        try {
            http.callJevApiDecision("เปิดไฟ")
            fail("expected ATOMNetworkException after exhausting retries")
        } catch (expected: ATOMNetworkException) {
            assertTrue(expected.isCloudFailure)
        }
        assertEquals(
            "must try once plus three retries",
            ATOMHttpClient.BACKOFF_MAX_ATTEMPTS,
            script.requestCount
        )
    }

    @Test
    fun `transport failure is retried then thrown`() {
        val script = Script().apply { throwIo = true }
        // threshold 1 so a single exhausted request visibly trips the circuit
        val circuit = CircuitBreaker(failureThreshold = 1, resetAfterMs = 60_000L)
        val http = clientFor(script, circuit)

        try {
            http.callJevApiDecision("เปิดไฟ")
            fail("expected ATOMNetworkException for a transport failure")
        } catch (expected: ATOMNetworkException) {
            assertTrue(expected.isCloudFailure)
        }
        assertEquals(ATOMHttpClient.BACKOFF_MAX_ATTEMPTS, script.requestCount)
        assertEquals("only the exhausted request is counted", 1, circuit.failureCount())
        assertEquals(CircuitBreaker.State.OPEN, circuit.state())
    }

    // ---------------------------------------------------------------- circuit

    @Test
    fun `429 retry-after header is parsed into the thrown exception`() {
        // Always 429 with Retry-After: 1 so the final exception carries the value.
        val script = Script().apply {
            codes = mutableListOf(429)
            retryAfterHeaders = mutableListOf("1")
        }
        val http = clientFor(script)

        try {
            http.callJevApiDecision("เปิดไฟ")
            fail("expected ATOMNetworkException once retries are exhausted")
        } catch (expected: ATOMNetworkException) {
            assertEquals(
                "Retry-After must be read from the response header",
                1_000L,
                expected.retryAfterMs
            )
        }
        assertEquals(ATOMHttpClient.BACKOFF_MAX_ATTEMPTS, script.requestCount)
    }

    @Test
    fun `open circuit blocks the call without touching the network`() {
        val script = Script().apply { codes = mutableListOf(200) }
        val circuit = CircuitBreaker(failureThreshold = 1, resetAfterMs = 60_000L)
        circuit.onFailure()
        assertEquals(CircuitBreaker.State.OPEN, circuit.state())

        val http = clientFor(script, circuit)
        try {
            http.callJevApiDecision("เปิดไฟ")
            fail("expected the open circuit to reject the call immediately")
        } catch (expected: ATOMNetworkException) {
            assertTrue(expected.isCloudFailure)
            assertTrue(expected.message.contains("circuit"))
        }
        assertEquals("no request may leave the device", 0, script.requestCount)
    }

    @Test
    fun `local failure never touches the cloud circuit`() {
        val script = Script().apply { codes = mutableListOf(500) }
        val circuit = CircuitBreaker(failureThreshold = 1, resetAfterMs = 60_000L)
        val http = clientFor(script, circuit)

        repeat(5) {
            try {
                http.callOllamaGenerate("เปิดไฟ")
                fail("expected the local call to fail")
            } catch (expected: ATOMNetworkException) {
                assertFalse("local failures must stay local", expected.isCloudFailure)
            }
        }

        assertEquals(
            "local traffic must never open the cloud circuit",
            CircuitBreaker.State.CLOSED,
            circuit.state()
        )
        assertEquals(0, circuit.failureCount())
        assertTrue(circuit.allowRequest())
    }

    @Test
    fun `successful cloud call closes a half-open circuit`() {
        val script = Script().apply { codes = mutableListOf(200) }
        val circuit = CircuitBreaker(failureThreshold = 1, resetAfterMs = 0L)
        circuit.onFailure()
        assertEquals(CircuitBreaker.State.OPEN, circuit.state())

        val http = clientFor(script, circuit)
        http.callJevApiDecision("เปิดไฟ")

        assertEquals(CircuitBreaker.State.CLOSED, circuit.state())
        assertEquals(0, circuit.failureCount())
    }

    // ----------------------------------------------------------------- gemini

    @Test
    fun `gemini without an api key fails loudly and sends nothing`() {
        // Skip rather than assert a false result if a key happens to be set.
        assumeTrue(
            "GEMINI_API_KEY is set - cannot assert the no-key path",
            System.getenv("GEMINI_API_KEY").isNullOrBlank()
        )

        val script = Script().apply { codes = mutableListOf(200) }
        val http = clientFor(script)

        try {
            http.callGeminiGenerate(systemPrompt = "คุณคือ ATOM", userPrompt = "สวัสดี")
            fail("expected a missing-key failure rather than a fabricated reply")
        } catch (expected: ATOMNetworkException) {
            assertTrue(expected.isCloudFailure)
            assertTrue(expected.message.contains("GEMINI_API_KEY"))
        }
        assertEquals("no request may be sent without a key", 0, script.requestCount)
    }
}