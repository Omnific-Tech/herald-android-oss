import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// Keep project terms and dependency-authored copyright/notice files in every APK.
val licenseAssets = layout.buildDirectory.dir("generated/licenseAssets")
val runtimeArtifacts = providers.provider {
    configurations.getByName("releaseRuntimeClasspath").incoming.artifacts.resolvedArtifacts
}.flatMap { it }
val projectLicenseAssets = tasks.register<Sync>("projectLicenseAssets") {
    dependsOn(rootProject.tasks.named("prepareHerald"))
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("third_party"))
    into(licenseAssets.map { it.dir("licenses") })
    inputs.files(runtimeArtifacts.map { artifacts -> artifacts.map { it.file } })
    doLast {
        val directory = licenseAssets.get().dir("licenses").asFile
        val artifacts = runtimeArtifacts.get()
        directory.resolve("DEPENDENCIES.txt").writeText(
            artifacts.map { it.id.componentIdentifier.displayName }.sorted().joinToString("\n", postfix = "\n")
        )
        for (artifact in artifacts) {
            val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
            project.copy {
                from(zipTree(artifact.file))
                include { it.file.name.matches(Regex("(?i)(LICENSE|NOTICE|COPYING)(\\..*)?")) }
                into(directory.resolve("dependencies/${id.group}/${id.module}/${id.version}"))
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(projectLicenseAssets) }

val verifyReleaseNotices = tasks.register("verifyReleaseNotices") {
    dependsOn("assembleRelease")
    val apk = providers.gradleProperty("apk.path").map { rootProject.file(it) }
        .orElse(layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").map { it.asFile })
    inputs.file(apk)
    inputs.dir(licenseAssets)
    inputs.dir(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask"))
    doLast {
        ZipFile(apk.get()).use { zip ->
            fun verifyEntry(path: String, expected: ByteArray) {
                val entry = requireNotNull(zip.getEntry(path)) { "APK is missing $path" }
                require(zip.getInputStream(entry).use { it.readBytes() }.contentEquals(expected)) {
                    "APK notice differs from generated source: $path"
                }
            }
            val resources = layout.buildDirectory.dir("generated/res/releaseOssLicensesTask/raw").get().asFile
            val text = resources.resolve("third_party_licenses").readBytes()
            val metadata = resources.resolve("third_party_license_metadata").readLines()
            require(metadata.isNotEmpty() && metadata.none { it.contains("Debug License Info") })
            for (line in metadata) {
                val range = line.substringBefore(' ').split(':').map { it.toInt() }
                require(range.size == 2 && range[0] >= 0 && range[1] > 0 &&
                    range[0].toLong() + range[1] <= text.size) { "Invalid license record: $line" }
            }
            for (name in listOf("third_party_licenses", "third_party_license_metadata")) {
                val expected = resources.resolve(name).readBytes()
                // AAPT may shorten resource filenames in optimized release APKs.
                require(zip.entries().asSequence().filter { it.name.startsWith("res/") }.any { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.contentEquals(expected)
                }) { "APK is missing generated notice resource: $name" }
            }
            val assets = licenseAssets.get().asFile
            for (file in assets.walkTopDown().filter { it.isFile }) {
                verifyEntry("assets/${file.relativeTo(assets).invariantSeparatorsPath}", file.readBytes())
            }
            logger.lifecycle("Verified ${metadata.size} notice records and project/dependency license assets in APK")
        }
    }
}

tasks.register<Zip>("releaseNotices") {
    dependsOn(verifyReleaseNotices)
    from(licenseAssets)
    from(layout.buildDirectory.dir("generated/res/releaseOssLicensesTask/raw")) { into("raw") }
    from(layout.buildDirectory.file("generated/third_party_licenses/release/dependencies.json"))
    archiveFileName.set("THIRD-PARTY-NOTICES.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/notices"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
