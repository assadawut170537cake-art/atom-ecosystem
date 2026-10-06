package online.assadawut.atom.routing

import online.assadawut.atom.model.ExecutionMode
import online.assadawut.atom.model.IntentType
import online.assadawut.atom.model.RouterDecision
import online.assadawut.atom.network.ATOMHttpClient
import online.assadawut.atom.network.ATOMNetworkException

/**
 * อินเทอร์เฟซหลักสำหรับการจำแนกความต้องการของผู้ใช้ (Intent Router)
 */
interface IntentRouter {
    /**
     * หน้าที่: วิเคราะห์ข้อความคำสั่งของผู้ใช้เพื่อระบุประเภท Intent และค่าคะแนนความเชื่อมั่น
     * พารามิเตอร์: prompt (String) - ข้อความคำสั่งหรือเสียงที่แปลงเป็นข้อความ, mode (ExecutionMode) - โหมดการประมวลผลที่เลือก
     * ประเภทอ็อบเจกต์ที่ส่งคืน: RouterDecision - อ็อบเจกต์ผลลัพธ์การตัดสินใจ Intent
     */
    fun routeIntent(prompt: String, mode: ExecutionMode): RouterDecision
}

/**
 * คลาส Implementation สำหรับประเมิน Intent แบบลูกผสม (Hybrid Intent Router)
 * รองรับทั้ง Rule-Based / Local Model (Ollama) และ Cloud-Native TypeSafe Jev API
 * พร้อมกลไก Fail-Safe ตกกลับมาเป็น Local ทันทีหาก Cloud ขัดข้อง
 */
class HybridIntentRouter(
    private val httpClient: ATOMHttpClient = ATOMHttpClient()
) : IntentRouter {

    /**
     * Each pattern's FIRST capture group is the verb, so the ON/OFF state comes
     * from what was actually matched rather than from a substring scan.
     *
     * Why this matters: "เปิด" (open/ON) contains "ปิด" (close/OFF) as a raw
     * substring, because Thai writes the leading vowel เ before the consonant
     * เ-ป-ิ-ด. An earlier `prompt.contains("ปิด")` check therefore reported OFF
     * for every "เปิด" command, so "เปิดไฟฉาย" switched the torch OFF.
     */
    private val directActionPatterns = listOf(
        Regex("(เปิด|ปิด|หรี่)\\s*(ไฟฉาย|ไฟห้อง|ไฟสว่าง|โคมไฟ)", RegexOption.IGNORE_CASE) to "device_torch",
        Regex("(เปิด|ปิด|สลับ)\\s*(ไวไฟ|wifi|wi-fi|บลูทูธ|bluetooth)", RegexOption.IGNORE_CASE) to "device_connectivity",
        Regex("(หรี่|เงียบ|ปิด|ลด|เพิ่ม)\\s*(เสียง|ความดัง|ลำโพง)", RegexOption.IGNORE_CASE) to "device_volume",
        Regex("(ลด|เพิ่ม|ปรับ)\\s*(แสง|ความสว่าง|หน้าจอ)", RegexOption.IGNORE_CASE) to "device_brightness",
        Regex("^(เปิด|launch|open)\\s+([a-zA-Z0-9ก-๙]+)$", RegexOption.IGNORE_CASE) to "app_launch",
        Regex("(ถ่ายรูป|เปิดกล้อง|สแกน)", RegexOption.IGNORE_CASE) to "device_camera",
        Regex("(เริ่มบันทึกเสียง|อัดเสียง|หยุดบันทึกเสียง)", RegexOption.IGNORE_CASE) to "device_audio_record"
    )

    /** Verbs that mean "decrease/disable". Everything else is an increase/enable. */
    private val offVerbs = setOf("ปิด", "ลด", "หรี่", "เงียบ", "หยุด", "stop", "off", "disable")

    private val memorySearchKeywords = listOf(
        "จำได้ไหม", "บันทึกอะไรไว้", "ค้นหา", "โน้ต", "สรุป", "เคยบอกว่า", 
        "เมื่อวาน", "ข้อมูลของ", "ประวัติ", "ใครคือ", "หมายเลข", "จดไว้ว่า",
        "search", "remember", "recall", "memory", "note", "summary", "find"
    )

    /**
     * หน้าที่: ตัดสินใจประเภท Intent ตามโหมดที่กำหนด พร้อมระบบ Fallback อัตโนมัติ
     * พารามิเตอร์: prompt (String) - ข้อความคำสั่ง, mode (ExecutionMode) - โหมดปัจจุบัน (Local หรือ Cloud)
     * ประเภทอ็อบเจกต์ที่ส่งคืน: RouterDecision - ผลการประเมิน Intent
     */
    override fun routeIntent(prompt: String, mode: ExecutionMode): RouterDecision {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isEmpty()) {
            return RouterDecision(
                intent = IntentType.UNKNOWN,
                confidenceScore = 0.0,
                extractedQuery = ""
            )
        }

        val ruleDecision = evaluateRuleBasedFastPattern(trimmedPrompt)
        if (ruleDecision != null && ruleDecision.confidenceScore >= 0.90) {
            return ruleDecision
        }

        return when (mode) {
            ExecutionMode.CLOUD_NATIVE -> {
                try {
                    evaluateCloudJevApi(trimmedPrompt)
                } catch (cloudException: Exception) {
                    evaluateLocalFallback(trimmedPrompt, fallbackReason = cloudException.message)
                }
            }
            ExecutionMode.LOCAL_FIRST -> {
                evaluateLocalFallback(trimmedPrompt, fallbackReason = null)
            }
        }
    }

    private fun evaluateRuleBasedFastPattern(prompt: String): RouterDecision? {
        try {
            for ((pattern, targetAction) in directActionPatterns) {
                val match = pattern.find(prompt)
                if (match != null) {
                    // Group 1 is the verb. Fall back to the whole match when a
                    // pattern has no capture group (e.g. device_camera).
                    val verb = match.groupValues.getOrNull(1)
                        ?.takeIf { it.isNotBlank() }
                        ?: match.value
                    val isTurnOff = verb.trim().lowercase() in offVerbs

                    return RouterDecision(
                        intent = IntentType.DIRECT_ACTION,
                        confidenceScore = 0.99,
                        actionTarget = targetAction,
                        actionPayload = mapOf(
                            "matchedPattern" to pattern.pattern,
                            "matchedVerb" to verb,
                            "command" to match.value,
                            "state" to if (isTurnOff) "OFF" else "ON"
                        ),
                        extractedQuery = prompt,
                        rawResponse = "Matched deterministic rule: $targetAction"
                    )
                }
            }

            for (keyword in memorySearchKeywords) {
                if (prompt.contains(keyword, ignoreCase = true)) {
                    return RouterDecision(
                        intent = IntentType.CONTEXT_MEMORY_SEARCH,
                        confidenceScore = 0.92,
                        extractedQuery = prompt,
                        rawResponse = "Matched keyword: $keyword"
                    )
                }
            }
        } catch (ex: Exception) {
            return null
        }
        return null
    }

    private fun evaluateCloudJevApi(prompt: String): RouterDecision {
        val jsonResponse = httpClient.callJevApiDecision(prompt)
        try {
            val choiceRegex = "\"choice\"\\s*:\\s*\"([A-Z_]+)\"".toRegex()
            val confidenceRegex = "\"confidence\"\\s*:\\s*([0-9.]+)".toRegex()

            val chosenOption = choiceRegex.find(jsonResponse)?.groups?.get(1)?.value ?: "CONTEXT_MEMORY_SEARCH"
            val confidence = confidenceRegex.find(jsonResponse)?.groups?.get(1)?.value?.toDoubleOrNull() ?: 0.85

            val mappedIntent = when (chosenOption) {
                "DIRECT_ACTION" -> IntentType.DIRECT_ACTION
                "CONTEXT_MEMORY_SEARCH" -> IntentType.CONTEXT_MEMORY_SEARCH
                "CONVERSATION_FALLBACK" -> IntentType.CONVERSATION_FALLBACK
                else -> IntentType.CONTEXT_MEMORY_SEARCH
            }

            return RouterDecision(
                intent = mappedIntent,
                confidenceScore = confidence,
                extractedQuery = prompt,
                rawResponse = jsonResponse
            )
        } catch (parseEx: Exception) {
            throw ATOMNetworkException("Failed to parse Jev API response: ${parseEx.message}", cause = parseEx)
        }
    }

    private fun evaluateLocalFallback(prompt: String, fallbackReason: String?): RouterDecision {
        try {
            val systemPrompt = """
                You are a fast intent classifier. Reply ONLY with one word:
                DIRECT_ACTION (for controlling device, torch, wifi, sound)
                CONTEXT_MEMORY_SEARCH (for questions, looking up facts, notes, memory)
                CONVERSATION_FALLBACK (for greetings, general talk)
                
                Input: $prompt
                Classification:
            """.trimIndent()

            val ollamaResponse = httpClient.callOllamaGenerate(systemPrompt).trim().uppercase()

            val determinedIntent = when {
                ollamaResponse.contains("DIRECT_ACTION") -> IntentType.DIRECT_ACTION
                ollamaResponse.contains("CONTEXT_MEMORY_SEARCH") -> IntentType.CONTEXT_MEMORY_SEARCH
                ollamaResponse.contains("CONVERSATION_FALLBACK") -> IntentType.CONVERSATION_FALLBACK
                else -> IntentType.CONTEXT_MEMORY_SEARCH
            }

            return RouterDecision(
                intent = determinedIntent,
                confidenceScore = 0.88,
                extractedQuery = prompt,
                rawResponse = "Local Ollama: $ollamaResponse | Fallback: $fallbackReason"
            )
        } catch (ollamaEx: Exception) {
            val containsActionVerb = prompt.startsWith("เปิด") || prompt.startsWith("ปิด") ||
                    prompt.startsWith("ตั้ง") || prompt.startsWith("โทร")
            val intent = if (containsActionVerb) IntentType.DIRECT_ACTION else IntentType.CONTEXT_MEMORY_SEARCH

            return RouterDecision(
                intent = intent,
                confidenceScore = 0.70,
                extractedQuery = prompt,
                rawResponse = "Local Heuristic Fallback (Ollama offline: ${ollamaEx.message})"
            )
        }
    }
}
