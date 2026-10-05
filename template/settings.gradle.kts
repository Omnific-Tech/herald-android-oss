pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
