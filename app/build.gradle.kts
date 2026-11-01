plugins {
    alias(libs.plugins.android.application)
}

fun readDotEnv(): Map<String, String> {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return emptyMap()
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
        .associate {
            val key = it.substringBefore("=").trim()
            val value = it.substringAfter("=").trim().trim('"', '\'')
            key to value
        }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val dotEnv = readDotEnv()

// CI sets MARKETEDGE_BUILD to the workflow run number so every published APK
// is a newer version than the last one and installs as an update.
val buildNumber = (System.getenv("MARKETEDGE_BUILD") ?: "").toIntOrNull() ?: 1
val releaseKeystore = rootProject.file("marketedge.p12")
val releaseKeystorePassword = System.getenv("MARKETEDGE_KEYSTORE_PASSWORD") ?: ""

android {
    namespace = "id.rahmat.marketedge"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "id.rahmat.marketedge"
        minSdk = 24
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AI_API_KEY", (dotEnv["AI_API_KEY"] ?: "").asBuildConfigString())
        buildConfigField("String", "AI_BASE_URL", (dotEnv["AI_BASE_URL"] ?: "https://api.groq.com/openai/v1/chat/completions").asBuildConfigString())
        buildConfigField("String", "AI_MODEL", (dotEnv["AI_MODEL"] ?: "llama-3.1-8b-instant").asBuildConfigString())
    }

    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storeType = "pkcs12"
            storePassword = releaseKeystorePassword
            keyAlias = "marketedge"
            keyPassword = releaseKeystorePassword
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Without the key (e.g. a local build) the release APK is left unsigned.
            if (releaseKeystore.exists() && releaseKeystorePassword.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
