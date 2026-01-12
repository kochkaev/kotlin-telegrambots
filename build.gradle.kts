import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.vanniktech.maven.publish") version "0.35.0"
}

allprojects {
    group = "io.github.kochkaev"
    version = "${property("projectVersion") as String}+${property("telegrambotsVersion") as String}"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    the<JavaPluginExtension>().apply {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
//        withSourcesJar()
//        withJavadocJar()
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    dependencies {
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
        testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    afterEvaluate {
        tasks.withType<GenerateModuleMetadata> {
            dependsOn(tasks.named("kotlinSourcesJar"))
            dependsOn(tasks.named("plainJavadocJar"))
        }
    }

    mavenPublishing {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()

        pom {
            name.set("Kotlin Telegram Bots - ${project.name}")
            description.set("Kotlin extensions and DSL for Telegram Bots API")
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
