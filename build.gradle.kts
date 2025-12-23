import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion

val projectVersion: String by project
val telegrambotsVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project

val telegramSources: Configuration by configurations.creating

dependencies {
    telegramSources("org.telegram:telegrambots-meta:$telegrambotsVersion:sources") {
        isTransitive = false
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("com.vanniktech.maven.publish") version "0.28.0"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.telegram:telegrambots:$telegrambotsVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}")
}

val generatedSrcDir = "src/main/generated/kotlin"
val unzippedSourcesDir = layout.buildDirectory.dir("unzipped-sources")

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin") // Include manual sources
            srcDir(generatedSrcDir)   // Include generated sources
        }
    }
}

val unzipTelegramSources = tasks.register<Sync>("unzipTelegramSources") {
    group = "build"
    description = "Unzips the TelegramBots-Meta sources.jar for code generation."
    from(zipTree(telegramSources.singleFile))
    into(unzippedSourcesDir)
}

val generateTelegramBotExtensions = tasks.register<GenerateExtensionsTask>("generateTelegramBotExtensions") {
    group = "build"
    description = "Generates high-level, idiomatic Kotlin extensions for API methods."
    sourcesDir.set(unzipTelegramSources.get().destinationDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramSources)
}

val generateObjectBuilders = tasks.register<GenerateObjectBuildersTask>("generateObjectBuilders") {
    group = "build"
    description = "Generates builder functions for API objects."
    sourcesDir.set(unzipTelegramSources.get().destinationDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramSources)
}

val generateHandlersDsl = tasks.register<GenerateHandlersDslTask>("generateHandlersDsl") {
    group = "build"
    description = "Generates type-safe DSL handlers for Update types."
    sourcesDir.set(unzipTelegramSources.get().destinationDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramSources)
}

val generateAll = tasks.register("generateAll") {
    group = "build"
    description = "Runs all code generation tasks."
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders, generateHandlersDsl)
}

tasks.named("compileKotlin") {
    dependsOn(generateAll)
}

group = "io.github.kochkaev"
version = "$projectVersion-$telegrambotsVersion"



java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.named("kotlinSourcesJar") {
    dependsOn(generateAll)
}

tasks.named("javadoc") {
    dependsOn(generateAll)
}

afterEvaluate {
    tasks.withType<GenerateModuleMetadata> {
        dependsOn(tasks.named("kotlinSourcesJar"))
        dependsOn(tasks.named("plainJavadocJar"))
    }
}

kotlin {
    jvmToolchain(17)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.S01)
    if (project.hasProperty("signing.key")) {
        signAllPublications()
    }

    pom {
        name.set("Kotlin Telegram Bots Extensions")
        description.set("A library providing Kotlin extension functions for the TelegramBots API.")
        url.set("https://github.com/kochkaev/kotlin-telegrambots")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("kochkaev")
                name.set("Dmitrii Kochkaev")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/kochkaev/kotlin-telegrambots.git")
            developerConnection.set("scm:git:ssh://github.com/kochkaev/kotlin-telegrambots.git")
            url.set("https://github.com/kochkaev/kotlin-telegrambots/tree/main")
        }
    }
}

tasks.jar {
    archiveBaseName.set("kotlin-telegrambots")
    archiveVersion.set(version.toString())
}
