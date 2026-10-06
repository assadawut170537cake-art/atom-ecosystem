package online.assadawut.atom.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * คลาสตัวกลางสำหรับการเชื่อมต่อเครือข่ายทั้งหมดของ Hybrid Pipeline
 * ครอบคลุม Ollama Local, TypeSafe Jev API, Cloudflare Workers AI,
 * Cloudflare Vectorize และ Google Gemini API
 *
 * หน้าที่: ส่งคำขอ HTTP/RESTful พร้อมระบบ Exponential Backoff ตามสูตร
 *         initial_delay * 2^(attempt-1) และตัวควบคุม Circuit Breaker
 * พารามิเตอร์คอนสตรัคเตอร์:
 *   client (OkHttpClient) - ตัวส่งคำขอ HTTP ที่ใช้ร่วมกัน
 *   circuit (CircuitBreaker) - ตัวควบคุมสถานะของฝั่ง Cloud
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี (ค่าส่งคืนอยู่ที่แต่ละเมธอด)
 */
class ATOMHttpClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val circuit: CircuitBreaker = CircuitBreaker()
) {

    companion object {
        /** ความหน่วงเริ่มต้นของ Exponential Backoff (มิลลิวินาที) */
        const val BACKOFF_INITIAL_DELAY_MS = 500L

        /**
         * จำนวน attempt สูงสุดต่อหนึ่งคำขอ (ลองใหม่ 3 ครั้ง = 4 attempts)
         * ค่า 4 ทำให้มีจังหวะหน่วง 3 รอบ คือ d / 2d / 4d ตามลำดับที่อนุมัติไว้
         */
        const val BACKOFF_MAX_ATTEMPTS = 4

        /** หน่วงเวลาสูงสุดต่อหนึ่งรอบลองใหม่ (มิลลิวินาที) */
        const val BACKOFF_MAX_DELAY_MS = 8_000L

        /** จำนวนความล้มเหลวติดต่อกันของฝั่ง Cloud ที่จะตัดวงจร (Circuit Breaker) */
        const val CIRCUIT_FAILURE_THRESHOLD = 3

        /** ระยะเวลาที่วงจรค้างอยู่ในสถานะเปิดก่อนจะทดสอบกลับ (มิลลิวินาที) */
        const val CIRCUIT_RESET_AFTER_MS = 30_000L

        /** URL พื้นฐานของ Ollama (Override ได้ผ่าน env OLLAMA_URL) */
        fun ollamaBaseUrl(): String =
            System.getenv("OLLAMA_URL")?.takeIf { it.isNotBlank() }
                ?: "http://127.0.0.1:11434"

        /** โมเดลที่ใช้สร้าง Embedding */
        fun embeddingModel(): String =
            System.getenv("OLLAMA_EMBED_MODEL")?.takeIf { it.isNotBlank() }
                ?: "nomic-embed-text"

        /** โมเดลที่ใช้สร้างคำตอบ */
        fun generateModel(): String =
            System.getenv("OLLAMA_GENERATE_MODEL")?.takeIf { it.isNotBlank() }
                ?: "gemma4:e4b"

        /** URL ปลายทางของ Gemini API */
        fun geminiGenerateUrl(): String {
            val model = System.getenv("GEMINI_MODEL")?.takeIf { it.isNotBlank() }
                ?: "gemini-2.5-flash"
            return "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        }

        /** URL ของ Cloudflare Vectorize (null หากยังไม่ได้ตั้งค่า env) */
        fun cloudflareVectorizeUrl(): String? {
            val account = System.getenv("CLOUDFLARE_ACCOUNT_ID")
                ?.takeIf { it.isNotBlank() } ?: return null
            val index = System.getenv("CLOUDFLARE_VECTORIZE_INDEX")
                ?.takeIf { it.isNotBlank() } ?: return null
            return "https://api.cloudflare.com/client/v4/accounts/$account" +
                "/vectorize/v2/indexes/$index/query"
        }
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * หน้าที่: ให้ Pipeline ตรวจสถานะ Circuit Breaker ของฝั่ง Cloud ได้โดยตรง
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: CircuitBreaker.State - CLOSED / OPEN / HALF_OPEN
     */
    fun cloudCircuitState(): CircuitBreaker.State = circuit.state()

    /**
     * หน้าที่: บังคับรีเซ็ตวงจร Cloud กลับเป็นปกติ (เรียกเมื่อระบบกลับมา Local ได้)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun resetCloudCircuit() = circuit.reset()

    /**
     * หน้าที่: ส่งคำขอ HTTP แบบมี Exponential Backoff ตามสูตร
     *         initial_delay * 2^(attempt-1) โดยจำกัดค่าสูงสุดไม่ให้หน่วงนานเกินไป
     * หาก Circuit Breaker ของฝั่ง Cloud เปิดอยู่ จะโยนข้อผิดพลาดทันที
     * โดยไม่ยิงคำขอออกไปเลย เพื่อให้ระบบเปลี่ยนไปใช้ Local ได้ทันที
     *
     * พารามิเตอร์:
     *   isCloudCall (Boolean) - true หากเป็นการเรียกฝั่ง Cloud
     *   block (Int) -> T - หน่วยงานที่ทำคำขอ HTTP รับ attempt (เริ่มจาก 1) และคืนค่าผลลัพธ์
     * ประเภทอ็อบเจกต์ที่ส่งคืน: T - ค่าที่ block คืนกลับมา
     * ข้อยกเว้น: ATOMNetworkException - เมื่อลองครบทุกครั้งแล้วยังล้มเหลว
     */
    private fun <T> executeWithRetry(
        isCloudCall: Boolean,
        block: (attempt: Int) -> T
    ): T {
        if (isCloudCall && !circuit.allowRequest()) {
            throw ATOMNetworkException(
                "Cloud circuit is ${circuit.state()} - falling back to local",
                isCloudFailure = true
            )
        }

        var lastError: ATOMNetworkException? = null

        for (attempt in 1..BACKOFF_MAX_ATTEMPTS) {
            try {
                val result = block(attempt)
                // นับความสำเร็จเฉพาะฝั่ง Cloud เท่านั้น การเรียก Local
                // ไม่เกี่ยวกับสถานะวงจรของ Cloud และห้ามไปปิดวงจรมัน
                if (isCloudCall) circuit.onSuccess()
                return result
            } catch (networkEx: ATOMNetworkException) {
                lastError = networkEx
                if (!shouldRetry(networkEx) || attempt == BACKOFF_MAX_ATTEMPTS) break
                sleepQuietly(backoffDelayMs(attempt, networkEx.retryAfterMs))
            } catch (ioEx: java.io.IOException) {
                lastError = ATOMNetworkException(
                    "Network I/O failed: ${ioEx.message}",
                    isCloudFailure = isCloudCall,
                    cause = ioEx
                )
                if (attempt == BACKOFF_MAX_ATTEMPTS) break
                sleepQuietly(backoffDelayMs(attempt, null))
            }
        }

        val exhausted = lastError ?: ATOMNetworkException(
            "Request failed with no recorded error",
            isCloudFailure = isCloudCall
        )
        if (isCloudCall) circuit.onFailure()
        throw exhausted
    }

    /**
     * หน้าที่: คำนวณความหน่วงของรอบลองใหม่จากจำนวน attempt ที่ผ่านไป
     * พารามิเตอร์:
     *   attempt (Int) - จำนวนรอบที่ลองไปแล้ว (เริ่มจาก 1)
     *   retryAfterMs (Long?) - ค่า Retry-After ที่ server แจ้งมา (มิลลิวินาที)
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Long - ความหน่วงเป็นมิลลิวินาที
     */
    internal fun backoffDelayMs(attempt: Int, retryAfterMs: Long?): Long {
        // ถ้า server บอกเวลา Retry-After มา ให้ถือเป็นค่าที่เชื่อถือได้ที่สุด
        if (retryAfterMs != null && retryAfterMs > 0) {
            return retryAfterMs.coerceAtMost(BACKOFF_MAX_DELAY_MS)
        }
        val exponential = BACKOFF_INITIAL_DELAY_MS * (1L shl (attempt - 1))
        return exponential.coerceAtMost(BACKOFF_MAX_DELAY_MS)
    }

    /**
     * หน้าที่: ตัดสินว่าข้อผิดพลาดที่เกิดขึ้นคุ้มค่าที่จะลองซ้ำหรือไม่
     *         ข้อผิดพลาดที่ไม่เกี่ยวกับเครือข่าย (เช่น JSON พัง) ไม่ควรลองซ้ำ
     * พารามิเตอร์:
     *   error (ATOMNetworkException) - ข้อผิดพลาดที่เกิดขึ้น
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Boolean - true หากควรลองซ้ำ
     */
    private fun shouldRetry(error: ATOMNetworkException): Boolean {
        if (error.retryAfterMs != null) return true
        if (error.isCloudFailure) return true
        val msg = error.message.lowercase()
        return msg.contains("unreachable") ||
            msg.contains("timeout") ||
            msg.contains("refused") ||
            msg.contains("429")
    }

    /**
     * หน้าที่: หน่วงเวลาด้วย Thread.sleep โดยแปลง Interrupt เป็นข้อยกเว้นชัดเจน
     * พารามิเตอร์:
     *   delayMs (Long) - ระยะเวลาที่ต้องการหน่วง (มิลลิวินาที)
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     * ข้อยกเว้น: ATOMNetworkException - เมื่อ thread ถูก interrupt
     */
    private fun sleepQuietly(delayMs: Long) {
        if (delayMs <= 0L) return
        try {
            Thread.sleep(delayMs)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ATOMNetworkException(
                "Interrupted during backoff",
                isCloudFailure = false,
                cause = interrupted
            )
        }
    }

    /**
     * หน้าที่: เรียก Gemini API เพื่อสร้างคำตอบแบบ Cloud (Mode B)
     * โยนข้อผิดพลาดทันทีหากยังไม่ได้ตั้งค่า GEMINI_API_KEY
     * เพื่อไม่ให้ระบบแสร้งทำเป็นว่าฝั่ง Cloud ตอบได้ทั้งที่ไม่มีคีย์
     *
     * พารามิเตอร์:
     *   systemPrompt (String) - คำสั่งระบบ/บุคลิกของผู้ช่วย
     *   userPrompt (String) - คำถามของผู้ใช้
     * ประเภทอ็อบเจกต์ที่ส่งคืน: String - ข้อความคำตอบจาก Gemini
     * ข้อยกเว้น: ATOMNetworkException - หากไม่มีคีย์ หรือเรียกไม่สำเร็จ
     */
    fun callGeminiGenerate(systemPrompt: String, userPrompt: String): String {
        val apiKey = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
            ?: throw ATOMNetworkException(
                "GEMINI_API_KEY is not configured - refusing to fabricate a reply",
                isCloudFailure = true
            )

        val payload = JsonObject().apply {
            add("systemInstruction", JsonObject().apply {
                add("parts", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemPrompt) })
                })
            })
            add("contents", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", userPrompt) })
                    })
                })
            })
        }

        return executeWithRetry(isCloudCall = true) { _ ->
            val request = Request.Builder()
                .url(geminiGenerateUrl())
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonType))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                consumeBody(resp, isCloudCall = true)
            }
            extractGeminiText(body)
        }
    }

    /**
     * หน้าที่: ดึงข้อความคำตอบจาก JSON ที่ Gemini ส่งกลับมา
     * พารามิเตอร์:
     *   body (String) - ตัวอักษร JSON ของคำตอบ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: String - ข้อความคำตอบที่แยกมาแล้ว
     * ข้อยกเว้น: ATOMNetworkException - หากโครงสร้าง JSON ไม่ถูกต้อง
     */
    private fun extractGeminiText(body: String): String {
        if (body.isBlank()) {
            throw ATOMNetworkException("Gemini returned an empty body", isCloudFailure = true)
        }
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                throw ATOMNetworkException(
                    "Gemini returned no candidates: ${body.take(200)}",
                    isCloudFailure = true
                )
            }
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
            parts.joinToString("") { it.asJsonObject.get("text").asString }
        } catch (parseEx: Exception) {
            throw ATOMNetworkException(
                "Failed to parse Gemini response: ${parseEx.message}",
                isCloudFailure = true,
                cause = parseEx
            )
        }
    }

    /**
     * หน้าที่: เรียก Ollama /api/generate เพื่อสร้างคำตอบแบบ Local (Mode A)
     * พารามิเตอร์:
     *   prompt (String) - ข้อความคำสั่งที่ต้องการให้โมเดลตอบ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: String - ข้อความคำตอบจากโมเดล Local
     * ข้อยกเว้น: ATOMNetworkException - เมื่อ Ollama เข้าไม่ถึงหรือตอบไม่ถูกต้อง
     */
    fun callOllamaGenerate(prompt: String): String {
        val jsonBody = JsonObject().apply {
            addProperty("model", generateModel())
            addProperty("prompt", prompt)
            addProperty("stream", false)
        }

        return executeWithRetry(isCloudCall = false) { _ ->
            val request = Request.Builder()
                .url("${ollamaBaseUrl()}/api/generate")
                .post(jsonBody.toString().toRequestBody(jsonType))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                consumeBody(resp, isCloudCall = false)
            }
            val parsed = JsonParser.parseString(body).asJsonObject
            parsed.get("response")?.asString
                ?: throw ATOMNetworkException(
                    "Ollama returned no response field",
                    isCloudFailure = false
                )
        }
    }

    /**
     * หน้าที่: เรียก Ollama /api/embed เพื่อแปลงข้อความเป็นเวกเตอร์ความหมาย
     *         ใช้ใน Step 3 (Vector Retrieval) ของ Hybrid Pipeline
     * พารามิเตอร์:
     *   text (String) - ข้อความที่ต้องการแปลงเป็น embedding
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<Float> - เวกเตอร์ความหมายความยาวเท่ากับมิติของโมเดล
     * ข้อยกเว้น: ATOMNetworkException - หาก Ollama เข้าไม่ถึงหรือผลลัพธ์ผิดรูปแบบ
     */
    fun callOllamaEmbed(text: String): List<Float> {
        if (text.isBlank()) {
            return emptyList()
        }

        val jsonBody = JsonObject().apply {
            addProperty("model", embeddingModel())
            addProperty("prompt", text)
        }

        return executeWithRetry(isCloudCall = false) { _ ->
            val request = Request.Builder()
                .url("${ollamaBaseUrl()}/api/embed")
                .post(jsonBody.toString().toRequestBody(jsonType))
                .build()

            val body = client.newCall(request).execute().use { resp ->
                consumeBody(resp, isCloudCall = false)
            }
            parseOllamaEmbedding(body)
        }
    }

    /**
     * หน้าที่: แยกค่าเวกเตอร์จาก JSON ที่ Ollama /api/embed ส่งกลับมา
     * พารามิเตอร์:
     *   body (String) - ตัวอักษร JSON ของคำตอบ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<Float> - เวกเตอร์ความหมาย
     * ข้อยกเว้น: ATOMNetworkException - หากไม่มี embedding หรือรูปแบบผิด
     */
    private fun parseOllamaEmbedding(body: String): List<Float> {
        return try {
            val parsed = JsonParser.parseString(body).asJsonObject
            val array = parsed.getAsJsonArray("embeddings")
                ?: throw ATOMNetworkException(
                    "Ollama embed returned no 'embeddings' field",
                    isCloudFailure = false
                )
            if (array.size() == 0) {
                throw ATOMNetworkException(
                    "Ollama embed returned an empty embeddings array",
                    isCloudFailure = false
                )
            }
            val first = array[0].asJsonArray
            (0 until first.size()).map { index -> first[index].asFloat }
        } catch (parseEx: ATOMNetworkException) {
            throw parseEx
        } catch (parseEx: Exception) {
            throw ATOMNetworkException(
                "Failed to parse Ollama embedding: ${parseEx.message}",
                isCloudFailure = false,
                cause = parseEx
            )
        }
    }

    /**
     * หน้าที่: เรียก TypeSafe Jev API เพื่อประเมิน Intent ฝั่ง Cloud (Mode B)
     * พารามิเตอร์:
     *   prompt (String) - ข้อความคำสั่งของผู้ใช้
     * ประเภทอ็อบเจกต์ที่ส่งคืน: String - JSON คำตอบจาก Jev API
     * ข้อยกเว้น: ATOMNetworkException - เมื่อเรียกไม่สำเร็จ
     */
    fun callJevApiDecision(prompt: String): String {
        val jsonBody = JsonObject().apply {
            addProperty("prompt", prompt)
        }

        return executeWithRetry(isCloudCall = true) { _ ->
            val request = Request.Builder()
                .url("https://assadawut-jarvis.online/api/v1/intent/jev")
                .post(jsonBody.toString().toRequestBody(jsonType))
                .build()

            client.newCall(request).execute().use { resp ->
                consumeBody(resp, isCloudCall = true)
            }
        }
    }

    /**
     * หน้าที่: อ่านเนื้อหาตอบกลับและปิด connection เสมอ พร้อมตรวจสถานะ HTTP
     *         หากเป็น 429 จะอ่านค่า Retry-After ใส่ exception ให้ backoff ใช้
     * พารามิเตอร์:
     *   response (Response) - คำตอบจาก endpoint
     *   isCloudCall (Boolean) - true หากเป็นการเรียกฝั่ง Cloud
     * ประเภทอ็อบเจกต์ที่ส่งคืน: String - เนื้อหาของคำตอบ
     * ข้อยกเว้น: ATOMNetworkException - เมื่อ endpoint ตอบสถานะผิดพลาด
     */
    private fun consumeBody(response: Response, isCloudCall: Boolean): String {
        response.use { resp ->
            if (!resp.isSuccessful) {
                val error = ATOMNetworkException(
                    "${if (isCloudCall) "Cloud" else "Local"} endpoint HTTP ${resp.code}",
                    isCloudFailure = isCloudCall
                )
                val retryAfterSeconds = resp.header("Retry-After")?.trim()?.toLongOrNull()
                if (retryAfterSeconds != null) {
                    error.retryAfterMs = retryAfterSeconds * 1000L
                } else if (resp.code == 429) {
                    error.retryAfterMs = 1000L
                }
                throw error
            }
            return resp.body?.string() ?: ""
        }
    }
}
