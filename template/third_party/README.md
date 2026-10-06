# Dependency notices

The build collects Google Play services embedded notices and Maven-declared
license links using the [OSS licenses plugin](https://developers.google.com/android/guides/opensource).
Full Apache-2.0 terms, project NOTICE and dependency-embedded LICENSE/NOTICE files
are also packaged under APK `assets/licenses/`.

`LICENSE-protobuf.txt` preserves the BSD terms for repackaged Protocol Buffers
used by DataStore. Source: [Protocol Buffers license](https://github.com/protocolbuffers/protobuf/blob/v28.2/LICENSE).
The Apache license selected for this project does not replace dependency terms,
including Google SDK terms linked in the generated notice metadata.

Run `./gradlew :app:releaseNotices` to verify the minified release APK and export
`app/build/outputs/notices/THIRD-PARTY-NOTICES.zip`. Distribute this archive with
the APK. It includes full texts, embedded notices, links and dependency inventory.
Review additions and license changes whenever updating dependencies.
