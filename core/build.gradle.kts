plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "dev.omnific.herald"
version = providers.gradleProperty("herald.coreVersion").getOrElse("0.1.0")

android {
    namespace = "dev.omnific.herald"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        // Notification channels, which product channels map onto, are API 26.
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
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

}

publishing {
    repositories {
        maven {
            name = "Local"
            url = uri(layout.buildDirectory.dir("maven"))
        }
    }
}

afterEvaluate {
    publishing.publications.create<MavenPublication>("release") {
        from(components["release"])
        artifactId = "herald-core"
        pom {
            name.set("Herald Android core")
            description.set("Notification client and Trusted Web Activity bridge")
        }
    }
}

tasks.register<Zip>("libraryZip") {
    dependsOn("publishReleasePublicationToLocalRepository")
    from(layout.buildDirectory.dir("maven"))
    into("maven")
    archiveFileName.set("herald-core-${project.version}-maven.zip")
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
