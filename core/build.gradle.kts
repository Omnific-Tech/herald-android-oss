plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.omnific.herald"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Notification channels, which product channels map onto, are API 26.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.browser)
    api(libs.android.browser.helper)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
	implementation(libs.okhttp)
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.messaging)
	implementation(libs.firebase.installations)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
