import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// La URL y la clave pública de Supabase viven en local.properties (no se sube a Git).
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(name: String) = (localProps.getProperty(name) ?: "").replace("\"", "")

// Firma de la versión para publicar. La llave y su contraseña viven FUERA del repositorio;
// local.properties solo dice dónde está el archivo (firma.archivo=C:/Users/.../orbita-firma.properties).
val firma = Properties().apply {
    val ruta = localProp("firma.archivo")
    if (ruta.isNotBlank() && file(ruta).exists()) file(ruta).inputStream().use { load(it) }
}

android {
    namespace = "com.orbitasinertegia.orbita"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.orbitasinertegia.orbita"
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("supabase.anonKey")}\"")
        buildConfigField("String", "WEB_URL", "\"https://orbita-web-ten.vercel.app\"")
        // ID público del cliente web de Google (el mismo que usa la web). No es secreto.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"614135047244-sgsnsavc2u5pdvjkkg3pjhnvksj6no3l.apps.googleusercontent.com\"")
    }

    signingConfigs {
        if (firma.getProperty("storeFile") != null) {
            create("publicacion") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("publicacion")?.let { signingConfig = it }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.androidx.work)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.ktor.client.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}