plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hackproff.wallpapers4k"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hackproff.wallpapers4k"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Chave opcional do Pexels (https://www.pexels.com/api/). Sem chave, a fonte Pexels fica desativada.
        val pexelsKey = providers.gradleProperty("PEXELS_KEY").getOrElse("")
        buildConfigField("String", "PEXELS_KEY", "\"$pexelsKey\"")

        // Chave do Serper.dev (Google Imagens): https://serper.dev. Sem chave, a fonte Google fica desativada.
        val serperKey = providers.gradleProperty("SERPER_KEY").getOrElse("")
        buildConfigField("String", "SERPER_KEY", "\"$serperKey\"")
    }

    // Chave de assinatura própria: o APK mantém a mesma assinatura em todo build (permite atualizar sem desinstalar).
    // Atenção: a chave está no repositório. Serve para uso pessoal, não para publicar na Play Store.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "dLa6lJhyoB04hh-_FTaVt7Jh"
            keyAlias = "wallpapers"
            keyPassword = "dLa6lJhyoB04hh-_FTaVt7Jh"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
