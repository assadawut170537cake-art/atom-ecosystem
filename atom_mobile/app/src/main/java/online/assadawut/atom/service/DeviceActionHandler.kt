package online.assadawut.atom.service

import online.assadawut.atom.model.RouterDecision

class DeviceActionHandler {
    fun executeDeviceAction(decision: RouterDecision): String {
        val target = decision.actionTarget ?: "unknown_action"
        val payload = decision.actionPayload ?: emptyMap()
        val state = payload["state"] ?: "ON"
        
        return when (target) {
            "device_torch" -> "ดำเนินการเปลี่ยนสถานะไฟฉายเป็น $state เรียบร้อยแล้วค่ะ"
            "device_connectivity" -> "ดำเนินการปรับสวิตช์การเชื่อมต่อเรียบร้อยแล้วค่ะ"
            "device_volume" -> "ดำเนินการปรับระดับเสียงเรียบร้อยแล้วค่ะ"
            "device_brightness" -> "ดำเนินการปรับความสว่างหน้าจอเรียบร้อยแล้วค่ะ"
            "app_launch" -> "กำลังเปิดแอปพลิเคชัน ${decision.extractedQuery} ให้ค่ะ"
            else -> "ดำเนินการคำสั่ง $target ($state) เรียบร้อยแล้วค่ะ"
        }
    }
}
