package online.assadawut.atom.routing

import online.assadawut.atom.model.ExecutionMode
import online.assadawut.atom.model.IntentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the rule-based layer of HybridIntentRouter.
 *
 * Only the deterministic fast-path is exercised: it short-circuits before any
 * network call, so these run offline and do not need an HTTP client stub.
 * The cloud/Ollama fallbacks are deliberately not tested here because they
 * require live endpoints.
 */
class HybridIntentRouterTest {

    private val router = HybridIntentRouter()

    @Test
    fun `empty prompt is UNKNOWN with zero confidence`() {
        val r = router.routeIntent("   ", ExecutionMode.LOCAL_FIRST)
        assertEquals(IntentType.UNKNOWN, r.intent)
        assertEquals(0.0, r.confidenceScore, 0.0001)
        assertEquals("", r.extractedQuery)
    }

    @Test
    fun `torch on is DIRECT_ACTION with ON state`() {
        val r = router.routeIntent("เปิดไฟฉาย", ExecutionMode.LOCAL_FIRST)
        assertEquals(IntentType.DIRECT_ACTION, r.intent)
        assertEquals("device_torch", r.actionTarget)
        assertEquals("ON", r.actionPayload?.get("state"))
        assertTrue(r.confidenceScore >= 0.90)
    }

    @Test
    fun `torch off is DIRECT_ACTION with OFF state`() {
        val r = router.routeIntent("ปิดไฟฉาย", ExecutionMode.LOCAL_FIRST)
        assertEquals(IntentType.DIRECT_ACTION, r.intent)
        assertEquals("device_torch", r.actionTarget)
        assertEquals("OFF", r.actionPayload?.get("state"))
    }

    @Test
    fun `wifi toggle is connectivity`() {
        val r = router.routeIntent("เปิดไวไฟ", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_connectivity", r.actionTarget)
    }

    @Test
    fun `bluetooth toggle is connectivity`() {
        val r = router.routeIntent("ปิดบลูทูธ", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_connectivity", r.actionTarget)
    }

    @Test
    fun `volume up is DIRECT_ACTION`() {
        val r = router.routeIntent("เพิ่มเสียง", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_volume", r.actionTarget)
        assertEquals("ON", r.actionPayload?.get("state"))
    }

    @Test
    fun `volume down is DIRECT_ACTION and OFF`() {
        val r = router.routeIntent("ลดเสียง", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_volume", r.actionTarget)
        assertEquals("OFF", r.actionPayload?.get("state"))
    }

    @Test
    fun `brightness is DIRECT_ACTION`() {
        val r = router.routeIntent("เพิ่มความสว่างหน้าจอ", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_brightness", r.actionTarget)
    }

    @Test
    fun `app launch is DIRECT_ACTION`() {
        val r = router.routeIntent("เปิด spotify", ExecutionMode.LOCAL_FIRST)
        assertEquals("app_launch", r.actionTarget)
    }

    @Test
    fun `camera is DIRECT_ACTION`() {
        val r = router.routeIntent("เปิดกล้อง", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_camera", r.actionTarget)
    }

    @Test
    fun `audio record is DIRECT_ACTION`() {
        val r = router.routeIntent("เริ่มบันทึกเสียง", ExecutionMode.LOCAL_FIRST)
        assertEquals("device_audio_record", r.actionTarget)
    }

    @Test
    fun `thai memory keyword triggers memory search`() {
        val r = router.routeIntent("เมื่อวานฉันบอกอะไรไว้บ้าง", ExecutionMode.LOCAL_FIRST)
        assertEquals(IntentType.CONTEXT_MEMORY_SEARCH, r.intent)
    }

    @Test
    fun `english memory keyword triggers memory search`() {
        val r = router.routeIntent("do you remember the note", ExecutionMode.LOCAL_FIRST)
        assertEquals(IntentType.CONTEXT_MEMORY_SEARCH, r.intent)
    }

    @Test
    fun `rule match reports the matched pattern in the payload`() {
        val r = router.routeIntent("เปิดไฟฉาย", ExecutionMode.LOCAL_FIRST)
        assertTrue(r.actionPayload?.containsKey("matchedPattern") == true)
        assertTrue(r.actionPayload?.containsKey("command") == true)
        assertTrue(r.rawResponse.isNotBlank())
    }

    @Test
    fun `Thai leading vowel does not make ON look like OFF`() {
        // Regression: "เปิด" contains "ปิด" as a substring because Thai writes
        // the leading vowel เ before the consonant. A substring-based OFF check
        // inverted every "เปิด" command, switching the torch OFF when asked ON.
        assertTrue(
            "sanity: เปิด really does contain ปิด",
            "เปิด".contains("ปิด")
        )

        val on = router.routeIntent("เปิดไฟฉาย", ExecutionMode.LOCAL_FIRST)
        assertEquals("ON", on.actionPayload?.get("state"))
        assertEquals("เปิด", on.actionPayload?.get("matchedVerb"))

        val wifi = router.routeIntent("เปิดไวไฟ", ExecutionMode.LOCAL_FIRST)
        assertEquals("ON", wifi.actionPayload?.get("state"))
    }

    @Test
    fun `turn-off verbs still report OFF`() {
        assertEquals(
            "OFF",
            router.routeIntent("ปิดไฟฉาย", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
        assertEquals(
            "OFF",
            router.routeIntent("ปิดไวไฟ", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
        assertEquals(
            "OFF",
            router.routeIntent("ลดเสียง", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
        assertEquals(
            "OFF",
            router.routeIntent("หรี่ไฟสว่าง", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
    }

    @Test
    fun `increase verbs report ON`() {
        assertEquals(
            "ON",
            router.routeIntent("เพิ่มเสียง", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
        assertEquals(
            "ON",
            router.routeIntent("เพิ่มความสว่าง", ExecutionMode.LOCAL_FIRST)
                .actionPayload?.get("state")
        )
    }

    @Test
    fun `no rule match does not short circuit`() {
        // A plain greeting must NOT be classified as a device action; it should
        // fall through to the model. Offline that lands on the heuristic, which
        // must still not claim DIRECT_ACTION.
        val r = router.routeIntent("สวัสดีตอนเช้า", ExecutionMode.LOCAL_FIRST)
        assertTrue(
            "plain greeting must not become DIRECT_ACTION, got ${r.intent}",
            r.intent != IntentType.DIRECT_ACTION
        )
    }
}