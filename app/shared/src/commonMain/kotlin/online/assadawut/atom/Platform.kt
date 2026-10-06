package online.assadawut.atom

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform