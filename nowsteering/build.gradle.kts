import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// +10: the run counter restarted when the workflow file was renamed; version codes must keep rising.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt() + 10
val gitSha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
val buildTime = Instant.now().toString()

android {
    namespace = "com.nowsteering.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nowsteering.app"
        minSdk = 33
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.2.$runNumber"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    // Same committed key as the probe, so every build installs over the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
