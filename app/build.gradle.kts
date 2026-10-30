import java.time.Instant

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val gitSha = (System.getenv("GITHUB_SHA") ?: "local").take(7)
val buildTime = Instant.now().toString()

android {
    namespace = "com.nowsteering.probe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nowsteering.probe"
        minSdk = 29
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.1.$runNumber-probe"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    // Fixed debug key committed to the repo so every CI build installs over the previous one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
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
