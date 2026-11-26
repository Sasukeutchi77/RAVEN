import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.ravenchat.vkwqle"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val requiredEnvDefaults = linkedMapOf(
      "CLOUDINARY_CLOUD_NAME" to "CLOUDINARY_CLOUD_NAME_PLACEHOLDER",
      "CLOUDINARY_UPLOAD_PRESET" to "CLOUDINARY_UPLOAD_PRESET_PLACEHOLDER",
      "CLOUDINARY_UPLOAD_FOLDER" to "raven_media",
      "FIREBASE_DATABASE_ID" to "FIREBASE_DATABASE_ID_PLACEHOLDER",
      "FIREBASE_WEB_CLIENT_ID" to "FIREBASE_WEB_CLIENT_ID_PLACEHOLDER",
      "FIREBASE_PROJECT_ID" to "FIREBASE_PROJECT_ID_PLACEHOLDER",
      "FIREBASE_APP_ID" to "FIREBASE_APP_ID_PLACEHOLDER",
      "FIREBASE_API_KEY" to "FIREBASE_API_KEY_PLACEHOLDER",
      "FIREBASE_MESSAGING_SENDER_ID" to "FIREBASE_MESSAGING_SENDER_ID_PLACEHOLDER",
      "FIREBASE_STORAGE_BUCKET" to "FIREBASE_STORAGE_BUCKET_PLACEHOLDER"
    )
    val loadedSecretKeys = mutableSetOf<String>()
    listOf(rootProject.file(".env.example"), rootProject.file(".env")).forEach { propFile ->
      if (propFile.exists() && propFile.isFile) {
        val props = Properties()
        propFile.inputStream().use { props.load(it) }
        props.stringPropertyNames().forEach { loadedSecretKeys.add(it) }
      }
    }
    requiredEnvDefaults.forEach { (key, defaultValue) ->
      if (!loadedSecretKeys.contains(key)) {
        val ciEnv = System.getenv(key)?.takeIf { it.isNotBlank() } ?: defaultValue
        val escaped = ciEnv.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", key, "\"$escaped\"")
      }
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin safely even if .env or .env.example was moved/omitted in CI.
val hasEnvFile = rootProject.file(".env").exists()
val hasEnvExampleFile = rootProject.file(".env.example").exists()
secrets {
  if (hasEnvFile) {
    propertiesFileName = ".env"
  } else if (hasEnvExampleFile) {
    propertiesFileName = ".env.example"
  }
  if (hasEnvExampleFile) {
    defaultPropertiesFileName = ".env.example"
  } else if (hasEnvFile) {
    defaultPropertiesFileName = ".env"
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.common)
  implementation(libs.stream.webrtc.android)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
  failFast = true
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}
