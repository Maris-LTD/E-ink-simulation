plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example.emptyapp"
  compileSdk {
    version = release(37) {
      minorApiLevel = 0
    }
  }
  defaultConfig {
    applicationId = "com.eink.reader.app"
    minSdk = 24
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
  }

  val releaseStoreFile = releaseSigningValue("RELEASE_STORE_FILE")
  signingConfigs {
    if (releaseStoreFile != null) {
      create("release") {
        storeFile = file(releaseStoreFile)
        storePassword = releaseSigningValue("RELEASE_STORE_PASSWORD")
        keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
        keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.findByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "org/bouncycastle/pqc/**/*.properties"
    }
  }

  bundle {
    language {
      enableSplit = false
    }
  }

  lint {
    disable += "TrustAllX509TrustManager"
  }
}

kotlin {
  jvmToolchain(17)
}

fun releaseSigningValue(name: String): String? =
  (findProperty(name) as? String)?.takeIf { it.isNotBlank() }
    ?: System.getenv(name)?.takeIf { it.isNotBlank() }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  coreLibraryDesugaring(libs.desugar.jdk.libs)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.fragment.compose)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.readium.shared)
  implementation(libs.readium.streamer)
  implementation(libs.readium.navigator)
  implementation(libs.readium.adapter.pdfium)
  implementation(libs.play.services.auth)
  implementation(libs.pdfbox.android)
}
