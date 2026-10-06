package online.assadawut.atom.network

/**
 * คลาสควบคุมสถานะวงจรสำหรับบริการฝั่ง Cloud (Circuit Breaker)
 *
 * หน้าที่: เมื่อ Cloud ล้มเหลวติดต่อกันเกินกำหนด จะเปิดวงจร (OPEN)
 *         เพื่อหยุดยิงคำขอออกไปชั่วคราว ให้ระบบสลับไปใช้ Local ทันที
 *         แล้วค่อย ๆ ทยอยทดสอบกลับเมื่อครบเวลา (HALF_OPEN -> CLOSED)
 * พารามิเตอร์คอนสตรัคเตอร์:
 *   failureThreshold (Int) - จำนวนความล้มเหลวติดต่อกันที่จะตัดวงจร
 *   resetAfterMs (Long) - ระยะเวลาที่วงจรจะค้างอยู่ในสถานะเปิด (มิลลิวินาที)
 *   clock (Long) - เมธอดที่คืนเวลาปัจจุบัน หน่วยมิลลิวินาที (ms) สำหรับทดสอบ
 * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่มี (เป็น state object)
 *
 * Thread-safety: ใช้ synchronized กับ internal lock เดียว
 */
class CircuitBreaker(
    private val failureThreshold: Int = ATOMHttpClient.CIRCUIT_FAILURE_THRESHOLD,
    private val resetAfterMs: Long = ATOMHttpClient.CIRCUIT_RESET_AFTER_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** สถานะทั้งสามของวงจร */
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val lock = Any()

    private var currentState: State = State.CLOSED
    private var consecutiveFailures: Int = 0
    private var openedAtMs: Long = 0L

    /**
     * หน้าที่: ถามว่าตอนนี้อนุญาตให้ส่งคำขอออกไปได้หรือไม่
     *         หากเป็น HALF_OPEN และครบเวลา จะกลับเป็น CLOSED
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Boolean - true หากอนุญาตให้ยิงคำขอ
     */
    fun allowRequest(): Boolean {
        synchronized(lock) {
            return when (currentState) {
                State.CLOSED -> true
                State.HALF_OPEN -> true
                State.OPEN -> {
                    val elapsed = clock() - openedAtMs
                    if (elapsed >= resetAfterMs) {
                        currentState = State.HALF_OPEN
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    /**
     * หน้าที่: บันทึกว่าคำขอสำเร็จ พร้อมรีเซ็ตตัวนับความล้มเหลว
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun onSuccess() {
        synchronized(lock) {
            consecutiveFailures = 0
            currentState = State.CLOSED
        }
    }

    /**
     * หน้าที่: บันทึกว่าคำขอล้มเหลว และตัดวงจรเมื่อครบเกณฑ์
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun onFailure() {
        synchronized(lock) {
            consecutiveFailures += 1
            if (consecutiveFailures >= failureThreshold) {
                currentState = State.OPEN
                openedAtMs = clock()
            }
        }
    }

    /**
     * หน้าที่: บังคับรีเซ็ตวงจรกลับเป็นสถานะปกติ CLOSED ทันที
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: ไม่คืนค่า
     */
    fun reset() {
        synchronized(lock) {
            currentState = State.CLOSED
            consecutiveFailures = 0
            openedAtMs = 0L
        }
    }

    /**
     * หน้าที่: อ่านสถานะปัจจุบันของวงจรโดยไม่เปลี่ยนค่าใด ๆ
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: State - สถานะปัจจุบัน
     */
    fun state(): State = synchronized(lock) { currentState }

    /**
     * หน้าที่: อ่านจำนวนความล้มเหลวติดต่อกัน (สำหรับตรวจสอบ/ทดสอบ)
     * พารามิเตอร์: ไม่มี
     * ประเภทอ็อบเจกต์ที่ส่งคืน: Int - จำนวนความล้มเหลว
     */
    fun failureCount(): Int = synchronized(lock) { consecutiveFailures }
}