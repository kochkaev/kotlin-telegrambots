import java.util.Properties

// Read properties from the root project's gradle.properties file
val properties = Properties()
file("../gradle.properties").inputStream().use { properties.load(it) }

val kotlinpoetVersion: String by properties
val telegrambotsVersion: String by properties
val javaparserCoreVersion: String by properties
val reflectionsVersion: String by properties
val lombokVersion: String by properties

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup:kotlinpoet:$kotlinpoetVersion")
    implementation("org.telegram:telegrambots:$telegrambotsVersion")
    implementation("org.reflections:reflections:$reflectionsVersion")
    implementation("org.projectlombok:lombok:$lombokVersion")
    implementation("com.github.javaparser:javaparser-core:$javaparserCoreVersion")
    implementation("com.github.javaparser:javaparser-symbol-solver-core:$javaparserCoreVersion") // Add the symbol solver
}
