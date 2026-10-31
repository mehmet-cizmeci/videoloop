plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.videoloop.legacy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videoloop.legacy"
        minSdk = 19
        targetSdk = 34
        versionCode = 3
        versionName = "1.2-legacy"
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
