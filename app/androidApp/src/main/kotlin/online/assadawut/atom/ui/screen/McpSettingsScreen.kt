package online.assadawut.atom.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.assadawut.atom.mcp.MCPToolDescriptor
import online.assadawut.atom.mcp.MCPTransportType

@Composable
fun McpSettingsScreen(
    tools: List<MCPToolDescriptor>,
    errorMessage: String?,
    onConnect: (id: String, name: String, transport: MCPTransportType, endpoint: String) -> Unit,
    onListTools: (String) -> Unit,
    onInvokeTool: (String, String, Map<String, Any?>) -> Unit,
    onDisconnect: (String) -> Unit,
    onBack: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(MCPTransportType.HTTP) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(16.dp))
            Text("MCP Servers", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Server ID") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint") }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            OutlinedButton(onClick = { dropdownExpanded = true }) {
                Text(transport.name)
            }
            DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                DropdownMenuItem(text = { Text("HTTP") }, onClick = { transport = MCPTransportType.HTTP; dropdownExpanded = false })
                DropdownMenuItem(text = { Text("SSE") }, onClick = { transport = MCPTransportType.SSE; dropdownExpanded = false })
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onConnect(id, name, transport, endpoint) }) { Text("Connect") }
            Button(onClick = { onListTools(id) }) { Text("List Tools") }
            Button(onClick = { onDisconnect(id) }) { Text("Disconnect") }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                id = "notebooklm"
                name = "NotebookLM"
                transport = MCPTransportType.HTTP
                endpoint = "https://newreleases.io/project/github/PleasePrompto/notebooklm-mcp/release/v1.2.0"
                onConnect(id, name, transport, endpoint)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Auto-Connect NotebookLM MCP")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text("Tools", style = MaterialTheme.typography.titleMedium)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tools) { tool ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(tool.name, style = MaterialTheme.typography.bodyLarge)
                        Text(tool.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
