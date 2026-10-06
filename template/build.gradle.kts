plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.oss.licenses) apply false
}

tasks.register<Copy>("prepareHerald") {
    val version = providers.gradleProperty("herald.coreVersion").get()
    val archive = file("vendor/herald-core-$version-maven.zip")
    from(zipTree(archive))
    into("vendor")
    doFirst {
        val expected = file("vendor/SHA256SUMS").readLines()
            .single { it.endsWith("  ${archive.name}") }.substringBefore(' ')
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        require(actual == expected) { "Vendored core checksum mismatch" }
    }
}

subprojects {
    tasks.configureEach {
        if (name == "preBuild" || name.endsWith("OssDependencyTask") || name == "collectReleaseDependencies") {
            dependsOn(rootProject.tasks.named("prepareHerald"))
        }
    }
}
