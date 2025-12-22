import org.gradle.jvm.toolchain.JavaLanguageVersion

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
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    `java-library`
    `maven-publish`
    signing
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

group = "io.github.kochkaev"
version = "$projectVersion-$telegrambotsVersion"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withJavadocJar()
    withSourcesJar()
}

// --- AND configure the task it creates ---
tasks.named<Jar>("sourcesJar") {
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
}


kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

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
    }
    repositories {
        maven {
            name = "local"
            url = uri("${rootProject.layout.buildDirectory}/repo")
        }
        maven {
            name = "sonatype"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) {
                "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            } else {
                "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            })
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}
signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["mavenJava"])
}

tasks.jar {
    archiveBaseName.set("kotlin-telegrambots")
    archiveVersion.set(version.toString())
}
