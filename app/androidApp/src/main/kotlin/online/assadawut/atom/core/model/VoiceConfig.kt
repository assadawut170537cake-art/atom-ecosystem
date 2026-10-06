package online.assadawut.atom.core.model

data class GeminiVoice(val id: String, val label: String)

object GeminiVoices {
    val all = listOf(
        GeminiVoice("Zephyr", "Zephyr"), GeminiVoice("Puck", "Puck"),
        GeminiVoice("Charon", "Charon"), GeminiVoice("Kore", "Kore"),
        GeminiVoice("Fenrir", "Fenrir"), GeminiVoice("Leda", "Leda"),
        GeminiVoice("Orus", "Orus"), GeminiVoice("Aoede", "Aoede"),
        GeminiVoice("Callirrhoe", "Callirrhoe"), GeminiVoice("Autonoe", "Autonoe"),
        GeminiVoice("Enceladus", "Enceladus"), GeminiVoice("Iapetus", "Iapetus"),
        GeminiVoice("Umbriel", "Umbriel"), GeminiVoice("Algieba", "Algieba")
    )
}