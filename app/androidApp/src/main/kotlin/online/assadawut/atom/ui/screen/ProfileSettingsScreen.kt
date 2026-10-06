package online.assadawut.atom.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.assadawut.atom.database.ProfileRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    profile: ProfileRecord?,
    ultronMode: Boolean,
    autoVoice: Boolean,
    handsFree: Boolean,
    wakeWordEnabled: Boolean,
    onSave: (ProfileRecord, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var displayName by remember { mutableStateOf(profile?.displayName ?: "บอส") }
    var favLanguages by remember { mutableStateOf(profile?.favLanguages ?: "") }
    var techStack by remember { mutableStateOf(profile?.techStack ?: "") }
    var codingStyle by remember { mutableStateOf(profile?.codingStyle ?: "") }
    var currentProject by remember { mutableStateOf(profile?.currentProject ?: "") }
    var language by remember { mutableStateOf(profile?.language ?: "th") }
    var customNotes by remember { mutableStateOf(profile?.customNotes ?: "") }

    var isUltronMode by remember { mutableStateOf(ultronMode) }
    var isAutoVoice by remember { mutableStateOf(autoVoice) }
    var isHandsFree by remember { mutableStateOf(handsFree) }
    var isWakeWord by remember { mutableStateOf(wakeWordEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("การตั้งค่าโปรไฟล์และโหมด") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("ข้อมูลส่วนตัว", style = MaterialTheme.typography.titleLarge)
            
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("ชื่อเรียก (DisplayName)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("ภาษาหลัก", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("th" to "ไทย", "en" to "English", "mix" to "ผสม").forEach { (code, label) ->
                    FilterChip(
                        selected = language == code,
                        onClick = { language = code },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = favLanguages,
                onValueChange = { favLanguages = it },
                label = { Text("ภาษาโปรแกรมที่ชอบ (คั่นด้วยลูกน้ำ)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = techStack,
                onValueChange = { techStack = it },
                label = { Text("Tech Stack") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = codingStyle,
                onValueChange = { codingStyle = it },
                label = { Text("สไตล์การเขียนโค้ด") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = currentProject,
                onValueChange = { currentProject = it },
                label = { Text("โปรเจกต์ปัจจุบัน") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = customNotes,
                onValueChange = { customNotes = it },
                label = { Text("บันทึกเพิ่มเติม (Custom Notes)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("โหมดการทำงาน", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ULTRON Protocol (โหด/ตรงประเด็น)")
                Switch(checked = isUltronMode, onCheckedChange = { isUltronMode = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Voice (AI ตอบด้วยเสียงอัตโนมัติ)")
                Switch(checked = isAutoVoice, onCheckedChange = { isAutoVoice = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hands-Free (สนทนาต่อเนื่อง)")
                Switch(checked = isHandsFree, onCheckedChange = { isHandsFree = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wake Word (หวัดดีอะตอม)")
                Switch(checked = isWakeWord, onCheckedChange = { isWakeWord = it })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val newRecord = ProfileRecord(
                        id = "default",
                        displayName = displayName,
                        favLanguages = favLanguages,
                        techStack = techStack,
                        codingStyle = codingStyle,
                        currentProject = currentProject,
                        language = language,
                        customNotes = customNotes,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(newRecord, isUltronMode, isAutoVoice, isHandsFree, isWakeWord)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("บันทึกข้อมูล")
            }
        }
    }
}
