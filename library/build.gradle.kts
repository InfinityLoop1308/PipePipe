import org.gradle.kotlin.dsl.implementation

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
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
    id("dev.icerock.mobile.multiplatform-resources")
}

android {
    namespace = "project.pipepipe.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(24)
    androidTarget {

    }
    sourceSets {
        commonMain {
            dependencies {
                implementation("project.pipepipe:shared")
                implementation("project.pipepipe:extractor")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.ktor:ktor-client-core:3.2.3")
                implementation("com.russhwolf:multiplatform-settings:1.3.0")
                implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")
                implementation("dev.icerock.moko:resources:0.25.1")
                implementation("io.github.darkokoa:datetime-wheel-picker:1.1.0-alpha05-compose1.9")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("com.materialkolor:material-kolor:3.0.1")
                implementation("com.fasterxml.jackson.core:jackson-core:2.20.0")
                implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
                implementation("dev.tmapps:konnection:1.4.5")
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
                implementation("io.coil-kt.coil3:coil-compose:3.3.0")
                implementation("dev.icerock.moko:resources-compose:0.25.1")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.ui:ui:1.7.3")
                implementation("org.jetbrains.compose.ui:ui-text:1.7.3")
                implementation("org.jetbrains.compose.foundation:foundation:1.7.3")
                implementation("org.jetbrains.compose.material3:material3:1.7.3")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
                implementation("io.github.panpf.zoomimage:zoomimage-compose-coil3:1.4.0")
                implementation("sh.calvin.reorderable:reorderable:3.0.0")

                // FileKit for cross-platform file picker
                implementation("io.github.vinceglb:filekit-dialogs-compose:0.12.0")

            }
        }
        androidMain {
            dependencies {
                implementation("org.ocpsoft.prettytime:prettytime:5.0.7.Final")
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.core:core-ktx:1.17.0")
                implementation("app.cash.sqldelight:android-driver:2.0.2")
                implementation("app.cash.sqldelight:primitive-adapters:2.0.2")
                implementation("androidx.media3:media3-session:1.8.0")
                implementation("androidx.media3:media3-exoplayer:1.8.0")
                implementation("androidx.media3:media3-ui:1.8.0")
                implementation("androidx.media3:media3-common:1.8.0")
            }
        }
    }
}

multiplatformResources {
    resourcesPackage.set("project.pipepipe.app")
}


sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("project.pipepipe.database")
            version = 902
        }
    }
}
