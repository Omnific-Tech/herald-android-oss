plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.register<Copy>("prepareHerald") {
    val version = providers.gradleProperty("herald.coreVersion").get()
    from(zipTree(file("vendor/herald-core-$version-maven.zip")))
    into("vendor")
}

subprojects {
    tasks.configureEach {
        if (name == "preBuild") dependsOn(rootProject.tasks.named("prepareHerald"))
    }
}
