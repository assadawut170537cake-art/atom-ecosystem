plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "online.assadawut.atom"
version = "1.0.0"
application {
    mainClass = "online.assadawut.atom.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}