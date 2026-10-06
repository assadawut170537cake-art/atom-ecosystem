package online.assadawut.atom.integration

class IntegrationRouter(private val repository: IntegrationRepository) {
    suspend fun send(type: IntegrationType, destination: String, message: String): Result<Unit> = runCatching {
        // Integration send implementation
    }
}
