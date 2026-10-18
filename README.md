# Herald Android

Reusable Android notification client and independently buildable Trusted Web
Activity template. Each app supplies its own identity, configuration and assets.

## Create an app

Requires JDK 21 and Android SDK platform 37.2/build-tools 37.0.0. Set
`ANDROID_HOME` or use a local SDK configuration file.

```sh
./gradlew releaseBundle newProduct -Pproduct.dir=/absolute/path/to/new-app
cd /absolute/path/to/new-app
./gradlew :app:assembleDebug :app:assembleRelease
```

The destination must not exist and must be outside this repository. It contains
its own Gradle wrapper and a pinned Maven ZIP; builds do not fetch private source.
Public Android/Firebase dependencies still require network access on first build.

Edit `gradle.properties` for application ID and app versions. Supply your own
Firebase client config in `app/google-services.json`, matching application ID.
The included Firebase configuration is a build-only placeholder.

Edit `app/src/main/assets/herald.json` and
`app/src/main/res/values/strings.xml` with matching origin/start URL. Replace icons.
The app namespace can remain the generic template namespace while its
application ID uniquely identifies the installed app.

Provision the application in your broker. Serve Digital Asset Links on the
configured origin with your app ID and signing certificate fingerprint.
Enable `web_subscription_bridge` only with both `handle_all_urls` and
`use_as_origin` relationships. The PWA uses Herald Web for subscription controls.

## Library distribution

`./gradlew releaseBundle` produces a local Maven ZIP, SHA256SUMS and source
provenance under `build/distributions/`. Its coordinate is
`dev.omnific.herald:herald-core:0.1.1`. The archive includes AAR, dependency
metadata and sources. Consumer shrinker rules and manifest entries ship in AAR.

Generate an app using the released core version pinned by the template. For
existing apps, unpack ZIP into a local Maven repository and declare the same
coordinate. Inspect `template/` for complete Gradle setup. Preserve vendored
artifacts and their source version; upgrade dependencies deliberately.

## Behavior and verification

Herald protocol v1 handles registration, FCM delivery, deduplication, expiry,
native notification channels, acknowledgement and trusted notification links.
The PWA owns channel/topic preferences; device credentials stay in the APK.

CI builds the library and a standalone generated consumer, including a minified
release APK. Packaging verification does not verify real transport delivery.
Before rollout, verify notification permission, delivery, tap routing and bridge
on an Android device. Existing app upgrades must preserve application ID,
certificate, increasing versionCode and saved preferences.

The [subscription bridge contract](docs/subscription-bridge.md) documents wire
messages and Digital Asset Links for integrations that do not use Herald Web.

Core 0.1.1 accepts up to 500 subscriptions, including Aeon's 343 combined
earthquake/haze/system subscriptions. Existing channel, ID, topic-length and
duplicate validation is unchanged. Consumers must update their pinned core and
ship an updated APK; changing the PWA cannot raise an older APK's bridge limit.

Generated apps retain dependency terms and embedded copyright notices in APKs.
From the generated project, run `./gradlew :app:releaseNotices` to verify
the minified APK and export its notice ZIP for distribution alongside the APK.
See [dependency notices](template/third_party/README.md). CI verifies and retains
both APKs and the notice ZIP.
