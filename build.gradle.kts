plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.google.services) apply false
}

val libraryVersion = providers.gradleProperty("herald.coreVersion").getOrElse("0.1.0")
val productDestination = providers.gradleProperty("product.dir")

val validateProductDestination = tasks.register("validateProductDestination") {
    doLast {
        require(productDestination.isPresent) {
            "Set -Pproduct.dir to a new directory outside this repository"
        }
        val target = file(productDestination.get()).canonicalFile
        require(!target.toPath().startsWith(rootDir.canonicalFile.toPath())) {
            "Product directory must be outside this repository"
        }
        require(!target.exists()) { "Product directory already exists: $target" }
    }
}

tasks.register<Copy>("newProduct") {
    dependsOn(validateProductDestination, "releaseBundle")
    into(productDestination.map { file(it) })
    from("template") {
        exclude(".gradle/**", "**/build/**", "local.properties", "vendor/**")
        filesMatching("gradle.properties") {
            filter { line: String ->
                if (line.startsWith("herald.coreVersion=")) "herald.coreVersion=$libraryVersion" else line
            }
        }
    }
    from(layout.buildDirectory.dir("distributions")) {
        include("herald-core-$libraryVersion-maven.zip", "SHA256SUMS", "PROVENANCE.txt")
        into("vendor")
    }
    from("LICENSE", "NOTICE")
}

tasks.register("releaseBundle") {
    dependsOn(":core:libraryZip")
    doLast {
        val status = providers.exec {
            commandLine("git", "status", "--porcelain")
        }.standardOutput.asText.get().trim()
        require(status.isEmpty()) { "Commit source changes before creating release provenance" }
        val directory = layout.buildDirectory.dir("distributions").get().asFile
        val archive = directory.resolve("herald-core-$libraryVersion-maven.zip")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes()).joinToString("") { "%02x".format(it) }
        directory.resolve("SHA256SUMS").writeText("$digest  ${archive.name}\n")
        val revision = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        directory.resolve("PROVENANCE.txt").writeText(
            "source_revision=$revision\ncore_version=$libraryVersion\n" +
                "coordinate=dev.omnific.herald:herald-core:$libraryVersion\n"
        )
    }
}
