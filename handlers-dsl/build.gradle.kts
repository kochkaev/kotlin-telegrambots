
plugins {
    kotlin("jvm")
    id("maven-publish")
}

val telegrambotsVersion: String by project
val generatedSrcDir = layout.buildDirectory.dir("generated/source/kotlin")

val telegramMetaSources: Configuration by configurations.creating

dependencies {
    api(project(":meta"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${project.property("kotlinxCoroutinesVersion")}")

    telegramMetaSources("org.telegram:telegrambots-meta:$telegrambotsVersion:sources") {
        isTransitive = false
    }
}

sourceSets {
    main {
        kotlin {
            srcDir(generatedSrcDir)
        }
    }
}

val unzipTelegramMetaSources = tasks.register<Sync>("unzipTelegramMetaSources") {
    from(zipTree(telegramMetaSources.singleFile))
    into(layout.buildDirectory.dir("unzipped-meta-sources"))
}

val generateHandlersDsl = tasks.register<GenerateHandlersDslTask>("generateHandlersDsl") {
    sourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramMetaSources)
}

tasks.named("compileKotlin") {
    dependsOn(generateHandlersDsl)
}
//tasks.named("sourcesJar") {
//    dependsOn(generateHandlersDsl)
//}
tasks.named("kotlinSourcesJar") {
    dependsOn(generateHandlersDsl)
}
