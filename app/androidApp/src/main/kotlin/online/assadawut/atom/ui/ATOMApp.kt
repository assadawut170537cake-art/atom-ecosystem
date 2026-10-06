package online.assadawut.atom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import online.assadawut.atom.core.model.AgentSwitchPayload
import online.assadawut.atom.ui.screen.*
import online.assadawut.atom.ui.screens.SettingsScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.util.Base64

enum class SettingsRoute {
    MAIN, PERSONA, INTEGRATION, MCP, PLUGIN, PROFILE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ATOMApp(
    viewModel: ATOMViewModel,
    onRequestAccessibilityPermission: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var settingsRoute by remember { mutableStateOf(SettingsRoute.MAIN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (settingsRoute) {
                            SettingsRoute.MAIN -> "ATOM Mobile (${state.agent.name})"
                            SettingsRoute.PERSONA -> "Persona & Model Settings"
                            SettingsRoute.INTEGRATION -> "Integration Credentials"
                            SettingsRoute.MCP -> "MCP Server Settings"
                            SettingsRoute.PLUGIN -> "Plugin Console"
                            SettingsRoute.PROFILE -> "Profile Settings"
                        }
                    )
                },
                actions = {
                    if (settingsRoute != SettingsRoute.MAIN) {
                        IconButton(onClick = { settingsRoute = SettingsRoute.MAIN }) {
                            Icon(Icons.Default.Settings, contentDescription = "Home")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (settingsRoute) {
                SettingsRoute.MAIN -> {
                    MainChatScreen(
                        state = state,
                        onSendText = viewModel::sendText,
                        onStartVoice = viewModel::startVoice,
                        onStopVoice = viewModel::stopVoice,
                        onSpeakText = viewModel::speakText,
                        onClearChatHistory = viewModel::clearChatHistory,
                        onSwitchAgent = { agent ->
                            viewModel.switchAgent(agent, AgentSwitchPayload(agent.name, "finish_sentence"))
                        },
                        onRequestAccessibilityPermission = onRequestAccessibilityPermission,
                        onNavigateToSettings = { settingsRoute = SettingsRoute.PERSONA },
                        onNavigateToIntegration = { settingsRoute = SettingsRoute.INTEGRATION },
                        onNavigateToMcp = { settingsRoute = SettingsRoute.MCP },
                        onNavigateToPlugin = { settingsRoute = SettingsRoute.PLUGIN },
                        onPickImage = { base64, mimeType ->
                            viewModel.sendImage("วิเคราะห์รูปภาพนี้", base64, mimeType)
                        },
                        onSelectModel = viewModel::setSelectedModel,
                        onPickFile = { fileName, base64, mimeType ->
                            viewModel.sendText("ไฟล์ที่แนบ: $fileName ($mimeType)\nข้อมูล: ${base64.take(100)}...")
                        }
                    )
                }
                SettingsRoute.PERSONA -> {
                    SettingsScreen(
                        currentModel = state.selectedModel,
                        onSelectModel = viewModel::setSelectedModel,
                        onOpenProfile = { settingsRoute = SettingsRoute.PROFILE }
                    )
                }
                SettingsRoute.PROFILE -> {
                    ProfileSettingsScreen(
                        profile = state.profile,
                        ultronMode = state.ultronMode,
                        autoVoice = state.autoVoice,
                        handsFree = state.handsFree,
                        wakeWordEnabled = state.wakeWordEnabled,
                        onSave = { p, u, a, h, w ->
                            viewModel.saveProfile(p, u, a, h, w)
                            settingsRoute = SettingsRoute.MAIN
                        },
                        onBack = { settingsRoute = SettingsRoute.MAIN }
                    )
                }
                SettingsRoute.INTEGRATION -> {
                    IntegrationSettingsScreen(
                        credentials = state.integrationCredentials,
                        onSave = viewModel::saveIntegrationCredentials,
                        onBack = { settingsRoute = SettingsRoute.MAIN }
                    )
                }
                SettingsRoute.MCP -> {
                    McpSettingsScreen(
                        tools = state.mcpTools,
                        errorMessage = state.error,
                        onConnect = { id, name, transport, endpoint ->
                            viewModel.connectMcpServer(id, name, transport, endpoint)
                        },
                        onListTools = viewModel::listMcpTools,
                        onInvokeTool = { serverId, toolName, args ->
                            viewModel.invokeMcpTool(serverId, toolName, args)
                        },
                        onDisconnect = viewModel::disconnectMcpServer,
                        onBack = { settingsRoute = SettingsRoute.MAIN }
                    )
                }
                SettingsRoute.PLUGIN -> {
                    PluginConsoleScreen(
                        onRun = viewModel::runAccessibility,
                        onBack = { settingsRoute = SettingsRoute.MAIN }
                    )
                }
            }
        }
    }
}
