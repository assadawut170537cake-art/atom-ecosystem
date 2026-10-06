package online.assadawut.atom.core.model

enum class Provider {
    GEMINI, OPENAI, GROK, DAHL
}

data class PersonaConfig(
    val name: String = "ATOM",
    val speechStyle: String = "natural",
    val customDescription: String = "",
    val provider: Provider = Provider.GEMINI
)

enum class AgentPersona {
    ATOM, FRIDAY, ULTRON, JEV, JULES
}
