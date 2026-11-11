plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.gamepadbridge.preview"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gamepadbridge.preview"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "Preview 1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
