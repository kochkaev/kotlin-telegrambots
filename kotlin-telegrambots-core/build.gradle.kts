
val telegrambotsVersion: String by project
val generatedSrcDir = layout.buildDirectory.dir("generated/source/kotlin")

// Configuration to download telegrambots-client sources
val telegramMetaSources: Configuration by configurations.creating
val telegramClientSources: Configuration by configurations.creating
val telegramClientClasspath: Configuration by configurations.creating

dependencies {
    api(project(":kotlin-telegrambots-meta"))
    api(project(":kotlin-telegrambots-handlers-dsl"))
    api("org.telegram:telegrambots-meta:$telegrambotsVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${project.property("kotlinxCoroutinesVersion")}")

    // Dependency for the generator to parse
    telegramClientSources("org.telegram:telegrambots-client:$telegrambotsVersion:sources") {
        isTransitive = false
    }
    telegramMetaSources("org.telegram:telegrambots-meta:$telegrambotsVersion:sources") {
        isTransitive = false
        }
    // Dependency for the symbol solver
    telegramClientClasspath("org.telegram:telegrambots-client:$telegrambotsVersion") {
        isTransitive = true // We need all transitive dependencies
    }
}

sourceSets {
    main {
        kotlin {
            srcDir(generatedSrcDir)
        }
    }
}

val unzipTelegramClientSources = tasks.register<Sync>("unzipTelegramClientSources") {
    from(zipTree(telegramClientSources.singleFile))
    into(layout.buildDirectory.dir("unzipped-client-sources"))
}
val unzipTelegramMetaSources = tasks.register<Sync>("unzipTelegramMetaSources") {
    from(zipTree(telegramMetaSources.singleFile))
    into(layout.buildDirectory.dir("unzipped-meta-sources"))
}

val generateAbstractKTelegramClient = tasks.register<GenerateAbstractKTelegramClientTask>("generateAbstractKTelegramClient") {
    sourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramMetaSources)
}

val generateTelegramClientSuspendable = tasks.register<GenerateTelegramClientSuspendableTask>("generateTelegramClientSuspendable") {
    sourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramMetaSources)
}

val generateDefaultKTelegramClient = tasks.register<GenerateDefaultKTelegramClientTask>("generateDefaultKTelegramClient") {
    sourcesDir.set(unzipTelegramClientSources.get().outputs.files.singleFile)
    metaSourcesDir.set(unzipTelegramMetaSources.get().outputs.files.singleFile)
    dependencyJars.from(telegramClientClasspath)
    outputDir.set(generatedSrcDir)
    dependsOn(unzipTelegramClientSources, unzipTelegramMetaSources)
}

tasks.named("compileKotlin") {
    dependsOn(generateTelegramClientSuspendable, generateAbstractKTelegramClient, generateDefaultKTelegramClient)
}
//tasks.named("sourcesJar") {
//    dependsOn(generateTelegramClientSuspendable, generateAbstractKTelegramClient, generateDefaultKTelegramClient)
//}
tasks.named("kotlinSourcesJar") {
    dependsOn(generateTelegramClientSuspendable, generateAbstractKTelegramClient, generateDefaultKTelegramClient)
}
