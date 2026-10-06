package online.assadawut.atom.plugin

interface ATOMPlugin {
    val id: String
    val name: String
    suspend fun initialize()
    suspend fun shutdown()
    suspend fun execute(context: PluginContext): PluginResult
}

data class PluginContext(
    val agent: String,
    val input: String
)

data class PluginResult(
    val success: Boolean,
    val output: String
)

class PluginRegistry {
    private val plugins = mutableMapOf<String, ATOMPlugin>()

    suspend fun register(plugin: ATOMPlugin) {
        plugin.initialize()
        plugins[plugin.id] = plugin
    }

    suspend fun execute(id: String, context: PluginContext): PluginResult {
        val plugin = plugins[id] ?: return PluginResult(false, "Plugin not found: $id")
        return plugin.execute(context)
    }

    suspend fun shutdown() {
        plugins.values.forEach { it.shutdown() }
        plugins.clear()
    }
}
