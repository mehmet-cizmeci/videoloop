import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.videoloop"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.videoloop"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Yayın imzası: anahtar ve parolalar depo dışında (%USERPROFILE%\.imza\videoloop-imza.properties,
    // VIDEOLOOP_IMZA ortam değişkeniyle değiştirilebilir). Yoksa debug anahtarı kullanılır. Bkz. docs/IMZALAMA.md
    val imzaDosyasi = file(
        System.getenv("VIDEOLOOP_IMZA") ?: "${System.getProperty("user.home")}/.imza/videoloop-imza.properties"
    )
    if (imzaDosyasi.exists()) {
        val imza = Properties().apply { imzaDosyasi.inputStream().use { load(it) } }
        signingConfigs.create("yayin") {
            storeFile = file(imza.getProperty("storeFile"))
            storePassword = imza.getProperty("storePassword")
            keyAlias = imza.getProperty("keyAlias")
            keyPassword = imza.getProperty("keyPassword")
        }
    } else {
        logger.warn("UYARI: Yayın anahtarı yok ($imzaDosyasi); release APK debug anahtarıyla imzalanacak.")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("yayin") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
}
