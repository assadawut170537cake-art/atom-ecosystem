package online.assadawut.atom.core.skill

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DynamicSkill(
    val id: String,
    val name: String,
    val description: String,
    val promptTemplate: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

class SkillEngine {
    private val _skills = MutableStateFlow<Map<String, DynamicSkill>>(emptyMap())
    val skills: StateFlow<Map<String, DynamicSkill>> = _skills.asStateFlow()

    fun registerSkill(skill: DynamicSkill) {
        _skills.update { it + (skill.id to skill) }
    }

    fun removeSkill(id: String) {
        _skills.update { it - id }
    }

    fun executeSkill(id: String, input: String): String {
        val skill = _skills.value[id] ?: return "Skill not found"
        if (!skill.enabled) return "Skill is disabled"
        return skill.promptTemplate.replace("{input}", input)
    }
}
