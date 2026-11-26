import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: the keystore and its passwords live OUTSIDE the project folder, in
// $LODESTONE_SIGNING_DIR/keystore.properties (default ~/.lodestone-signing), holding storeFile, storePassword, keyAlias, keyPassword.
// Without that file the release build is unsigned. Never commit the keystore or its passwords.
val signingProps = Properties().apply {
    val dir = System.getenv("LODESTONE_SIGNING_DIR")?.let { File(it) } ?: File(System.getProperty("user.home"), ".lodestone-signing")
    val f = File(dir, "keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.lodestone"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "rykode.lodestone"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"
        // Real phones are arm; x86_64 is for the emulator. Dropping 32-bit x86 keeps the app small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildFeatures {
        buildConfig = true
    }

    // No signing-blob of dependency data in the APK: F-Droid wants none, and nothing here reads it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (signingProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
