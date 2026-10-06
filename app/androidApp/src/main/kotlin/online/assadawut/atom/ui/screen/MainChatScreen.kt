package online.assadawut.atom.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.assadawut.atom.core.model.AgentPersona
import online.assadawut.atom.core.model.OrbState
import online.assadawut.atom.ui.state.ATOMUiState
import online.assadawut.atom.ui.state.ChatTurn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.platform.LocalContext

// Sci-Fi Theme Palette
private val DarkBg = Color(0xFF090C15)
private val CardGlassBg = Color(0x22131929)
private val UserBubbleBg = Color(0x3300F0FF)
private val AtomNeonCyan = Color(0xFF00F0FF)
private val FridayNeonViolet = Color(0xFFD000FF)
private val UltronNeonRed = Color(0xFFFF0055)
private val JevNeonBlue = Color(0xFF00BFFF)
private val JulesNeonGreen = Color(0xFF00FF80)
private val TextMuted = Color(0xFF8A99AD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    state: ATOMUiState,
    onSendText: (String, Boolean) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSpeakText: (String) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onSwitchAgent: (AgentPersona) -> Unit,
    onRequestAccessibilityPermission: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToIntegration: () -> Unit,
    onNavigateToMcp: () -> Unit,
    onNavigateToPlugin: () -> Unit,
    onPickImage: (String, String) -> Unit = { _, _ -> },
    onSelectModel: (String) -> Unit = {},
    onPickFile: (String, String, String) -> Unit = { _, _, _ -> },
) {
    var inputText by remember { mutableStateOf("") }
    var isHeavyReasoning by remember { mutableStateOf(false) }
    var isVoiceActive by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: ATOM, 1: วันนี้, 2: ความจำ, 3: ไฟล์
    var memorySearchQuery by remember { mutableStateOf("") }
    var newTaskTitle by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
                onPickImage(base64, mimeType)
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "file"
                onPickFile(fileName, base64, mimeType)
            }
        }
    }

    // Dynamic Time Greeting (Matching & Superior to Jarvis CEO)
    val greetingText = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "สวัสดีตอนเช้า ลูกพี่ , บอส"
            in 12..16 -> "สวัสดีตอนบ่าย ลูกพี่ , บอส"
            in 17..21 -> "สวัสดีตอนเย็น ลูกพี่ , บอส"
            else -> "สวัสดีตอนดึก ลูกพี่ , บอส"
        }
    }

    // Auto scroll to latest chat turn when chat history updates
    LaunchedEffect(state.chatHistory.size, state.transcript) {
        if (state.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.chatHistory.size - 1)
        }
    }

    // Agent Theme Primary Color
    val activeNeonColor = when (state.agent) {
        AgentPersona.ATOM -> AtomNeonCyan
        AgentPersona.FRIDAY -> FridayNeonViolet
        AgentPersona.ULTRON -> UltronNeonRed
        AgentPersona.JEV -> JevNeonBlue
        AgentPersona.JULES -> JulesNeonGreen
    }

    // Orb State Color
    val orbPrimaryColor by animateColorAsState(
        targetValue = when (state.orbState) {
            OrbState.IDLE -> activeNeonColor
            OrbState.LISTENING -> Color(0xFF00FF66)
            OrbState.THINKING -> Color(0xFFFFB700)
            OrbState.SPEAKING -> Color(0xFF00E5FF)
        },
        animationSpec = tween(600),
        label = "OrbPrimaryColor"
    )

    // Rotation Infinite Transition for Sci-Fi Radar Rings
    val infiniteTransition = rememberInfiniteTransition(label = "RadarRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAngle"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sci-Fi Header Selector & Agent Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentPersona.entries.filter {
                        it == AgentPersona.ATOM || it == AgentPersona.FRIDAY || it == AgentPersona.ULTRON
                    }.forEach { persona ->
                        val isSelected = state.agent == persona
                        val chipColor = when (persona) {
                            AgentPersona.ATOM -> AtomNeonCyan
                            AgentPersona.FRIDAY -> FridayNeonViolet
                            AgentPersona.ULTRON -> UltronNeonRed
                            AgentPersona.JEV -> JevNeonBlue
                            AgentPersona.JULES -> JulesNeonGreen
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSwitchAgent(persona) }
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) chipColor else Color.DarkGray,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            color = if (isSelected) chipColor.copy(alpha = 0.2f) else Color(0xFF101622)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) chipColor else Color.Gray)
                                )
                                Text(
                                    text = persona.name,
                                    color = if (isSelected) Color.White else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Box {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { modelMenuExpanded = true }
                            .border(0.5.dp, activeNeonColor, RoundedCornerShape(12.dp)),
                        color = Color(0xFF101622)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.selectedModel,
                                fontSize = 10.sp,
                                color = activeNeonColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        listOf(
                            "gemini-3.8-flash",
                            "gemini-2.5-flash",
                            "gpt-4o",
                            "gpt-4o-mini",
                            "grok-beta",
                            "deepseek-r1"
                        ).forEach { modelName ->
                            DropdownMenuItem(
                                text = { Text(modelName, fontSize = 12.sp) },
                                onClick = {
                                    onSelectModel(modelName)
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.chatHistory.isNotEmpty()) {
                        IconButton(
                            onClick = onClearChatHistory,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF131B2A))
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = UltronNeonRed
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF131B2A))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = activeNeonColor
                        )
                    }
                }
            }

            // Main Tab Content Router
            when (activeTab) {
                0 -> { // Main ATOM Voice & Chat View
                    Column(modifier = Modifier.weight(1f)) {
                        // Greeting Banner (Matching Jarvis CEO)
                        Text(
                            text = greetingText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Compact Sci-Fi Reactor Core Indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .scale(pulseScale)
                                    .clickable {
                                        if (isVoiceActive) {
                                            isVoiceActive = false
                                            onStopVoice()
                                        } else {
                                            isVoiceActive = true
                                            onStartVoice()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val radius = size.minDimension / 2

                                    drawCircle(
                                        color = orbPrimaryColor.copy(alpha = 0.25f),
                                        radius = radius * 0.95f,
                                        style = Stroke(
                                            width = 1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), rotationAngle)
                                        )
                                    )

                                    drawArc(
                                        color = orbPrimaryColor.copy(alpha = 0.8f),
                                        startAngle = rotationAngle,
                                        sweepAngle = 100f,
                                        useCenter = false,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                orbPrimaryColor.copy(alpha = 0.8f),
                                                orbPrimaryColor.copy(alpha = 0.2f),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = radius * 0.7f
                                        ),
                                        radius = radius * 0.7f
                                    )
                                }

                                Text(
                                    text = state.orbState.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Long-Context Memory Status Badge
                            Surface(
                                color = Color(0xFF0F1827),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.border(0.5.dp, activeNeonColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🧠 CONTEXT MEMORY",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = activeNeonColor
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            text = "TTS: AUDIO ON",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF00FF66)
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${state.chatHistory.size} Turns | ${state.memories.size} Records Retained",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Quick Suggestion Prompt Chips (Matching Jarvis CEO)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("📰 ขอสรุปข่าววันนี้", "🚀 อัปเดตงานล่าสุด", "🧠 ค้นหาความจำ").forEach { suggestion ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSendText(suggestion, isHeavyReasoning) }
                                        .border(0.5.dp, Color(0xFF253348), RoundedCornerShape(8.dp)),
                                    color = Color(0xFF121B2D)
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Chat History Feed
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.chatHistory.isEmpty()) {
                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.horizontalGradient(
                                                    listOf(activeNeonColor.copy(alpha = 0.4f), Color.Transparent)
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            ),
                                        color = CardGlassBg
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = activeNeonColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "// SYSTEM READY & MIGRATED MEMORY ACTIVE",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = activeNeonColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = state.transcript.ifBlank { "ระบบ ATOM พร้อมปฏิบัติตามคำสั่งเสียงและมีระบบอ่านเสียงพูดตอบกลับครับลูกพี่..." },
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }

                            items(state.chatHistory) { turn ->
                                ChatTurnBubble(
                                    turn = turn,
                                    activeColor = activeNeonColor,
                                    onReplayAudio = { onSpeakText(turn.message) }
                                )
                            }

                            state.error?.let { err ->
                                item {
                                    Surface(
                                        color = Color(0x33FF0055),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "ALERT: $err",
                                            color = UltronNeonRed,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(10.dp),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // วันนี้ (Today Tasks & Schedule)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📅 ตารางงานและภารกิจวันนี้ (Today's Tasks)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }

                        Spacer(Modifier.height(10.dp))

                        // Task Creator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTaskTitle,
                                onValueChange = { newTaskTitle = it },
                                placeholder = { Text("เพิ่มภารกิจใหม่...", color = TextMuted, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (newTaskTitle.isNotBlank()) {
                                        onSendText("สร้าง Task ใหม่: $newTaskTitle", false)
                                        newTaskTitle = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(activeNeonColor)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.Black)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (state.queue.isEmpty()) {
                            Surface(
                                color = CardGlassBg,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("ไม่มีภารกิจค้างในคิวระบบ", color = activeNeonColor, fontWeight = FontWeight.Bold)
                                    Text("สั่งงาน ATOM หรือพิมพ์เพิ่มภารกิจใหม่ด้านบนได้ทันทีครับลูกพี่", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.queue) { q ->
                                    Card(colors = CardDefaults.cardColors(containerColor = CardGlassBg)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("Task: ${q.taskType}", color = activeNeonColor, fontWeight = FontWeight.Bold)
                                            Text("Agent: ${q.agent} | Priority: ${q.priority}", color = Color.White, fontSize = 12.sp)
                                            Text("Status: ${q.status}", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> { // ความจำ (553 Migrated Memories Bank Inspector)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text("🧠 คลังความทรงจำทั้งหมด (${state.memories.size} Records)", style = MaterialTheme.typography.titleMedium, color = activeNeonColor)

                        Spacer(Modifier.height(8.dp))

                        // Memory Search Filter Box
                        OutlinedTextField(
                            value = memorySearchQuery,
                            onValueChange = { memorySearchQuery = it },
                            placeholder = { Text("ค้นหาในความทรงจำ...", color = TextMuted, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = activeNeonColor) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        val filteredMemories = remember(state.memories, memorySearchQuery) {
                            if (memorySearchQuery.isBlank()) state.memories
                            else state.memories.filter { it.content.contains(memorySearchQuery, ignoreCase = true) }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(filteredMemories.take(50)) { mem ->
                                Card(colors = CardDefaults.cardColors(containerColor = CardGlassBg)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("ID: ${mem.id}", color = activeNeonColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(Modifier.height(4.dp))
                                        Text(mem.content, color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> { // ไฟล์ (Files & Vault Storage)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = activeNeonColor, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("📁 คลังไฟล์และเอกสาร (File Vault Storage)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = CardGlassBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("✓ Loaded Asset: jarvis_migrated_memories.json (553 Records)", color = activeNeonColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.height(4.dp))
                                Text("พร้อมรองรับการแนบไฟล์ PDF, รูปภาพ, และโปรเจกต์โค้ดเพื่อส่งวิเคราะห์", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Cyberpunk Action Shortcuts & Accessibility Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToIntegration() }
                        .border(0.5.dp, Color(0xFF253348), RoundedCornerShape(10.dp)),
                    color = Color(0xFF0F1626)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.IntegrationInstructions, contentDescription = null, tint = activeNeonColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Integrations", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onNavigateToMcp() }
                        .border(0.5.dp, Color(0xFF253348), RoundedCornerShape(10.dp)),
                    color = Color(0xFF0F1626)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = activeNeonColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("MCP Console", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRequestAccessibilityPermission() }
                        .border(0.5.dp, activeNeonColor.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
                    color = Color(0xFF152238)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null, tint = activeNeonColor, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Accessibility", fontSize = 9.sp, color = activeNeonColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Cyberpunk Input Controls
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isHeavyReasoning,
                            onCheckedChange = { isHeavyReasoning = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = activeNeonColor,
                                uncheckedColor = Color.Gray
                            )
                        )
                        Text(
                            "DEEP REASONING (HEAVY)",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isHeavyReasoning) activeNeonColor else TextMuted
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (isVoiceActive) {
                                    isVoiceActive = false
                                    onStopVoice()
                                } else {
                                    isVoiceActive = true
                                    onStartVoice()
                                }
                            },
                        color = if (isVoiceActive) UltronNeonRed else Color(0xFF131C2E)
                    ) {
                        Icon(
                            imageVector = if (isVoiceActive) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Mode",
                            tint = if (isVoiceActive) Color.White else activeNeonColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131C2E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Upload Image",
                            tint = activeNeonColor
                        )
                    }
                    IconButton(
                        onClick = {
                            docPickerLauncher.launch("*/*")
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131C2E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Upload Document",
                            tint = activeNeonColor
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("ป้อนคำสั่ง AI...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeNeonColor,
                            unfocusedBorderColor = Color(0xFF253348),
                            focusedContainerColor = Color(0xFF0F1524),
                            unfocusedContainerColor = Color(0xFF0B101D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendText(inputText, isHeavyReasoning)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(activeNeonColor, activeNeonColor.copy(alpha = 0.7f))
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Navigation Bar Tabs (Exact & Superior to Jarvis CEO)
            NavigationBar(
                containerColor = Color(0xFF0D121F),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(0.5.dp, Color(0xFF212D42), RoundedCornerShape(16.dp))
            ) {
                listOf(
                    Triple(0, "ATOM", Icons.Default.SmartToy),
                    Triple(1, "วันนี้", Icons.Default.CalendarToday),
                    Triple(2, "ความจำ", Icons.Default.Psychology),
                    Triple(3, "ไฟล์", Icons.Default.Folder)
                ).forEach { (index, label, icon) ->
                    val isSelected = activeTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = index },
                        icon = {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) activeNeonColor else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 10.sp,
                                color = if (isSelected) activeNeonColor else TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatTurnBubble(
    turn: ChatTurn,
    activeColor: Color,
    onReplayAudio: () -> Unit = {}
) {
    val timeStr = remember(turn.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(turn.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (turn.isUser) 16.dp else 4.dp,
                        bottomEnd = if (turn.isUser) 4.dp else 16.dp
                    )
                )
                .border(
                    width = 0.8.dp,
                    color = if (turn.isUser) activeColor.copy(alpha = 0.6f) else Color(0xFF253348),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (turn.isUser) 16.dp else 4.dp,
                        bottomEnd = if (turn.isUser) 4.dp else 16.dp
                    )
                ),
            color = if (turn.isUser) UserBubbleBg else CardGlassBg
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = turn.sender,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (turn.isUser) activeColor else Color.White
                        )
                        if (!turn.isUser) {
                            IconButton(
                                onClick = onReplayAudio,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Replay Audio",
                                    tint = activeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = timeStr,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = turn.message,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
