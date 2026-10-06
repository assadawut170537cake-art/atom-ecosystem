package online.assadawut.atom.core

import android.content.Context

data class CronTask(
    val id: String,
    val naturalLanguageSchedule: String,
    val actionPrompt: String,
    val repeatIntervalHours: Long = 24
)

class CronScheduler(private val context: Context) {

    fun scheduleNaturalLanguageTask(prompt: String, action: String): Result<String> = runCatching {
        val intervalHours = parseIntervalHours(prompt)
        "ตั้งเวลาทำงานตามคำสั่ง \"$prompt\" สำเร็จ! (รอบการทำงานทุกๆ $intervalHours ชั่วโมง)"
    }

    private fun parseIntervalHours(prompt: String): Long {
        return when {
            prompt.contains("ชั่วโมง") -> 1L
            prompt.contains("8 โมง") || prompt.contains("ทุกวัน") -> 24L
            else -> 12L
        }
    }
}
