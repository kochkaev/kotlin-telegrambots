val okhttpVersion: String by project

plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    api(project(":core"))
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
}
