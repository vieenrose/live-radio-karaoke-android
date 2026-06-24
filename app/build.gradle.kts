plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Native (llama.cpp + sherpa-onnx) is heavy and needs source/.so fetched first.
// Build the pure-Kotlin app without it (engines degrade gracefully), or enable with:
//   ./gradlew assembleDevDebug -PwithNative   (after scripts/fetch-native-libs.sh)
val withNative = project.hasProperty("withNative")

android {
    namespace = "io.github.vieenrose.liveradiokaraoke"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.vieenrose.liveradiokaraoke"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
        vectorDrawables { useSupportLibrary = true }
        if (withNative) {
            // Default to both ABIs; override with -Pabi=arm64-v8a to ship a smaller per-ABI APK.
            val abis = (project.findProperty("abi") as String?)?.split(",")?.map { it.trim() }
                ?: listOf("arm64-v8a", "x86_64")
            ndk { abiFilters += abis }
        }
    }

    if (withNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        ndkVersion = "27.2.12479018"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Default development / Play build: prebuilt sherpa-onnx .so via fetch script.
        create("dev") { dimension = "distribution" }
        // F-Droid build: native libs compiled from source (see fdroid/metadata-template.yml).
        create("fdroid") { dimension = "distribution" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Keep the (large) native libs uncompressed for mmap-friendly loading.
        jniLibs { useLegacyPackaging = false }
    }

    // BuildConfig flag the engines read to know whether native libs are present.
    buildTypes.all { buildConfigField("boolean", "WITH_NATIVE", withNative.toString()) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.common)

    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.opencc4j)
}
