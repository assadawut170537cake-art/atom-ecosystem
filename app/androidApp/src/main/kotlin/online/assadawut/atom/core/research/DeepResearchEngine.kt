package online.assadawut.atom.core.research

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.assadawut.atom.provider.LLMProvider

data class ResearchReport(
    val query: String,
    val keyFindings: List<String>,
    val detailedSynthesis: String,
    val timestamp: Long = System.currentTimeMillis()
)

class DeepResearchEngine(
    private val provider: LLMProvider
) {
    suspend fun conductResearch(topic: String): Result<ResearchReport> = withContext(Dispatchers.IO) {
        runCatching {
            val planPrompt = "วางแผนการวิจัยหัวข้อแบบเจาะลึก: $topic ให้แตกเป็น 3 คำถามย่อยสำหรับค้นหาข้อมูล"
            val planResponse = provider.chat(planPrompt).getOrThrow()

            val synthesisPrompt = """
                ใช้ข้อมูลหัวข้อ: $topic
                แผนการวิจัย: $planResponse
                
                จงสรุปรายงานการวิจัยเชิงลึก (Deep Research Report) แยกเป็น:
                1. ข้อสรุปสำคัญ (Key Findings)
                2. เนื้อหาวิเคราะห์เจาะลึก (Detailed Analysis)
            """.trimIndent()

            val rawSynthesis = provider.chat(synthesisPrompt).getOrThrow()
            
            ResearchReport(
                query = topic,
                keyFindings = listOf("วิเคราะห์ผลกระทบและความเป็นไปได้เรียบร้อย"),
                detailedSynthesis = rawSynthesis
            )
        }
    }
}
