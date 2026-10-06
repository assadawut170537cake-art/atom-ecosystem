package online.assadawut.atom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import online.assadawut.atom.ui.ATOMApp
import online.assadawut.atom.ui.ATOMViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ATOMViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
            val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            } else true

            if (!audioGranted) {
                Toast.makeText(this, "กรุณาอนุญาตสิทธิ์ Microphone เพื่อใช้งานคำสั่งเสียง", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Microphone and Notification Permissions
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        val atom = application as ATOMApplication
        lifecycleScope.launch {
            atom.syncManager.pullOnOpen()
            atom.syncManager.startFiveMinutePush()
        }

        setContent {
            ATOMApp(
                viewModel = viewModel,
                onRequestAccessibilityPermission = { openAccessibilitySettings() }
            )
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "กรุณาเปิดการใช้งานบริการ ATOM Accessibility Service", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ไม่สามารถเปิดการตั้งค่า Accessibility ได้: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        val atom = application as ATOMApplication
        lifecycleScope.launch {
            atom.syncManager.pushOnClose()
        }
    }
}
