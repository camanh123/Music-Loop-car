plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.musicloop.car"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.musicloop.car"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
        // Sideloaded CARFU Android 10 app; not distributed via Google Play.
        disable += "ExpiredTargetSdkVersion"
        // Keep AndroidX artifacts compatible with compileSdk 33 / minSdk 29.
        disable += "GradleDependency"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
