
val projectVersion: String by project
val telegrambotsVersion: String by project
val kotlinxCoroutinesVersion: String by project

val telegramSources: Configuration by configurations.creating

dependencies {
    telegramSources("org.telegram:telegrambots-meta:$telegrambotsVersion:sources") {
        isTransitive = false
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.telegram:telegrambots:$telegrambotsVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
}

val generatedSrcDir = "src/main/generated/kotlin"
val unzippedSourcesDir = layout.buildDirectory.dir("unzipped-sources")

sourceSets {
    main {
        kotlin {
            srcDir(generatedSrcDir)
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

tasks.named("compileKotlin") {
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "ru.kochkaev.kotlin"
            artifactId = "telegrambots"
            version = "$projectVersion-$telegrambotsVersion"

            from(components["java"])

            val sourcesJar by tasks.registering(Jar::class) {
                dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
                from(sourceSets.main.get().allSource)
                archiveClassifier.set("sources")
            }
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri("${rootProject.layout.buildDirectory}/repo")
        }
    }
}
