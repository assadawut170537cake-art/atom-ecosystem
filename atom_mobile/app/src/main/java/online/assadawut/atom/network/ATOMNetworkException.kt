package online.assadawut.atom.network

/**
 * คลาสข้อยกเว้นเฉพาะสำหรับปัญหาเครือข่ายของ ATOM
 *
 * หน้าที่: ห่อหุ้มข้อผิดพลาดเครือข่ายพร้อมระบุว่าเป็นฝั่ง Cloud หรือไม่
 *         เพื่อให้ Pipeline รู้ว่าควรสลับกลับ Local ทันทีหรือไม่
 * พารามิเตอร์คอนสตรัคเตอร์:
 *   message (String) - คำอธิบายข้อผิดพลาด
 *   isCloudFailure (Boolean) - true หากเป็นความล้มเหลวของฝั่ง Cloud
 *   cause (Throwable?) - ต้นตอของข้อผิดพลาด
 *   retryAfterMs (Long?) - ค่า Retry-After ที่ server แจ้งมา (มิลลิวินาที)
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี (เป็น Exception)
 */
class ATOMNetworkException(
    override val message: String,
    val isCloudFailure: Boolean = true,
    override val cause: Throwable? = null,
    var retryAfterMs: Long? = null
) : Exception(message, cause)
