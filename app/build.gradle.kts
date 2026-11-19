import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val assistantUrl = providers.gradleProperty("selunaAssistantUrl").orNull
assistantUrl?.let { url ->
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null && uri.query == null) {
        "selunaAssistantUrl debe ser la URL HTTPS del endpoint /chat de Seluna"
    }
}

android {
    namespace = "com.seluna.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seluna.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "ASSISTANT_URL", "\"${assistantUrl.orEmpty()}\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            buildConfigField("String", "ASSISTANT_URL", "\"${assistantUrl ?: "http://10.0.2.2:8787/chat"}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation(platform("com.google.firebase:firebase-bom:35.2.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
