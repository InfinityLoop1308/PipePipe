buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("dev.icerock.moko:resources-generator:0.25.1")
    }
}
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("app.cash.sqldelight") version "2.1.0"
    id("dev.icerock.mobile.multiplatform-resources") version "0.25.1"
}
