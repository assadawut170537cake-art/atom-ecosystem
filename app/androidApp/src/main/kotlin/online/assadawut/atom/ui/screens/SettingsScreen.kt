package online.assadawut.atom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    currentModel: String = "gemini-3.8-flash",
    onSelectModel: (String) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    val availableModels = listOf(
        "gemini-3.8-flash",
        "gemini-2.5-flash",
        "gpt-4o",
        "gpt-4o-mini",
        "grok-beta",
        "deepseek-r1"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings & Model Switcher", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("โมเดล AI ปัจจุบัน (Selected Model)", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    availableModels.take(3).forEach { model ->
                        FilterChip(
                            selected = currentModel == model,
                            onClick = { onSelectModel(model) },
                            label = { Text(model, fontSize = 11.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    availableModels.drop(3).forEach { model ->
                        FilterChip(
                            selected = currentModel == model,
                            onClick = { onSelectModel(model) },
                            label = { Text(model, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            onClick = onOpenProfile,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("โปรไฟล์ & Memory", style = MaterialTheme.typography.bodyLarge)
                Text("ชื่อ ภาษา Tech Stack ULTRON", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Integrations", style = MaterialTheme.typography.bodyLarge)
                Text("LINE / Telegram / Slack tokens", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MCP Servers", style = MaterialTheme.typography.bodyLarge)
                Text("Model Context Protocol", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Plugin Console", style = MaterialTheme.typography.bodyLarge)
                Text("Accessibility commands", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
