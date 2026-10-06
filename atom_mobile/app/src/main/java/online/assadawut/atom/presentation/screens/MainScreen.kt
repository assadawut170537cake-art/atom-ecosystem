package online.assadawut.atom.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.firstOrNull
import online.assadawut.atom.presentation.AtomMainViewModel
import online.assadawut.atom.presentation.components.AssistantState
import online.assadawut.atom.presentation.components.ParticleOrb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val viewModel = remember { AtomMainViewModel(context) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        var apiKeyText by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            apiKeyText = viewModel.settingsRepository.geminiApiKey.firstOrNull() ?: ""
        }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("ตั้งค่า Gemini API Key") },
            text = {
                Column {
                    Text("ใส่ Gemini API Key ของคุณเพื่อใช้งานระบบคุมสตรีมสดสองทาง (Bidi Live Audio)")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveApiKey(apiKeyText.trim())
                        showSettingsDialog = false
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("A.T.O.M. Mobile") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "ATOM") },
                    label = { Text("ATOM") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "วันนี้") },
                    label = { Text("วันนี้") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "ความจำ") },
                    label = { Text("ความจำ") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> AtomTab(viewModel = viewModel)
                1 -> QueueTab()
                2 -> SystemTab()
            }
        }
    }
}

@Composable
fun AtomTab(viewModel: AtomMainViewModel) {
    val assistantState by viewModel.assistantState.collectAsState()
    val orbColor by viewModel.orbColor.collectAsState()
    val currentAgent by viewModel.currentAgent.collectAsState()
    val transcript by viewModel.transcript.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AGENT: $currentAgent",
            style = MaterialTheme.typography.titleMedium,
            color = orbColor
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.clickable { viewModel.toggleVoiceSession() }
        ) {
            ParticleOrb(
                state = assistantState,
                color = orbColor
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = when (assistantState) {
                AssistantState.LISTENING -> "🔴 สตรีมสดเปิดอยู่: กำลังฟังเสียงของคุณ..."
                AssistantState.SPEAKING -> "🔊 $currentAgent กำลังตอบกลับด้วยเสียง..."
                AssistantState.THINKING -> "⚡ กำลังเชื่อมต่อ Gemini Live..."
                else -> "⚡ แตะลูกแก้ว Orb หรือกดปุ่มด้านล่างเพื่อสายคุยสด"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = orbColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = transcript,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("บทสนทนาสด (Live Transcript)") },
            readOnly = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = { viewModel.toggleVoiceSession() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (assistantState != AssistantState.IDLE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Mic")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (assistantState != AssistantState.IDLE) "ตัดสายการคุยสด" else "เปิดสายคุยสด (Stream Live)"
            )
        }
    }
}
