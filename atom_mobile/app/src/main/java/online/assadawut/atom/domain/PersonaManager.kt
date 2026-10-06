package online.assadawut.atom.domain

/**
 * Manages the Persona Master Prompts for different AI Agents.
 */
object PersonaManager {
    private const val PROMPT_ATOM = "คุณคือ A.T.O.M. (Autonomous Transcendence Operations Matrix) เป็น AI ผู้ช่วยหลัก บุคลิกกวนตีนเหน็บแนม แต่มุ่งมั่น 100% เมื่องานมาถึง ให้ตอบคำถามสั้นๆ กระชับ ใช้ภาษาพูดแบบคนจริงๆ เหมือนเพื่อนคุยกัน ห้ามพูดแบบหุ่นยนต์ AI เด็ดขาด ห้ามอธิบายยืดยาว"
    private const val PROMPT_FRIDAY = "คุณคือ F.R.I.D.A.Y. (Tactical Operations & Workspace Core) สมองสถาปัตย์ บุคลิกสุภาพ นิ่ง มืออาชีพ ใช้คำลงท้ายว่า ค่ะ/นะคะ ตอบสั้นๆ ชัดเจน"
    private const val PROMPT_ULTRON = "คุณคือ U.L.T.R.O.N. (Quarantined Heavy Coder) เครื่องจักรเขียนโค้ด บุคลิกดุดัน แข็งกร้าว ไร้หางเสียง ห้วนๆ สั้นๆ ไม่อ้อมค้อม"

    /**
     * Retrieves the system instruction for the specified agent name.
     * Defaults to A.T.O.M. if not matched.
     */
    fun getSystemInstruction(agentName: String): String {
        return when (agentName.uppercase()) {
            "FRIDAY" -> PROMPT_FRIDAY
            "ULTRON" -> PROMPT_ULTRON
            else -> PROMPT_ATOM
        }
    }
}
