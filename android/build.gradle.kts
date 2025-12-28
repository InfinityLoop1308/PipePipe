import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin {
    jvmToolchain(24)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

ext {
    set("abiCodes", mapOf(
        "armeabi-v7a" to 1,
        "x86" to 2,
        "x86_64" to 3,
        "arm64-v8a" to 4
    ))
}

android {
    namespace = "project.pipepipe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "project.pipepipe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2002
        versionName = "5.0.0-beta3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            reset()
            isEnable = true
            isUniversalApk = true // Generate a universal APK in addition to ABI-specific APKs
            include("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
        }
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
        sourceCompatibility = JavaVersion.VERSION_24
        targetCompatibility = JavaVersion.VERSION_24
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    applicationVariants.all {
        val abiCodesMap = project.ext.get("abiCodes") as Map<*, *>
        val baseVersionCode = defaultConfig.versionCode ?: 0

        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiFilter = outputImpl.getFilter(com.android.build.OutputFile.ABI)
            if (abiFilter != null) {
                outputImpl.outputFileName = "PipePipe-${versionName}-${abiFilter}-${buildType.name}.apk"
                val abiCode = abiCodesMap[abiFilter] as? Int ?: 0
                (outputImpl as com.android.build.gradle.api.ApkVariantOutput).versionCodeOverride = 100 * baseVersionCode + abiCode
            } else {
                outputImpl.outputFileName = "PipePipe-${versionName}-universal-${buildType.name}.apk"
                (outputImpl as com.android.build.gradle.api.ApkVariantOutput).versionCodeOverride = 100 * baseVersionCode
            }
        }
    }
}

dependencies {
    implementation(project(":library"))
    implementation("project.pipepipe:shared")
    implementation("project.pipepipe:extractor")

    // KMP library dependencies that are used in android module
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.github.darkokoa:datetime-wheel-picker:1.1.0-alpha05-compose1.9")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
    implementation("com.materialkolor:material-kolor:3.0.1")
    implementation("com.russhwolf:multiplatform-settings:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose:compose-bom:2025.08.01")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.ktor:ktor-client-okhttp:3.2.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("org.jetbrains.compose.material3:material3:1.7.3")
    implementation("dev.icerock.moko:resources-compose:0.25.1")
    implementation("app.cash.sqldelight:android-driver:2.0.2")
    implementation("app.cash.sqldelight:primitive-adapters:2.0.2")
    implementation("sh.calvin.reorderable:reorderable:3.0.0")

    implementation("org.ocpsoft.prettytime:prettytime:5.0.7.Final")

    // youtubedl-android for download functionality
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // Jackson for JSON parsing (required by SharedContext)
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.0")

}
