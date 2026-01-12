val okhttpVersion: String by project

plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":kotlin-telegrambots-core"))
    api("com.squareup.okhttp3:okhttp:$okhttpVersion")
}
