package online.assadawut.atom.plugin

class AccessibilityPlugin : ATOMPlugin {
    override val id: String = "accessibility"
    override val name: String = "Accessibility Plugin"

    override suspend fun initialize() {}
    override suspend fun shutdown() {}

    override suspend fun execute(context: PluginContext): PluginResult {
        return PluginResult(true, "Executed accessibility command: ${context.input}")
    }
}
