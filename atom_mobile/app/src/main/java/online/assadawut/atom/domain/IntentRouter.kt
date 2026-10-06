package online.assadawut.atom.domain

/**
 * Analyzes voice or text transcripts to detect specific intents like switching agents.
 */
object IntentRouter {

    /**
     * Analyzes the given transcript and returns an AgentSwitchPayload if an agent switch is requested.
     */
    fun analyzeTranscript(transcript: String): AgentSwitchPayload? {
        val lowerText = transcript.lowercase()

        // Check for F.R.I.D.A.Y.
        if (lowerText.contains("ต่อสายไฟรเดย์") || 
            lowerText.contains("เปลี่ยนเป็นไฟรเดย์") || 
            lowerText.contains("เรียกไฟรเดย์")
        ) {
            return AgentSwitchPayload(
                targetAgent = "FRIDAY",
                mode = "finish_sentence"
            )
        }

        // Check for U.L.T.R.O.N.
        if (lowerText.contains("เปลี่ยนเป็นอัลตรอน") || 
            lowerText.contains("เรียกอัลตรอน") || 
            lowerText.contains("ต่อสายอัลตรอน")
        ) {
            return AgentSwitchPayload(
                targetAgent = "ULTRON",
                mode = "immediate_cut"
            )
        }

        // Check for returning to A.T.O.M.
        if (lowerText.contains("เปลี่ยนเป็นอะตอม") || 
            lowerText.contains("เรียกอะตอม") || 
            lowerText.contains("กลับมาอะตอม")
        ) {
            return AgentSwitchPayload(
                targetAgent = "ATOM",
                mode = "finish_sentence"
            )
        }

        return null
    }
}
