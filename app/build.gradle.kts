import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.ResValue
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
var hasReleaseSigning = false
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
    val storeFilePath = keystoreProperties.getProperty("storeFile")
    val storePassword = keystoreProperties.getProperty("storePassword")
    val keyPassword = keystoreProperties.getProperty("keyPassword")
    hasReleaseSigning = !storeFilePath.isNullOrBlank() &&
        !storePassword.isNullOrBlank() &&
        !keyPassword.isNullOrBlank() &&
        rootProject.file(storeFilePath).exists()
}

// Firebase Web API key lives in local.properties (not committed): firebase.apiKey=...
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val firebaseApiKey: String = localProperties.getProperty("firebase.apiKey")
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException(
        "Missing firebase.apiKey in local.properties (Firebase Web API key used for sign-in)."
    )

android {
    namespace = "zhb.company.smart_home"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "zhb.company.smart_home"
        minSdk = 24
        targetSdk = 36
        versionCode = 18
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the code (Compose and coroutines benefit most); rules for
            // libraries that do not ship their own are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // java.time and other Java 8+ APIs on Android 7 (the wall tablet runs API 24).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

// Firebase Android app values for FCM push notifications (also in local.properties, copied from
// the Firebase console's google-services.json). They become the same string resources the
// google-services plugin would generate, so FirebaseApp initializes itself at startup.
fun requiredLocal(key: String): String = localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Missing $key in local.properties (Firebase Android app, see docs/push_notifications.md).")
val firebaseResources = mapOf(
    "google_app_id" to requiredLocal("firebase.android.appId"),
    "google_api_key" to requiredLocal("firebase.android.apiKey"),
    "gcm_defaultSenderId" to requiredLocal("firebase.senderId"),
    "project_id" to requiredLocal("firebase.projectId"),
)

androidComponents {
    onVariants { variant ->
        firebaseResources.forEach { (name, value) ->
            variant.resValues.put(variant.makeResValueKey("string", name), ResValue(value, "Firebase (local.properties)"))
        }
        variant.buildConfigFields?.put(
            "FIREBASE_API_KEY",
            BuildConfigField("String", "\"$firebaseApiKey\"", "Firebase Web API key (from local.properties)"),
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.hivemq.mqtt.client)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
