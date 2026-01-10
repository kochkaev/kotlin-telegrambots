
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
    ":meta",
    ":handlers-dsl",
    ":core",
    ":longpolling",
    ":client-okhttp"
)
