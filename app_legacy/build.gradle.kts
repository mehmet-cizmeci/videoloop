import java.util.Properties

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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
