plugins {
    id("com.android.application")
}

// CI supplies these for version tags; local/debug builds keep the upstream defaults.
val ciVersionName = providers.gradleProperty("ciVersionName").orElse("0.1.0-diagnostic")
val ciVersionCode = providers.gradleProperty("ciVersionCode").map { it.toInt() }.orElse(1)

android {
    namespace = "com.southjack.bridge"
    compileSdk = 34
    compileSdkExtension = 12
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.southjack.bridge"
        minSdk = 29
        targetSdk = 34
        versionCode = ciVersionCode.get()
        versionName = ciVersionName.get()
    }

    buildTypes {
        debug {
            // CI debug certificates are ephemeral; keep test installs separate.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // CI signs the unsigned APK externally after Gradle finishes.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Kotlin's Gradle plugin provides the stdlib; app code uses platform APIs.
}
