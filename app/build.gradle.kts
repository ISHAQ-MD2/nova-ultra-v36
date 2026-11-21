plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nova.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nova.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 36
        versionName = "36.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // NOVA Browser: 1.12.1 = compileSdk 34 ke saath chalta hai (1.15+ ko compileSdk 35 chahiye) aur profile (MULTI_PROFILE) support rakhta hai
    implementation("androidx.webkit:webkit:1.12.1")
}
