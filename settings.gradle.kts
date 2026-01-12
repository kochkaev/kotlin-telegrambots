
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-telegrambots"

include(
    ":kotlin-telegrambots-meta",
    ":kotlin-telegrambots-handlers-dsl",
    ":kotlin-telegrambots-core",
    ":kotlin-telegrambots-longpolling",
    ":kotlin-telegrambots-webhook",
    ":kotlin-telegrambots-client-okhttp"
)
