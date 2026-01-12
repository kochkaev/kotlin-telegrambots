plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":kotlin-telegrambots-core"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${project.property("kotlinxCoroutinesVersion")}")

    testImplementation(project(":kotlin-telegrambots-client-okhttp"))
}
