package online.assadawut.atom.provider

class ProviderException(
    val providerName: String,
    message: String,
    cause: Throwable? = null,
) : Exception("[$providerName] $message", cause)