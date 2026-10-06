package online.assadawut.atom.database

import online.assadawut.atom.model.ExecutionMode
import online.assadawut.atom.model.VectorRecord
import online.assadawut.atom.network.ATOMHttpClient
import online.assadawut.atom.network.ATOMNetworkException

/**
 * คลาสบริหารจัดการเวกเตอร์ความจำและการค้นหาความหมายเชิงพื้นที่
 * ครอบคลุมการสร้าง Embedding และ Cosine Similarity Search
 *
 * หน้าที่: รับข้อความ query แล้วแปลงเป็นเวกเตอร์ ค้นหาข้อมูลที่คล้ายกัน
 *         แล้วคืนผลลัพธ์เรียงตามความคล้ายมากไปน้อย (Step 3 ของ Pipeline)
 * พารามิเตอร์คอนสตรัคเตอร์:
 *   embedder (TextEmbedder) - ตัวแปลงข้อความเป็นเวกเตอร์
 *   store (VectorStore) - ตัวเก็บและค้นเวกเตอร์
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี
 *
 * หมายเหตุ: การเก็บข้อมูลถาวร (SQLite-vec / Vectorize) ยังไม่ได้ต่อใน
 * เวอร์ชันนี้ ข้อมูลจะอยู่ใน memory ตลอดอายุ process เท่านั้น
 */
class VectorStorageManager(
    private val embedder: TextEmbedder = OllamaTextEmbedder(ATOMHttpClient()),
    private val store: VectorStore = VectorStore()
) {

    companion object {
        /** เกณฑ์ความคล้ายขั้นต่ำที่ Step 4 ใช้ตัดสิน (ตรงกับ PipelineCoordinator) */
        const val RELEVANCE_THRESHOLD = 0.65
    }

    /**
     * หน้าที่: บันทึกข้อมูลใหม่พร้อมสร้าง embedding เก็บลงใน vector store
     * พารามิเตอร์:
     *   id (String) - รหัสอ้างอิงของรายการ
     *   content (String) - ข้อความที่ต้องการจดจำ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Boolean - true หากบันทึกสำเร็จ
     * ข้อยกเว้น: ATOMNetworkException - หากสร้าง embedding ไม่สำเร็จ
     */
    fun remember(id: String, content: String): Boolean {
        if (content.isBlank()) return false
        val vector = embedder.embed(content)
        if (vector.isEmpty()) return false
        store.upsert(id = id, content = content, vector = vector)
        return true
    }

    /**
     * หน้าที่: ค้นหาข้อมูลที่มีความคล้ายกับคำค้นมากที่สุด topK รายการ
     * พารามิเตอร์:
     *   queryText (String) - ข้อความที่ต้องการค้นหา
     *   topK (Int) - จำนวนผลลัพธ์สูงสุดที่ต้องการ
     *   mode (ExecutionMode) - โหมดการทำงาน (Local หรือ Cloud)
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<VectorRecord> - ผลลัพธ์เรียงตามความคล้าย
     *
     * หมายเหตุ: โหมด CLOUD_NATIVE ปัจจุบันยังสร้าง embedding แบบ Local เพราะ
     * endpoint ของ Cloudflare Vectorize ยังไม่ได้ตั้งค่า เมื่อตั้งค่าแล้วจะ
     * สลับไปเรียกฝั่ง Cloud ที่นี่โดยตรง
     */
    fun searchSimilar(
        queryText: String,
        topK: Int = 3,
        mode: ExecutionMode = ExecutionMode.LOCAL_FIRST
    ): List<VectorRecord> {
        if (queryText.isBlank() || topK <= 0) return emptyList()

        return try {
            val queryVector = embedder.embed(queryText)
            if (queryVector.isEmpty()) return emptyList()
            store.search(queryVector = queryVector, topK = topK)
        } catch (_: ATOMNetworkException) {
            // ถ้าฝั่ง Cloud ล้มเหลวในโหมด CLOUD_NATIVE ให้ลอง Local ทันที
            if (mode == ExecutionMode.CLOUD_NATIVE) {
                tryLocalFallback(queryText, topK)
            } else {
                emptyList()
            }
        }
    }

    /**
     * หน้าที่: ลองค้นหาซ้ำด้วย embedder สำรองฝั่ง Local เมื่อ Cloud ล้มเหลว
     * พารามิเตอร์:
     *   queryText (String) - ข้อความที่ต้องการค้นหา
     *   topK (Int) - จำนวนผลลัพธ์สูงสุดที่ต้องการ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<VectorRecord> - ผลลัพธ์จากการค้นแบบ Local
     */
    private fun tryLocalFallback(
        queryText: String,
        topK: Int
    ): List<VectorRecord> {
        return try {
            val localEmbedder = OllamaTextEmbedder(ATOMHttpClient())
            val queryVector = localEmbedder.embed(queryText)
            if (queryVector.isEmpty()) return emptyList()
            store.search(queryVector = queryVector, topK = topK)
        } catch (localEx: Exception) {
            emptyList()
        }
    }

    /**
     * หน้าที่: คืนจำนวนรายการที่เก็บอยู่ใน store (สำหรับตรวจสอบ/ทดสอบ)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Int - จำนวนรายการ
     */
    fun storedCount(): Int = store.size()

    /**
     * หน้าที่: ล้างข้อมูลทั้งหมด (สำหรับรีเซ็ต/ทดสอบ)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun clearAll() = store.clear()
}
