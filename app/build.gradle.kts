plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.flowpass.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.flowpass.mobile"
        minSdk = 23
        targetSdk = 28
        versionCode = 2
        versionName = "1.0.1"
    }
}