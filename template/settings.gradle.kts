pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(rootDir.resolve("vendor/maven"))
            content { includeGroup("dev.omnific.herald") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "herald-product"
include(":app")
