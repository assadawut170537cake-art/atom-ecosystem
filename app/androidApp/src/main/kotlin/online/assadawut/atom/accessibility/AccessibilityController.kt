package online.assadawut.atom.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccessibilityController {
    suspend fun clickByText(text: String): Boolean = withContext(Dispatchers.Main) {
        val service = ATOMAccessibilityService.instance ?: return@withContext false
        val root = service.rootInActiveWindow ?: return@withContext false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return@withContext true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return@withContext true
                }
                parent = parent.parent
            }
        }
        false
    }
    suspend fun inputText(viewId: String, text: String): Boolean = withContext(Dispatchers.Main) {
        val service = ATOMAccessibilityService.instance ?: return@withContext false
        val root = service.rootInActiveWindow ?: return@withContext false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNotEmpty()) {
            val node = nodes.first()
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return@withContext true
        }
        false
    }
}