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
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("app.cash.sqldelight") version "2.1.0"
    id("dev.icerock.mobile.multiplatform-resources") version "0.25.1"
}

android {
    namespace = "project.pipepipe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "project.pipepipe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2000
        versionName = "5.0.0-alpha2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "PipePipe-${versionName}-${buildType.name}.apk"
        }
    }
}

kotlin {
    jvmToolchain(24)
    androidTarget {

    }
    sourceSets {
        commonMain {
            dependencies {
                implementation("project.pipepipe:shared:5.0.0-alpha1")
                implementation("project.pipepipe:extractor:5.0.0-alpha1")

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("io.ktor:ktor-client-core:3.2.3")
                implementation("com.russhwolf:multiplatform-settings:1.3.0")
                implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")
                implementation("dev.icerock.moko:resources:0.25.1")
                implementation("io.github.darkokoa:datetime-wheel-picker:1.1.0-alpha05-compose1.9")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("com.materialkolor:material-kolor:3.0.1")
            }
        }

        androidMain {
            dependencies {
                implementation("androidx.core:core-ktx:1.17.0")
                implementation("androidx.appcompat:appcompat:1.7.1")
                implementation("com.google.android.material:material:1.12.0")
                implementation("androidx.work:work-runtime-ktx:2.10.5")
                implementation("androidx.activity:activity-compose:1.10.1")
                implementation("androidx.compose:compose-bom:2025.08.01")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.datastore:datastore-preferences:1.1.7")
                implementation("androidx.compose.material3:material3:1.4.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
                implementation("io.coil-kt.coil3:coil-compose:3.3.0")
                implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
                implementation("androidx.media3:media3-session:1.8.0")
                implementation("androidx.media3:media3-exoplayer:1.8.0")
                implementation("androidx.media3:media3-ui:1.8.0")
                implementation("androidx.media3:media3-common:1.8.0")
                implementation("androidx.media3:media3-datasource:1.8.0")
                implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
                implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
                implementation("androidx.concurrent:concurrent-futures:1.3.0")
                implementation("androidx.compose.foundation:foundation")
                implementation("io.ktor:ktor-client-okhttp:3.2.3")
                implementation("androidx.compose.material:material-icons-extended:1.7.8")
                implementation("androidx.navigation:navigation-compose:2.9.3")
                implementation("dev.icerock.moko:resources-compose:0.25.1")
                implementation("app.cash.sqldelight:android-driver:2.0.2")
                implementation("sh.calvin.reorderable:reorderable:3.0.0")
                implementation("io.github.panpf.zoomimage:zoomimage-compose-coil3:1.4.0")
            }
        }
    }
}

multiplatformResources {
    resourcesPackage.set("project.pipepipe.app")
}
