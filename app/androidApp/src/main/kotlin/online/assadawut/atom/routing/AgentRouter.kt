package online.assadawut.atom.routing

enum class Route { OLLAMA, API, GEMINI_LIVE, QUEUE }
data class RoutingContext(val pcOnline: Boolean, val voice: Boolean, val heavy: Boolean)

class AgentRouter {
    fun route(context: RoutingContext): Route {
        return when {
            context.voice -> Route.GEMINI_LIVE
            context.heavy -> Route.QUEUE
            context.pcOnline -> Route.OLLAMA
            else -> Route.API
        }
    }
}