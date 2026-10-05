plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

/**
 * A product target overrides these in its own gradle.properties rather than
 * editing this file.
 */
val productApplicationId: String = providers.gradleProperty("herald.applicationId").getOrElse("dev.omnific.herald.template")
val productVersionName: String = providers.gradleProperty("herald.versionName").getOrElse("0.1.0")
val productVersionCode: Int = providers.gradleProperty("herald.versionCode").getOrElse("1").toInt()

android {
    namespace = "dev.omnific.herald.template"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = productApplicationId
        minSdk = 26
        targetSdk = 37
        versionCode = productVersionCode
        versionName = productVersionName
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
}

dependencies {
    implementation(project(":core"))
}
