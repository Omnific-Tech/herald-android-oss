<div align="center">

# Herald Android

**Native notifications for your web app. Your product, your Android identity.**

[Core releases](https://github.com/Omnific-Tech/herald-android-oss/releases) · [Bridge contract](docs/subscription-bridge.md) · [Herald broker](https://github.com/Omnific-Tech/herald-oss) · [Herald Web](https://github.com/Omnific-Tech/herald-web-oss)

</div>

Herald Android is a reusable notification client and an independently buildable **Trusted Web Activity template**. Turn an existing PWA into an Android app with its own package ID, branding, Firebase configuration, and notification channels.

The core handles Herald protocol v1 registration, FCM delivery, deduplication, expiry, native notification channels, acknowledgements, and trusted notification links. Your web app owns the product experience and subscription preferences; device credentials remain inside the Android app.

## How the pieces fit

| Project | Responsibility |
| --- | --- |
| [Herald](https://github.com/Omnific-Tech/herald-oss) | Broker: authenticated publishing, routing, retries, device registration |
| **Herald Android** | Android client, native FCM delivery, TWA shell and subscription bridge |
| [Herald Web](https://github.com/Omnific-Tech/herald-web-oss) | Browser Web Push and the page side of the Android bridge |
| Your product | PWA, app identity, notification content and preferences |

[Aeon for Android](https://github.com/Omnific-Tech/aeon-android) and [Nexus for Android](https://github.com/Omnific-Tech/nexus-android) are product apps built on this core. The reusable library contains no product-specific identity or backend.

## Create an app

Requires **JDK 21**, Android SDK platform **37.2**, and build-tools **37.0.0**. Set `ANDROID_HOME` or configure your SDK locally. Generate from a clean, committed checkout so release provenance identifies the exact source.

```sh
./gradlew releaseBundle newProduct -Pproduct.dir=/absolute/path/to/new-app
cd /absolute/path/to/new-app
./gradlew :app:assembleDebug :app:releaseNotices :app:lint
```

The destination must not exist and must be outside this repository. The generated app includes its own Gradle wrapper and a pinned Maven ZIP. It builds without a private source checkout; public Android/Firebase dependencies require network access on the first build.

Configure your app before distribution:

1. Set the application ID and app versions in `gradle.properties`. Replace icons and the display name in `app/src/main/res/values/strings.xml`.
2. Supply your own `app/google-services.json` matching that application ID. The template's Firebase configuration is a build-only placeholder.
3. Set your broker, application slug, origin, start URL, and channels in `app/src/main/assets/herald.json`. Keep the origin/start URL aligned with `strings.xml`.
4. Provision the application in Herald. Serve Digital Asset Links on your origin with the app ID and signing certificate fingerprint.
5. To enable `web_subscription_bridge`, configure both `handle_all_urls` and `use_as_origin` relationships. Use Herald Web in the PWA for subscription controls.

The generic template namespace may remain unchanged; the application ID uniquely identifies the installed app. See the [subscription bridge contract](docs/subscription-bridge.md) for messages, trust requirements, and integration without Herald Web.

## Library distribution

Current coordinate: **`dev.omnific.herald:herald-core:0.1.1`**.

```sh
./gradlew releaseBundle
```

Outputs under `build/distributions/`:

| Artifact | Contents |
| --- | --- |
| `herald-core-0.1.1-maven.zip` | Local Maven repository: AAR, dependency metadata, sources |
| `SHA256SUMS` | Archive integrity checksum |
| `PROVENANCE.txt` | Core version, coordinate, exact source revision |

Consumer shrinker rules and manifest components ship in the AAR. Generated apps verify and unpack the pinned ZIP during their build; dependencies are resolved using its metadata. Preserve the archive, checksum, and provenance together, and upgrade deliberately. The [template](template/) shows the complete consumer setup.

Core 0.1.1 supports **up to 500 subscriptions**, including combined product watches such as Aeon's 343 earthquake/haze/system subscriptions. Channel, ID, topic-length, and duplicate validation still apply. Existing apps must update their pinned core and ship a new APK; a web update cannot increase an older installed bridge's capacity.

## Verification and rollout

CI builds the library and a standalone generated consumer, including debug and minified release APKs, Android lint, and dependency-notice verification. Tagged `core-v*` builds publish the versioned library bundle.

Generated apps retain dependency terms and embedded copyright notices. Run `./gradlew :app:releaseNotices` inside the generated project to verify the minified APK and export `THIRD-PARTY-NOTICES.zip`. Distribute it alongside the APK; see [dependency notices](template/third_party/README.md).

Before product rollout, verify notification permission, actual delivery, tap routing, and the subscription bridge on a physical Android device. Test in-place upgrades with saved preferences. Preserve the application ID and signing certificate, increase `versionCode`, and keep Digital Asset Links aligned. Packaging checks do not establish real transport delivery or device behavior.

## License

[Apache License 2.0](LICENSE) · [Copyright notice](NOTICE)

Part of [Omnific Tech](https://omnific.dev/). Product apps and the broker are maintained separately, so the core can be used with your own product and deployment.
