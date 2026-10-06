package online.assadawut.atom.database

import online.assadawut.atom.model.VectorRecord

/**
 * อินเทอร์เฟซสำหรับแปลงข้อความเป็นเวกเตอร์ความหมาย (Embedding)
 *
 * หน้าที่: แยกขั้นตอนการสร้าง embedding ออกจากตัวเก็บเวกเตอร์
 *         เพื่อให้ VectorStorageManager ทดสอบได้โดยไม่ต้องใช้ HTTP จริง
 * พารามิเตอร์: ไม่มีในตัวอินเทอร์เฟซ
 * ประเภทอ็อบเจกต์ที่ส่งคืน: List<Float> - เวกเตอร์ความหมาย
 */
interface TextEmbedder {
    /**
     * หน้าที่: แปลงข้อความเป็นเวกเตอร์ความหมาย
     * พารามิเตอร์:
     *   text (String) - ข้อความที่ต้องการแปลง
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<Float> - เวกเตอร์ความหมาย (ว่างเปล่าหาก input ว่าง)
     */
    fun embed(text: String): List<Float>
}

/**
 * ตัวสร้าง embedding ฝั่ง Local ผ่าน Ollama /api/embed
 *
 * หน้าที่: เรียก Ollama เพื่อสร้าง embedding ให้ VectorStorageManager
 * พารามิเตอร์คอนสตรัคเตอร์:
 *   client (ATOMHttpClient) - ตัวส่งคำขอ HTTP
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี
 */
class OllamaTextEmbedder(
    private val client: online.assadawut.atom.network.ATOMHttpClient
) : TextEmbedder {

    /**
     * หน้าที่: เรียก Ollama /api/embed เพื่อสร้าง embedding แบบ Local
     * พารามิเตอร์:
     *   text (String) - ข้อความที่ต้องการแปลง
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<Float> - เวกเตอร์ความหมาย
     * ข้อยกเว้น: ATOMNetworkException - เมื่อ Ollama เข้าไม่ถึง
     */
    override fun embed(text: String): List<Float> {
        if (text.isBlank()) return emptyList()
        return client.callOllamaEmbed(text)
    }
}

/**
 * คลาสเก็บความจำในรูปแบบเวกเตอร์ความหมาย (Vector Store)
 *
 * หน้าที่: เก็บข้อมูลพร้อมเวกเตอร์ และค้นหาด้วย Cosine Similarity
 *         แบบคำนวณตรง (brute force) เหมาะกับความจุระดับหลักพันรายการ
 * พารามิเตอร์: ไม่มี (stateless constructor)
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี
 */
class VectorStore {

    /** ข้อมูลหนึ่งรายการพร้อมเวกเตอร์ความหมายที่คำนวณไว้แล้ว */
    private data class StoredVector(
        val id: String,
        val content: String,
        val vector: List<Float>
    )

    private val items = mutableListOf<StoredVector>()

    /**
     * หน้าที่: บันทึกหรือแทนที่ข้อมูลพร้อมเวกเตอร์ความหมาย
     * หากมี id ซ้ำแล้วจะเขียนทับรายการเดิม
     * พารามิเตอร์:
     *   id (String) - รหัสอ้างอิงของรายการ (ไม่เว้นวรรค)
     *   content (String) - ข้อความเนื้อหาที่ต้องการจดจำ
     *   vector (List<Float>) - เวกเตอร์ความหมายของข้อความ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun upsert(id: String, content: String, vector: List<Float>) {
        if (vector.isEmpty()) return
        items.removeAll { it.id == id }
        items.add(StoredVector(id = id, content = content, vector = vector))
    }

    /**
     * หน้าที่: ค้นหาข้อมูลที่มีความคล้ายคลึงสูงสุดตาม Cosine Similarity
     * พารามิเตอร์:
     *   queryVector (List<Float>) - เวกเตอร์ของคำถามที่ต้องการค้น
     *   topK (Int) - จำนวนผลลัพธ์สูงสุดที่ต้องการ
     * ประเภทอ็อบเจกต์ที่ส่งคืน: List<VectorRecord> - เรียงจากความคล้ายมากไปน้อย
     */
    fun search(queryVector: List<Float>, topK: Int): List<VectorRecord> {
        if (queryVector.isEmpty() || topK <= 0) return emptyList()

        return items
            .map { stored ->
                VectorRecord(
                    id = stored.id,
                    content = stored.content,
                    similarityScore = cosineSimilarity(queryVector, stored.vector)
                )
            }
            .filter { it.similarityScore > 0.0 }
            .sortedByDescending { it.similarityScore }
            .take(topK)
    }

    /**
     * หน้าที่: จำนวนรายการที่เก็บไว้ทั้งหมด (สำหรับตรวจสอบ)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Int - จำนวนรายการ
     */
    fun size(): Int = items.size

    /**
     * หน้าที่: ล้างข้อมูลทั้งหมดออกจาก store (สำหรับทดสอบ)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun clear() = items.clear()

    /**
     * หน้าที่: คำนวณค่า Cosine Similarity ระหว่างเวกเตอร์สองตัว
     * สูตร: dot(a,b) / (||a|| * ||b||) คืนค่า 0.0 หากมิติไม่ตรงกันหรือเป็นศูนย์เวกเตอร์
     * พารามิเตอร์:
     *   a (List<Float>) - เวกเตอร์ตัวที่หนึ่ง
     *   b (List<Float>) - เวกเตอร์ตัวที่สอง
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Double - ค่าความคล้ายในช่วง -1.0 ถึง 1.0
     */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }

        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (Math.sqrt(normA) * Math.sqrt(normB))
    }
}