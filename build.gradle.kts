import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.jvm.toolchain.JavaLanguageVersion

val projectVersion: String by project
val telegrambotsVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project

val telegramMetaSources: Configuration by configurations.creating
val telegrambotsSources: Configuration by configurations.creating

dependencies {
    telegramMetaSources("org.telegram:telegrambots-meta:$telegrambotsVersion:sources") {
        isTransitive = false
    }
    telegrambotsSources("org.telegram:telegrambots:$telegrambotsVersion:sources") {
        isTransitive = false
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("com.vanniktech.maven.publish") version "0.35.0"
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
val unzippedMetaSourcesDir = layout.buildDirectory.dir("unzipped-meta-sources")
val unzippedBotSourcesDir = layout.buildDirectory.dir("unzipped-bot-sources")

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin") // Include manual sources
            srcDir(generatedSrcDir)   // Include generated sources
        }
    }
}

val unzipTelegramMetaSources = tasks.register<Sync>("unzipTelegramMetaSources") {
    group = "build"
    description = "Unzips the TelegramBots-Meta sources.jar for code generation."
    from(zipTree(telegramMetaSources.singleFile))
    into(unzippedMetaSourcesDir)
}

val unzipTelegrambotsSources = tasks.register<Sync>("unzipTelegrambotsSources") {
    group = "build"
    description = "Unzips the TelegramBots sources.jar for code generation."
    from(zipTree(telegrambotsSources.singleFile))
    into(unzippedBotSourcesDir)
}

val generateTelegramBotExtensions = tasks.register<GenerateExtensionsTask>("generateTelegramBotExtensions") {
    group = "build"
    description = "Generates high-level, idiomatic Kotlin extensions for API methods."
    sourcesDir.set(unzippedMetaSourcesDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramMetaSources)
}

val generateObjectBuilders = tasks.register<GenerateObjectBuildersTask>("generateObjectBuilders") {
    group = "build"
    description = "Generates builder functions for API objects."
    sourcesDir.set(unzippedMetaSourcesDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramMetaSources)
}

val generateHandlersDsl = tasks.register<GenerateHandlersDslTask>("generateHandlersDsl") {
    group = "build"
    description = "Generates type-safe DSL handlers for Update types."
    sourcesDir.set(unzippedMetaSourcesDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegramMetaSources)
}

val generateKTelegramBot = tasks.register<GenerateKTelegramBotTask>("generateKTelegramBot") {
    group = "build"
    description = "Generates a suspendable KTelegramBot wrapper."
    botSourcesDir.set(unzippedBotSourcesDir)
    metaSourcesDir.set(unzippedMetaSourcesDir)
    outputDir.set(file(generatedSrcDir))
    dependsOn(unzipTelegrambotsSources, unzipTelegramMetaSources)
}

val generateAll = tasks.register("generateAll") {
    group = "build"
    description = "Runs all code generation tasks."
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders, generateHandlersDsl, generateKTelegramBot)
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
    withSourcesJar()
}

tasks.kotlinSourcesJar {
    dependsOn(generateAll)
}

tasks.named("javadoc") {
    dependsOn(generateAll)
}

tasks.named("sourcesJar") {
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
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
//    if (project.hasProperty("signingInMemoryKey") || project.hasProperty("signing.key")) {
//        signAllPublications()
//    }

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
publishing {
    repositories {
        mavenLocal()
    }
}

tasks.jar {
    archiveBaseName.set("kotlin-telegrambots")
    archiveVersion.set(version.toString())
}
