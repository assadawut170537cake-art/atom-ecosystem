package online.assadawut.atom.core

import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.MemoryRecord

data class AtomSkill(
    val id: String,
    val name: String,
    val description: String,
    val usageCount: Int = 1
)

class SkillSynthesizer(private val database: ATOMDatabase) {

    suspend fun synthesizeFromTurn(userPrompt: String, assistantReply: String) {
        if (userPrompt.length < 10) return

        val timestamp = System.currentTimeMillis()
        val skillName = "skill_${userPrompt.take(15).replace(" ", "_")}"
        val record = MemoryRecord(
            id = "skill_$timestamp",
            content = "[SELF-LEARNED SKILL: $skillName]\nTrigger: $userPrompt\nLearned Action Pattern: $assistantReply",
            timestamp = timestamp
        )
        database.dao().saveMemory(record)
    }
}
