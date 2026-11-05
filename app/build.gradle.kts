plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.tonyapps.wetter"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.tonyapps.wetter"
        // Die Himmels-Animation nutzt AGSL (RuntimeShader), das gibt es ab Android 13.
        minSdk = 33
        targetSdk = 35
        // Die Build-Nummer aus GitHub Actions: Anzeige in der App und Release passen zusammen
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 10
        versionName = "1.8"
    }

    // Fester Schluessel im Repo: Jeder CI-Build traegt dieselbe Signatur,
    // dadurch laesst sich jede neue APK ueber die alte installieren.
    // Nur fuer die private Nutzung gedacht.
    signingConfigs {
        getByName("debug") {
            storeFile = file("wetter-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // Eine Quelle fuer GitHub und App
    sourceSets["main"].assets.srcDir("$rootDir/changelog")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
