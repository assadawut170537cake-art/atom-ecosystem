package online.assadawut.atom.mcp

enum class MCPTransportType { HTTP, SSE, STDIO }

data class MCPServerConfig(
    val id: String,
    val name: String,
    val transport: MCPTransportType,
    val endpoint: String,
    val command: String = ""
)

data class MCPToolDescriptor(
    val serverId: String,
    val name: String,
    val description: String,
    val inputSchema: String
)

data class MCPToolResult(
    val success: Boolean,
    val output: String
)
