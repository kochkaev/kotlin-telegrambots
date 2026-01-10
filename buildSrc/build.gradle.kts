import java.util.Properties

// Read properties from the root project's gradle.properties file
val properties = file("../gradle.properties").takeIf { it.exists() }?.let {
    Properties().apply { it.inputStream().use { load(it) } }
}

val kotlinpoetVersion: String = properties?.getProperty("kotlinpoetVersion") ?: "1.16.0"
val javaparserCoreVersion: String = properties?.getProperty("javaparserCoreVersion") ?: "3.25.10"
val reflectionsVersion: String = properties?.getProperty("reflectionsVersion") ?: "0.10.2"
val telegrambotsVersion: String = properties?.getProperty("telegrambotsVersion") ?: "0.10.2"

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:$kotlinpoetVersion")
    implementation("org.telegram:telegrambots-meta:$telegrambotsVersion")
    implementation("org.telegram:telegrambots-client:$telegrambotsVersion")
    implementation("org.reflections:reflections:$reflectionsVersion")
    implementation("com.github.javaparser:javaparser-core:$javaparserCoreVersion")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:$javaparserCoreVersion")
}
