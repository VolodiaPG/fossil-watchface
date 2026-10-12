plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "io.github.volodiapg.fossilfeed"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.volodiapg.fossilfeed"
        minSdk = 26
        targetSdk = 34
        // CI passes the release number and the run number so that every build can update the previous one
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersion") as String?) ?: "dev"
    }

    // Release builds are signed with the key from the environment (CI decodes it from GitHub secrets);
    // without it, e.g. for a local build, the Android debug key is used.
    val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
