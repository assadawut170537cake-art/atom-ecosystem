package online.assadawut.atom.routing

import online.assadawut.atom.database.OllamaTextEmbedder
import online.assadawut.atom.database.VectorStorageManager
import online.assadawut.atom.model.ExecutionMode
import online.assadawut.atom.model.IntentType
import online.assadawut.atom.model.PipelineExecutionResult
import online.assadawut.atom.model.RouterDecision
import online.assadawut.atom.model.VectorRecord
import online.assadawut.atom.network.ATOMHttpClient
import online.assadawut.atom.network.ATOMNetworkException
import online.assadawut.atom.network.CircuitBreaker
import online.assadawut.atom.service.DeviceActionHandler

/**
 * ตัวประสานงานหลัก (Pipeline Coordinator / Orchestrator) สำหรับระบบ Hybrid Pipeline ของ ATOM
 */
class PipelineCoordinator(
    private var defaultMode: ExecutionMode = ExecutionMode.LOCAL_FIRST,
    private val httpClient: ATOMHttpClient = ATOMHttpClient(),
    private val intentRouter: HybridIntentRouter = HybridIntentRouter(httpClient),
    private val vectorStorage: VectorStorageManager = VectorStorageManager(
        embedder = OllamaTextEmbedder(httpClient),
        store = online.assadawut.atom.database.VectorStore()
    ),
    private val deviceActionHandler: DeviceActionHandler = DeviceActionHandler()
) {

    private val relevanceThreshold: Double = 0.65

    fun setExecutionMode(mode: ExecutionMode) {
        this.defaultMode = mode
    }

    fun getExecutionMode(): ExecutionMode {
        return this.defaultMode
    }

    /**
     * หน้าที่: รันกระบวนการ Full-flow Hybrid Pipeline ตั้งแต่รับอินพุตจนถึงประมวลผลเสร็จสิ้น
     * พารามิเตอร์: userPrompt (String) - ข้อความคำสั่งหรือเสียงที่แปลงเป็นข้อความของผู้ใช้
     * ประเภทอ็อบเจกต์ที่ส่งคืน: PipelineExecutionResult - ผลลัพธ์สุดท้ายของการทำงานในทุกขั้นตอน
     */
    fun processPipeline(userPrompt: String): PipelineExecutionResult {
        var currentMode = defaultMode

        try {
            // Step 1: Input & Fast Routing
            val routerDecision = evaluateStep1Routing(userPrompt, currentMode)

            // Step 2: Direct Short-Circuit for Device Control
            if (routerDecision.intent == IntentType.DIRECT_ACTION) {
                return executeStep2DirectShortCircuit(routerDecision, currentMode)
            }

            // Step 3: Vector Retrieval & Cosine Similarity Search
            val retrievedRecords = executeStep3VectorRetrieval(routerDecision, currentMode)

            // Step 4: Decision & Relevance Filter & Final Execution
            return executeStep4DecisionAndSynthesis(userPrompt, routerDecision, retrievedRecords, currentMode)

        } catch (networkEx: ATOMNetworkException) {
            if (networkEx.isCloudFailure && currentMode == ExecutionMode.CLOUD_NATIVE) {
                currentMode = ExecutionMode.LOCAL_FIRST
                return processFallbackLocalPipeline(userPrompt, networkEx.message)
            }

            return PipelineExecutionResult(
                success = false,
                message = "เกิดข้อผิดพลาดในการประมวลผลเครือข่าย: ${networkEx.message}",
                executedStep = "NETWORK_ERROR",
                modeUsed = currentMode,
                confidenceScore = 0.0,
                errorDetails = networkEx.message
            )
        } catch (generalEx: Exception) {
            return PipelineExecutionResult(
                success = false,
                message = "เกิดข้อผิดพลาดไม่คาดคิดในระบบ Pipeline: ${generalEx.message}",
                executedStep = "SYSTEM_ERROR",
                modeUsed = currentMode,
                confidenceScore = 0.0,
                errorDetails = generalEx.message
            )
        }
    }

    private fun evaluateStep1Routing(userPrompt: String, mode: ExecutionMode): RouterDecision {
        return try {
            intentRouter.routeIntent(userPrompt, mode)
        } catch (ex: Exception) {
            RouterDecision(
                intent = IntentType.CONTEXT_MEMORY_SEARCH,
                confidenceScore = 0.50,
                extractedQuery = userPrompt,
                rawResponse = "Router error fallback: ${ex.message}"
            )
        }
    }

    private fun executeStep2DirectShortCircuit(
        decision: RouterDecision,
        mode: ExecutionMode
    ): PipelineExecutionResult {
        return try {
            val actionResponse = deviceActionHandler.executeDeviceAction(decision)
            PipelineExecutionResult(
                success = true,
                message = actionResponse,
                executedStep = "STEP_2_DIRECT_SHORT_CIRCUIT",
                modeUsed = mode,
                confidenceScore = decision.confidenceScore
            )
        } catch (ex: Exception) {
            PipelineExecutionResult(
                success = false,
                message = "ไม่สามารถดำเนินการคำสั่งอุปกรณ์ได้: ${ex.message}",
                executedStep = "STEP_2_DIRECT_SHORT_CIRCUIT_FAILED",
                modeUsed = mode,
                confidenceScore = decision.confidenceScore,
                errorDetails = ex.message
            )
        }
    }

    private fun executeStep3VectorRetrieval(
        decision: RouterDecision,
        mode: ExecutionMode
    ): List<VectorRecord> {
        return try {
            val queryText = if (decision.extractedQuery.isNotEmpty()) decision.extractedQuery else "general_query"
            vectorStorage.searchSimilar(queryText = queryText, topK = 3, mode = mode)
        } catch (ex: Exception) {
            emptyList()
        }
    }

    private fun executeStep4DecisionAndSynthesis(
        prompt: String,
        decision: RouterDecision,
        records: List<VectorRecord>,
        mode: ExecutionMode
    ): PipelineExecutionResult {
        try {
            val relevantRecords = records.filter { it.similarityScore >= relevanceThreshold }

            if (relevantRecords.isNotEmpty()) {
                val contextSummary = StringBuilder("พบข้อมูลที่เกี่ยวข้องในระบบความจำดังนี้ค่ะ:\n")
                for ((index, item) in relevantRecords.withIndex()) {
                    contextSummary.append("${index + 1}. ${item.content} (ความเหมือน: ${String.format("%.2f", item.similarityScore * 100)}%)\n")
                }

                return PipelineExecutionResult(
                    success = true,
                    message = contextSummary.toString().trim(),
                    executedStep = "STEP_4_VECTOR_MEMORY_RETRIEVAL",
                    modeUsed = mode,
                    confidenceScore = decision.confidenceScore,
                    contextData = relevantRecords
                )
            } else {
                val generatedAnswer = generateAnswerWithLlm(prompt, records, mode)
                return PipelineExecutionResult(
                    success = true,
                    message = generatedAnswer,
                    executedStep = "STEP_4_LLM_GENERATION",
                    modeUsed = mode,
                    confidenceScore = decision.confidenceScore,
                    contextData = records
                )
            }
        } catch (ex: Exception) {
            return PipelineExecutionResult(
                success = false,
                message = "ไม่สามารถสร้างคำตอบใน Step 4 ได้: ${ex.message}",
                executedStep = "STEP_4_ERROR",
                modeUsed = mode,
                confidenceScore = decision.confidenceScore,
                errorDetails = ex.message
            )
        }
    }

    private fun generateAnswerWithLlm(
        prompt: String,
        contextRecords: List<VectorRecord>,
        mode: ExecutionMode
    ): String {
        val systemPrompt = "คุณคือ ไฟรเดย์ (F.R.I.D.A.Y) ผู้ช่วย AI อัจฉริยะสำหรับลูกพี่ ตอบคำถามอย่างสุภาพ ฉลาด คล่องแคล่ว ใช้คำลงท้าย ค่ะ/นะคะ เท่านั้น"
        val contextBuilder = StringBuilder()
        if (contextRecords.isNotEmpty()) {
            contextBuilder.append("\nบริบทข้อมูลอ้างอิง:\n")
            for (rec in contextRecords) {
                contextBuilder.append("- ${rec.content}\n")
            }
        }
        val enrichedPrompt = "$prompt\n$contextBuilder"

        return when (mode) {
            ExecutionMode.CLOUD_NATIVE -> {
                try {
                    httpClient.callGeminiGenerate(systemPrompt, enrichedPrompt)
                } catch (cloudEx: Exception) {
                    generateLocalAnswer(systemPrompt, enrichedPrompt)
                }
            }
            ExecutionMode.LOCAL_FIRST -> {
                generateLocalAnswer(systemPrompt, enrichedPrompt)
            }
        }
    }

    private fun generateLocalAnswer(systemPrompt: String, enrichedPrompt: String): String {
        return try {
            val fullPrompt = "$systemPrompt\n\nคำถาม: $enrichedPrompt\nคำตอบ:"
            httpClient.callOllamaGenerate(fullPrompt)
        } catch (ex: Exception) {
            "ไฟรเดย์ได้รับคำถามแล้วค่ะ แต่ขณะนี้โมเดล Local และ Cloud ไม่สามารถประมวลผลคำตอบได้ชั่วคราว: ${ex.message}"
        }
    }

    private fun processFallbackLocalPipeline(userPrompt: String, failureReason: String?): PipelineExecutionResult {
        val localRouterDecision = intentRouter.routeIntent(userPrompt, ExecutionMode.LOCAL_FIRST)
        if (localRouterDecision.intent == IntentType.DIRECT_ACTION) {
            return executeStep2DirectShortCircuit(localRouterDecision, ExecutionMode.LOCAL_FIRST)
        }
        val localRecords = executeStep3VectorRetrieval(localRouterDecision, ExecutionMode.LOCAL_FIRST)
        val result = executeStep4DecisionAndSynthesis(userPrompt, localRouterDecision, localRecords, ExecutionMode.LOCAL_FIRST)
        return result.copy(
            errorDetails = "Cloud failure triggered automatic local fallback. Reason: $failureReason"
        )
    }
}
