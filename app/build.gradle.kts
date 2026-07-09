plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.southjack.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.southjack.bridge"
        minSdk = 29          // CallRedirectionService requires API 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-diagnostic"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Intentionally zero third-party deps: everything below uses platform APIs
    // (RoleManager, TelecomManager, CallRedirectionService). Kotlin stdlib is
    // added by the Kotlin plugin.
}
