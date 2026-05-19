plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ethan.tnd"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ethan.tnd"
        minSdk = 24
        targetSdk = 34
        versionCode = (project.findProperty("vCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("vName") as String?) ?: "2.4.7"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Use debug signing so CI can produce an installable APK without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    androidResources {
        noCompress += listOf("")
    }

    packaging {
        jniLibs {
            // Required so the bundled Rust binary (shipped as libtnd.so) is extracted
            // to nativeLibraryDir on install and can be exec'd as a normal file.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
