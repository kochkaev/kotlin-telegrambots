
plugins {
    kotlin("jvm")
    id("maven-publish")
}

val telegrambotsVersion: String by project
val generatedSrcDir = layout.buildDirectory.dir("generated/source/kotlin")

val telegramMetaSources: Configuration by configurations.creating

dependencies {
    api("org.telegram:telegrambots-meta:$telegrambotsVersion")
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

val generateTelegramBotExtensions = tasks.register<GenerateTelegramBotExtensionsTask>("generateTelegramBotExtensions") {
    sourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramMetaSources)
}

val generateObjectBuilders = tasks.register<GenerateObjectBuildersTask>("generateObjectBuilders") {
    sourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramMetaSources)
}

tasks.named("compileKotlin") {
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
}
//tasks.named("sourcesJar") {
//    dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
//}
tasks.named("kotlinSourcesJar") {
    dependsOn(generateTelegramBotExtensions, generateObjectBuilders)
}